package net.microfalx.threadpool;

import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.ThreadUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import static java.lang.Long.MAX_VALUE;
import static java.util.Collections.unmodifiableCollection;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static net.microfalx.lang.ArgumentUtils.requireBounded;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.FormatterUtils.formatDuration;
import static net.microfalx.lang.TimeUtils.toDuration;
import static net.microfalx.threadpool.ThreadPoolUtils.getThreadPoolId;

/**
 * Thread pool implementation.
 */
final class ThreadPoolImpl extends AbstractExecutorService implements ThreadPool {

    final static Logger LOGGER = LoggerFactory.getLogger(ThreadPoolImpl.class);

    private final static Duration INITIAL_DELAY = Duration.ofSeconds(30);
    private final static boolean NO_INITIAL_DELAY = Boolean.getBoolean("thread.pool.no_initial_delay");

    private final BlockingQueue<TaskWrapper<?, ?>> taskQueue;
    private final BlockingQueue<TaskWrapper<?, ?>> extraTaskQueue;
    private final DelayQueue<CallableTaskWrapper<?>> delayedTaskQueue;
    private final Collection<TaskWrapper<?, ?>> running = new CopyOnWriteArrayList<>();
    private final Collection<TaskWrapper<?, ?>> scheduled = new CopyOnWriteArrayList<>();
    private final Queue<TaskDescriptor> completed = new ArrayBlockingQueue<>(500);
    private final Map<Class<?>, Set<Object>> singletonPermits = new ConcurrentHashMap<>();
    private final String id;
    private final OptionsImpl options;
    private final ThreadFactory factory;
    private final Dispatcher dispatcher;
    private final ReadWriteLock lock1 = new ReentrantReadWriteLock();
    private final Lock rlock = lock1.readLock();
    private final Lock wlock = lock1.writeLock();
    private final Metrics metrics = new MetricsImpl();

    private final AtomicLong executedTaskCount = new AtomicLong();
    private final AtomicInteger failedTaskCount = new AtomicInteger();
    private final AtomicBoolean shutdown = new AtomicBoolean();
    private final AtomicBoolean terminated = new AtomicBoolean();
    private final AtomicBoolean suspended = new AtomicBoolean();
    private final CountDownLatch terminatedLatch = new CountDownLatch(1);

    ThreadPoolImpl(BlockingQueue<TaskWrapper<?, ?>> taskQueue, OptionsImpl options) {
        requireNonNull(taskQueue);
        requireNonNull(options);
        this.taskQueue = taskQueue;
        this.id = getThreadPoolId(options);
        this.options = options;
        this.factory = new ThreadFactory(this);
        this.dispatcher = Dispatcher.getInstance();
        this.delayedTaskQueue = new DelayQueue<>();
        this.extraTaskQueue = new LinkedBlockingQueue<>();
        this.dispatcher.register(this);
    }

    @Override
    public String getId() {
        return id;
    }

