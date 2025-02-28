package net.microfalx.threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.Long.MAX_VALUE;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static net.microfalx.lang.ArgumentUtils.requireBounded;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * Thread pool implementation.
 */
final class ThreadPoolImpl extends AbstractExecutorService implements ThreadPool {

    final static Logger LOGGER = LoggerFactory.getLogger(ThreadPoolImpl.class);

    private final BlockingQueue<TaskWrapper<?, ?>> taskQueue;
    private final BlockingQueue<TaskWrapper<?, ?>> extraTaskQueue;
    private final DelayQueue<CallableTaskWrapper<?>> delayedTaskQueue;
    private final Collection<TaskWrapper<?, ?>> running = new CopyOnWriteArrayList<>();
    private final Collection<TaskWrapper<?, ?>> scheduled = new CopyOnWriteArrayList<>();
    private final Map<Class<?>, Set<Object>> singletonPermits = new ConcurrentHashMap<>();
    private final OptionsImpl options;
    private final ThreadFactory factory;
    private final Dispatcher dispatcher;
    private final ReadWriteLock lock1 = new ReentrantReadWriteLock();
    private final Lock rlock = lock1.readLock();
    private final Lock wlock = lock1.writeLock();
    private final Metrics metrics = new MetricsImpl();

    private final AtomicLong executedTaskCount = new AtomicLong();
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final AtomicBoolean terminated = new AtomicBoolean();
    private final AtomicBoolean suspended = new AtomicBoolean();
    private final CountDownLatch terminatedLatch = new CountDownLatch(1);

    ThreadPoolImpl(BlockingQueue<TaskWrapper<?, ?>> taskQueue, OptionsImpl options) {
        requireNonNull(taskQueue);
        requireNonNull(options);
        this.taskQueue = taskQueue;
        this.options = options;
        this.factory = new ThreadFactory(this);
        this.dispatcher = Dispatcher.getInstance();
        this.delayedTaskQueue = new DelayQueue<>();
        this.extraTaskQueue = new LinkedBlockingQueue<>();
        this.dispatcher.register(this);
    }

    @Override
    public Options getOptions() {
        return options;
    }

    @Override
    public int getIndex(Thread thread) {
        requireNonNull(thread);
        return factory.getIndex(thread);
    }

    @Override
    public void shutdown() {
        shutdown.set(true);
        this.dispatcher.unregister(this);
        tryTerminate();
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown();
        List<TaskWrapper<?, ?>> pending = new ArrayList<>();
        taskQueue.drainTo(pending);
        extraTaskQueue.drainTo(pending);
        return pending.stream().filter(t -> t instanceof RunnableTaskWrapper).map(t -> (Runnable) t.getTask()).toList();
    }

    @Override
    public boolean isShutdown() {
        return shutdown.get();
    }

    @Override
    public boolean isTerminated() {
        return terminated.get();
    }

    @Override
    public void suspend() {
        suspended.set(true);
    }

    @Override
    public void resume() {
        suspended.set(false);
        dispatcher.wakeUp(this);
    }

    @Override
    public boolean isPaused() {
        return suspended.get();
    }

    @Override
    public boolean isActive() {
        rlock.lock();
        try {
            return !running.isEmpty();
        } finally {
            rlock.unlock();
        }
    }

    @Override
    public boolean isIdle() {
        rlock.lock();
        try {
            return running.isEmpty() && isQueueEmpty();
        } finally {
            rlock.unlock();
        }
    }

    @Override
    public void setMaximumSize(int maximumSize) {
        options.setMaximumSize(maximumSize);
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        requireNonNull(unit);
        requireBounded(timeout, 0, MAX_VALUE);
        return terminatedLatch.await(timeout, unit);
    }

