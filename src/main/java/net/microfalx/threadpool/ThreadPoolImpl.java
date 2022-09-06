package net.microfalx.threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

import static net.microfalx.threadpool.ThreadPoolUtils.requireBounded;
import static net.microfalx.threadpool.ThreadPoolUtils.requireNonNull;

/**
 * Thread pool implementation.
 */
final class ThreadPoolImpl extends AbstractExecutorService implements ThreadPool {

    final static Logger LOGGER = LoggerFactory.getLogger(ThreadPoolImpl.class);

    private final BlockingQueue<TaskWrapper<?, ?>> taskQueue;
    private final BlockingQueue<TaskWrapper<?, ?>> extraTaskQueue;
    private final DelayQueue<CallableTaskWrapper<?>> delayedTaskQueue;
    private final Queue<ThreadImpl> threadQueue = new ConcurrentLinkedDeque<>();
    private final Collection<TaskWrapper<?, ?>> running = new CopyOnWriteArrayList<>();
    private final OptionsImpl options;
    private final ThreadFactory factory;
    private final Dispatcher dispatcher;
    private final Lock lock = new ReentrantLock();
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
        this.factory = new ThreadFactory(this, options.getNamePrefix());
        this.dispatcher = Dispatcher.getInstance();
        this.delayedTaskQueue = new DelayQueue<>();
        this.extraTaskQueue = new LinkedBlockingQueue<>();
        Dispatcher.getInstance().register(this);
    }

    @Override
    public Options getOptions() {
        return options;
    }

    @Override
    public int getIndex(Thread thread) {
        requireNonNull(thread);

        if (thread instanceof ThreadImpl) {
            return ((ThreadImpl) thread).getIndex();
        } else {
            throw new IllegalArgumentException("Thread '" + thread.getClass().getName() + "' is not part of the thread pool");
        }
    }

    @Override
    public void shutdown() {
        shutdown.set(true);
        Dispatcher.getInstance().unregister(this);
        tryTerminate();
    }

    @Override
    public List<Runnable> shutdownNow() {
        shutdown();
        List<TaskWrapper<?, ?>> pending = new ArrayList<>();
        taskQueue.drainTo(pending);
        extraTaskQueue.drainTo(pending);
        return pending.stream().filter(t -> t instanceof RunnableTaskWrapper).map(t -> (Runnable) t.getTask()).collect(Collectors.toUnmodifiableList());
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
        lock.lock();
        try {
            return !running.isEmpty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean isIdle() {
        lock.lock();
        try {
            return running.isEmpty() && isQueueEmpty();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        requireNonNull(unit);
        requireBounded(timeout, 0, Long.MAX_VALUE);

        return terminatedLatch.await(timeout, unit);
    }

    @Override
    public void execute(Runnable task) {
        requireNonNull(task);

        checkIfShuttingDown(task);

        if (task instanceof ScheduledTask) {
            ScheduledTask scheduledTask = (ScheduledTask) task;
            switch (scheduledTask.getStrategy()) {
                case FIXED_RATE:
                    scheduleAtFixedRate(task, scheduledTask.getInitialDelay().toMillis(),
                            scheduledTask.getInterval().toMillis(), TimeUnit.MILLISECONDS);
                    break;
                case FIXED_DELAY:
                    scheduleWithFixedDelay(task, scheduledTask.getInitialDelay().toMillis(),
                            scheduledTask.getInterval().toMillis(), TimeUnit.MILLISECONDS);
                    break;
            }
        } else if (task instanceof DelayedTask) {
            schedule(task, ((DelayedTask) task).getDelay().toMillis(), TimeUnit.MILLISECONDS);
        } else {
            if (!taskQueue.offer(new RunnableTaskWrapper(this, task))) {
                reject(task);
            }
            dispatcher.wakeUp(this);
        }
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, long delay, TimeUnit unit) {
        requireNonNull(task);
        requireNonNull(unit);
        requireBounded(delay, 0, Long.MAX_VALUE);

        checkIfShuttingDown(task);
        Callable<?> callable = Executors.callable(task);
        CallableTaskWrapper<?> callableTask = new CallableTaskWrapper<>(this, callable, delay, unit);
        delayedTaskQueue.offer(callableTask);
        return callableTask.getFuture();
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        requireNonNull(callable);
        requireNonNull(unit);
        requireBounded(delay, 0, Long.MAX_VALUE);

        CallableTaskWrapper<V> callableTask = new CallableTaskWrapper<>(this, callable, delay, unit);
        delayedTaskQueue.offer(callableTask);
        return callableTask.getFuture();
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        requireNonNull(task);
        requireNonNull(unit);
        requireBounded(initialDelay, 0, Long.MAX_VALUE);
        requireBounded(period, 1, Long.MAX_VALUE);

        checkIfShuttingDown(task);

        Callable<?> callable = Executors.callable(task);
        CallableTaskWrapper<?> callableTask = new CallableTaskWrapper<>(this, callable, initialDelay, unit).interval(TaskWrapper.Mode.FIXED_RATE, period, unit);
        delayedTaskQueue.offer(callableTask);
        return callableTask.getFuture();
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, long initialDelay, long delay, TimeUnit unit) {
        requireNonNull(task);
        requireNonNull(unit);
        requireBounded(initialDelay, 0, Long.MAX_VALUE);
        requireBounded(delay, 0, Long.MAX_VALUE);

        checkIfShuttingDown(task);

        Callable<?> callable = Executors.callable(task);
        CallableTaskWrapper<?> callableTask = new CallableTaskWrapper<>(this, callable, initialDelay, unit).interval(TaskWrapper.Mode.FIXED_DELAY, delay, unit);
        delayedTaskQueue.offer(callableTask);
        return callableTask.getFuture();
    }

    @Override
    public Metrics getMetrics() {
        return metrics;
    }

    /**
     * Invoked by the dispatcher to process any available tasks.
     *
     * @return {@code true} if the pool had at least one tasks scheduled, {@code false} otherwise
     */
    boolean handleTasks() {
        if (!hasQueued() || isPaused()) return false;
        boolean hasTasks = false;
        int maxTasks = Math.max(1, this.factory.getSize());
        lock.lock();
        try {
            while (maxTasks-- > 0 && hasQueued()) {
                hasTasks |= handleNextTask();
                if (!hasTasks) break;
            }
            return hasTasks;
        } finally {
            lock.unlock();
        }
    }

    /**
     * Returns whether the queue has at least one task queued.
     *
     * @return {@code true} if there is at least one task.
     */
    private boolean hasQueued() {
        for (; ; ) {
            CallableTaskWrapper<?> callableTask = delayedTaskQueue.poll();
            if (callableTask == null) break;
            if (!taskQueue.offer(callableTask)) extraTaskQueue.add(callableTask);
        }
        return !isQueueEmpty();
    }

    /**
     * Invoked by the dispatcher to push scheduled tasks to free threads.
     *
     * @return {@code true} if the pool had a task scheduled for execution, {@code false} otherwise
     */
    private boolean handleNextTask() {
        ThreadImpl thread = getNextFreeThread();
        if (thread != null) {
            TaskWrapper<?, ?> task = nextTask();
            if (task != null) {
                thread.awakeThread(task);
            } else {
                threadQueue.offer(thread);
            }
            return task != null;
        } else {
            return false;
        }
    }

    /**
     * Returns the next available thread from the pool.
     *
     * @return the thread, null if no thread is available
     */
    ThreadImpl getNextFreeThread() {
        ThreadImpl nextThread = threadQueue.poll();
        if (nextThread != null && nextThread.isDead()) {
            factory.destroyThread(nextThread);
            nextThread = null;
        }
        if (nextThread == null && factory.getSize() < options.getMaximumSize()) {
            nextThread = factory.createThread();
        }
        return nextThread;
    }

    /**
     * Called from a pooled thread to mark the execution of the task completed.
     *
     * @param thread the thread
     * @param task   the task
     */
    void completeTask(ThreadImpl thread, TaskWrapper<?, ?> task) {
        executedTaskCount.incrementAndGet();
        running.remove(task);
        threadQueue.offer(thread);
        updateTask(task);
        tryTerminate();
    }

    /**
     * Called from a pooled thread to mark the execution of the task completed.
     *
     * @param thread    the thread
     * @param task      the task
     * @param throwable the exception
     */
    void failedTask(ThreadImpl thread, TaskWrapper<?, ?> task, Throwable throwable) {
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
    TaskWrapper<?, ?> nextTask() {
        if (shutdown.get()) return null;
        TaskWrapper<?, ?> task = taskQueue.poll();
        if (task == null) {
            task = extraTaskQueue.poll();
        }
        if (task != null) running.add(task);
        return task;
    }

    private void updateTask(TaskWrapper<?, ?> task) {
        task.lastExecuted = System.currentTimeMillis();
        if (task instanceof CallableTaskWrapper) {
            CallableTaskWrapper<?> callableTask = (CallableTaskWrapper<?>) task;
            if (callableTask.isPeriodic()) {
                callableTask.updateDelay();
                delayedTaskQueue.offer(callableTask);
            }
        }
    }

    private void tryTerminate() {
        if (!terminated.get() && shutdown.get() && isQueueEmpty() && running.isEmpty()) {
            terminated.set(true);
            terminatedLatch.countDown();
        }
    }

    private void checkIfShuttingDown(Runnable runnable) {
        if (shutdown.get() && runnable != null) {
            reject(runnable);
        }
    }

    private void reject(Runnable command) {
        options.getRejectedHandler().rejected(command, this);
    }

    private boolean isQueueEmpty() {
        return taskQueue.isEmpty() && extraTaskQueue.isEmpty();
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