    @Override
    public String getName() {
        return options.getNamePrefix();
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
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
        requireNonNull(task);
        requireNonNull(trigger);
        checkIfShuttingDown(task);
        Callable<?> callable = Executors.callable(task);
        if (trigger instanceof IntervalAwareTrigger intervalAwareTrigger) {
            LOGGER.info("Register task '{}' with interval = {}, strategy = {}", ClassUtils.getName(task),
                    formatDuration(intervalAwareTrigger.getInterval()), intervalAwareTrigger.getStrategy());
            CallableTaskWrapper<?> callableTask = new CallableTaskWrapper<>(this, callable, intervalAwareTrigger.getInterval().toMillis(), MILLISECONDS)
                    .trigger(trigger);
            registerScheduled(callableTask);
            return callableTask.getFuture();
        } else if (trigger instanceof CronTrigger cronTrigger) {
            LOGGER.info("Register task '{}' with cron expression = {}, interval = {}", ClassUtils.getName(task), cronTrigger.getExpression(),
                    formatDuration(cronTrigger.getInterval()));
            CallableTaskWrapper<?> callableTask = new CallableTaskWrapper<>(this, callable, cronTrigger.getInterval().toMillis(), MILLISECONDS)
                    .trigger(trigger);
            registerScheduled(callableTask);
            return callableTask.getFuture();
        } else {
            throw new IllegalArgumentException("Unsupported trigger type: " + trigger.getClass());
        }
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        requireNonNull(task);
        requireNonNull(unit);
        requireBounded(initialDelay, 0, MAX_VALUE);
        requireBounded(period, 1, MAX_VALUE);
        checkIfShuttingDown(task);
        Callable<?> callable = Executors.callable(task);
        LOGGER.info("Register task '{}' at fixed rate: initial delay = {}, period = {}", ClassUtils.getName(task),
                formatDuration(toDuration(initialDelay, unit)), formatDuration(toDuration(period, unit)));
        CallableTaskWrapper<?> callableTask = new CallableTaskWrapper<>(this, callable, initialDelay, unit)
                .trigger(new PeriodicTrigger(period, unit, true));
        registerScheduled(callableTask);
        return callableTask.getFuture();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
        requireNonNull(period);
        return scheduleAtFixedRate(task, getInitialDelay(period), period);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration initialDelay, Duration period) {
        requireNonNull(task);
        requireNonNull(initialDelay);
        requireNonNull(period);
        requireBounded(initialDelay.toMillis(), 0, MAX_VALUE);
        requireBounded(period.toMillis(), 1, MAX_VALUE);
        checkIfShuttingDown(task);
        return scheduleAtFixedRate(task, initialDelay.toMillis(), period.toMillis(), MILLISECONDS);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
        return scheduleAtFixedRate(task, getInitialDelay(delay), delay);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration initialDelay, Duration delay) {
        requireNonNull(task);
        requireNonNull(initialDelay);
        requireNonNull(delay);
        requireBounded(initialDelay.toMillis(), 0, MAX_VALUE);
        requireBounded(delay.toMillis(), 0, MAX_VALUE);
        checkIfShuttingDown(task);
        return scheduleWithFixedDelay(task, initialDelay.toMillis(), delay.toMillis(), MILLISECONDS);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        requireNonNull(task);
        requireNonNull(unit);
        requireBounded(initialDelay, 0, MAX_VALUE);
        requireBounded(delay, 0, MAX_VALUE);
        checkIfShuttingDown(task);
        LOGGER.info("Register task '{}' with fixed delay: initial delay = {}, delay = {}", ClassUtils.getName(task),
                formatDuration(toDuration(initialDelay, unit)), formatDuration(toDuration(delay, unit)));
        Callable<?> callable = Executors.callable(task);
        CallableTaskWrapper<?> callableTask = new CallableTaskWrapper<>(this, callable, initialDelay, unit)
                .trigger(new PeriodicTrigger(delay, unit, false));
        registerScheduled(callableTask);
        return callableTask.getFuture();
    }

    @Override
    public Collection<TaskDescriptor> getRunningTasks() {
        return running.stream().map(TaskDescriptor.class::cast).toList();
    }

    @Override
    public Collection<TaskDescriptor> getPendingTasks() {
        Collection<TaskDescriptor> pending = new ArrayList<>();
        pending.addAll(taskQueue.stream().map(TaskDescriptor.class::cast).toList());
        pending.addAll(extraTaskQueue.stream().map(TaskDescriptor.class::cast).toList());
        return pending;
    }

    @Override
    public Collection<TaskDescriptor> getScheduledTasks() {
        return scheduled.stream().map(TaskDescriptor.class::cast).toList();
    }

    @Override
    public Collection<TaskDescriptor> getCompletedTasks() {
        return unmodifiableCollection(completed);
    }

    @Override
    public Collection<Thread> getThreads() {
        return factory.getThreads();
    }

    @Override
    public Metrics getMetrics() {
        return metrics;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ThreadPoolImpl that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
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
        DetachedTaskDescriptor completedTask = new DetachedTaskDescriptor(this, task);
        for (int i = 0; i < 10; i++) {
            if (completed.offer(completedTask)) break;
            completed.poll();
            ThreadUtils.interrupt();
        }
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
        failedTaskCount.incrementAndGet();
        options.getFailedHandler().failed(this, Thread.currentThread(), throwable, task.unwrapTask());
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

    private Duration getInitialDelay(Duration period) {
        if (NO_INITIAL_DELAY) return Duration.ZERO;
        int seconds = (int) Math.max(30, Math.min(60, period.toSeconds() / 10));
        seconds = ThreadLocalRandom.current().nextInt(seconds);
        Duration initialDelay = Duration.ofSeconds(seconds);
        return INITIAL_DELAY.plus(initialDelay);
    }

    private void handleScheduledAndDelayed() {
        for (; ; ) {
            CallableTaskWrapper<?> callableTask = delayedTaskQueue.poll();
            if (callableTask == null) break;
            callableTask.markScheduled();
            if (!taskQueue.offer(callableTask)) extraTaskQueue.add(callableTask);
        }
    }

    private void registerScheduled(CallableTaskWrapper<?> callableTask) {
        callableTask.markScheduled();
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
        options.getRejectedHandler().rejected(this, runnable);
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
        public int getFailedTaskCount() {
            return failedTaskCount.get();
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