    @Override
    public void execute(Runnable task) {
        requireNonNull(task);
        checkIfShuttingDown(task);
        if (canSchedule(task)) {
            if (task instanceof ScheduledTask scheduledTask) {
                schedule(scheduledTask);
            } else if (task instanceof DelayedTask) {
                schedule(task, ((DelayedTask) task).getDelay().toMillis(), MILLISECONDS);
            } else {
                if (!taskQueue.offer(new RunnableTaskWrapper(this, task))) reject(task);
            }
        } else {

        }
        dispatcher.wakeUp(this);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        requireNonNull(task);
        requireNonNull(unit);
        requireBounded(delay, 0, MAX_VALUE);
        checkIfShuttingDown(task);
        Callable<?> callable = Executors.callable(task);
        CallableTaskWrapper<?> callableTask = new CallableTaskWrapper<>(this, callable, delay, unit);
        registerScheduled(callableTask);
        return callableTask.getFuture();
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        requireNonNull(callable);
        requireNonNull(unit);
        requireBounded(delay, 0, MAX_VALUE);
        CallableTaskWrapper<V> callableTask = new CallableTaskWrapper<>(this, callable, delay, unit);
        registerScheduled(callableTask);
        return callableTask.getFuture();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        requireNonNull(task);
        requireNonNull(unit);
        requireBounded(initialDelay, 0, MAX_VALUE);
        requireBounded(period, 1, MAX_VALUE);
        checkIfShuttingDown(task);
        Callable<?> callable = Executors.callable(task);
        CallableTaskWrapper<?> callableTask = new CallableTaskWrapper<>(this, callable, initialDelay, unit).interval(TaskWrapper.Mode.FIXED_RATE, period, unit);
        registerScheduled(callableTask);
        return callableTask.getFuture();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        requireNonNull(task);
        requireNonNull(unit);
        requireBounded(initialDelay, 0, MAX_VALUE);
        requireBounded(delay, 0, MAX_VALUE);
        checkIfShuttingDown(task);
        Callable<?> callable = Executors.callable(task);
        CallableTaskWrapper<?> callableTask = new CallableTaskWrapper<>(this, callable, initialDelay, unit).interval(TaskWrapper.Mode.FIXED_DELAY, delay, unit);
        registerScheduled(callableTask);
        return callableTask.getFuture();
    }

    @Override
    public Collection<?> getRunningTasks() {
        return running.stream().map(TaskWrapper::getTask).toList();
    }

    @Override
    public Collection<?> getPendingTasks() {
        Collection<Object> pending = new ArrayList<>();
        pending.addAll(taskQueue.stream().map(TaskWrapper::getTask).toList());
        pending.addAll(extraTaskQueue.stream().map(TaskWrapper::getTask).toList());
        return pending;
    }

    @Override
    public Collection<?> getScheduledTasks() {
        return scheduled.stream().map(TaskWrapper::getTask).toList();
    }

    @Override
    public Collection<Thread> getThreads() {
        return factory.getThreads();
    }

    @Override
    public Metrics getMetrics() {
        return metrics;
    }

    /**
     * Invoked by the dispatcher to process any available tasks.
     */
    void handleTasks() {
        if (!isPaused()) {
            handleScheduledAndDelayed();
            if (!isQueueEmpty() && !factory.hasIdle()) factory.createThread(null, true);
        }
    }


    /**
     * Called from a pooled thread to mark the execution of the task completed.
     *
     * @param task the task
     */
    void completeTask(TaskWrapper<?, ?> task) {
        executedTaskCount.incrementAndGet();
        running.remove(task);
        if (task.getTask() instanceof SingletonTask singletonTask && singletonTask.isSingleton()) {
            getSingletonKeys(singletonTask).remove(singletonTask.getKey());
        }
        updateTask(task);
        tryTerminate();
    }

    /**
     * Called by the executing thread before the actually execution starts.
     *
     * @param task the task
     */
    void beforeTask(TaskWrapper<?, ?> task) {
        running.add(task);
        if (task.getTask() instanceof SingletonTask singletonTask && singletonTask.isSingleton()) {
            getSingletonKeys(singletonTask).add(singletonTask.getKey());
        }
    }

    /**
     * Called from a pooled thread to mark the execution of the task completed (successfuly or not).
     *
     * @param task      the task
     * @param throwable the exception
     */
    void failedTask(TaskWrapper<?, ?> task, Throwable throwable) {
        if (task instanceof RunnableTaskWrapper) {
            options.getFailedHandler().failed(((RunnableTaskWrapper) task).getTask(), this, throwable);
        }
    }

    /**
     * Returns the next task which should be executed.
     * <p>
     * If the thread pool is in the shutdown, no task is returned even if there are tasks in the queue.
     *
     * @return a non-null instance if there is an available task, null otherwise
     */
    TaskWrapper<?, ?> nextTask() throws InterruptedException {
        if (shutdown.get() || isPaused()) return null;
        handleScheduledAndDelayed();
        TaskWrapper<?, ?> task = taskQueue.poll();
        if (task == null) task = extraTaskQueue.poll(100, MILLISECONDS);
        return task;
    }

    /**
     * Invoked before a thread leaves the execution loop.
     *
     * @param object the object
     */
    void destroyThread(Thread object) {
        factory.destroyThread(object);
    }

    private void schedule(ScheduledTask scheduledTask) {
        Runnable task = (Runnable) scheduledTask;
        switch (scheduledTask.getStrategy()) {
            case FIXED_RATE:
                scheduleAtFixedRate(task, scheduledTask.getInitialDelay().toMillis(), scheduledTask.getInterval().toMillis(), MILLISECONDS);
                break;
            case FIXED_DELAY:
                scheduleWithFixedDelay(task, scheduledTask.getInitialDelay().toMillis(), scheduledTask.getInterval().toMillis(), MILLISECONDS);
                break;
        }
    }

    private void handleScheduledAndDelayed() {
        for (; ; ) {
            CallableTaskWrapper<?> callableTask = delayedTaskQueue.poll();
            if (callableTask == null) break;
            if (!taskQueue.offer(callableTask)) extraTaskQueue.add(callableTask);
        }
    }

    private void registerScheduled(CallableTaskWrapper<?> callableTask) {
        delayedTaskQueue.offer(callableTask);
        if (callableTask.isPeriodic()) scheduled.add(callableTask);
        dispatcher.wakeUp(this);
    }

    private void updateTask(TaskWrapper<?, ?> task) {
        if (task instanceof CallableTaskWrapper<?> callableTask && callableTask.isPeriodic()) {
            callableTask.updateDelay();
            delayedTaskQueue.offer(callableTask);
        }
    }

    private void tryTerminate() {
        if (!terminated.get() && shutdown.get() && isQueueEmpty() && running.isEmpty()) {
            terminated.set(true);
            terminatedLatch.countDown();
        }
    }

    private void checkIfShuttingDown(Runnable runnable) {
        if (shutdown.get() && runnable != null) reject(runnable);
    }

    private void reject(Runnable runnable) {
        options.getRejectedHandler().rejected(runnable, this);
    }

    private boolean isQueueEmpty() {
        return taskQueue.isEmpty() && extraTaskQueue.isEmpty();
    }

    private boolean canSchedule(Runnable task) {
        if (task instanceof SingletonTask singletonTask) {
            if (!singletonTask.isSingleton()) {
                return true;
            } else {
                Object key = singletonTask.getKey();
                return !getSingletonKeys(singletonTask).contains(key);
            }
        } else {
            return true;
        }
    }

    private Set<Object> getSingletonKeys(SingletonTask task) {
        return singletonPermits.computeIfAbsent(task.getClass(), c -> new ConcurrentSkipListSet<>());
    }

    class MetricsImpl implements Metrics {

        private final ZonedDateTime created = ZonedDateTime.now();

        @Override
        public ZonedDateTime getCreatedTime() {
            return created;
        }

        @Override
        public int getRunningTaskCount() {
            return running.size();
        }

        @Override
        public int getPendingTaskCount() {
            return taskQueue.size() + extraTaskQueue.size();
        }

        @Override
        public long getExecutedTaskCount() {
            return executedTaskCount.get();
        }

        @Override
        public int getAvailableThreadCount() {
            return factory.getSize();
        }

        @Override
        public int getCreatedThreadCount() {
            return factory.created.get();
        }

        @Override
        public int getDestroyedThreadCount() {
            return factory.destroyed.get();
        }

    }

}
