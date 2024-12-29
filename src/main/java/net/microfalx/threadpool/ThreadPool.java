package net.microfalx.threadpool;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.*;

import static net.microfalx.lang.ArgumentUtils.*;

/**
 * A thread pool abstraction.
 */
public interface ThreadPool extends ScheduledExecutorService {

    /**
     * Creates a thread pool with a default naming scheme.
     *
     * @return a non-null instance
     */
    static ThreadPool create() {
        return create("Default");
    }

    /**
     * Creates a thread pool which adjusts automatically to the number of CPUs.
     *
     * @param namePrefix the thread name prefix
     * @return a non-null instance
     */
    static ThreadPool create(String namePrefix) {
        return new Builder(namePrefix).maximumSize(Runtime.getRuntime().availableProcessors() * 2).build();
    }

    /**
     * Creates a thread pool builder.
     *
     * @param namePrefix the thread name prefix
     * @return a non-null instance
     */
    static Builder builder(String namePrefix) {
        return new Builder(namePrefix);
    }

    /**
     * Returns the options of this thread pool.
     *
     * @return a non-null instance
     */
    Options getOptions();

    /**
     * Returns the index associated with a thread part of the thread pool.
     * <p>
     * Each thread receives a unique index between 1 and {@link Options#getMaximumSize()}.
     *
     * @param thread the thread
     * @return the index
     * @throws IllegalArgumentException if the thread does not belong to the pool
     */
    int getIndex(Thread thread);

    /**
     * Suspends the execution of scheduled tasks.
     * <p>
     * Tasks already in progress will continue to run.
     */
    void suspend();

    /**
     * Resumes execution of scheduled tasks.
     */
    void resume();

    /**
     * Returns whether the thread pool is suspended (paused).
     *
     * @return {@code true} if suspended, {@code false}
     */
    boolean isPaused();

    /**
     * Returns whether the pool is actively running tasks.
     *
     * @return {@code true} if the pool is executing tasks, {@code false} otherwise
     */
    boolean isActive();

    /**
     * Returns whether the pool is not running any tasks and the queue is empty,
     *
     * @return {@code true} if the pool is executing tasks, {@code false} otherwise
     */
    boolean isIdle();

    /**
     * Changes the maximum number of threads allowed to be created and maintained by the pool.
     *
     * @param maximumSize a positive integer >= 1
     */
    void setMaximumSize(int maximumSize);

    /**
     * Returns a collection with running tasks.
     *
     * @return a non-null instance
     */
    Collection<?> getRunningTasks();

    /**
     * Returns a collection with pending tasks.
     *
     * @return a non-null instance
     */
    Collection<?> getPendingTasks();

    /**
     * Returns a collection with scheduled tasks.
     *
     * @return a non-null instance
     */
    Collection<?> getScheduledTasks();

    /**
     * Returns metrics about this pool.
     *
     * @return a non-null instance
     */
    Metrics getMetrics();

    /**
     * Options for a thread pool.
     */
    interface Options {

        /**
         * Returns the maximum number of threads allowed to be created and maintained by the pool.
         *
         * @return a positive integer >= 1
         */
        int getMaximumSize();

        /**
         * Changes the maximum number of threads allowed to be created and maintained by the pool.
         *
         * @param maximumSize a positive integer >= 1
         */
        void setMaximumSize(int maximumSize);

        /**
         * Returns the queue maximum size.
         * <p>
         * Some thread pools might have a queue implementation which does not obey the queue limits.
         *
         * @return a positive integer
         */
        int getQueueSize();

        /**
         * Returns the inactive timeout.
         * <p>
         * The inactive timeout specifies how long a thread can remain idle before removed from the pool.
         *
         * @return a positive duration
         */
        Duration getKeepAliveTime();

        /**
         * Returns the maximum amount a thread is allowed to stay in the pool (used or idle).
         * <p>
         * This timeout allows for threads (and their locals) recycling based on time. Sometimes threads hold/accumulate resources
         * in various services and limiting the amount of time they are kept alive helps with resource management.
         *
         * @return a positive duration
         */
        Duration getMaximumReuseTime();

        /**
         * Returns the handler of executable tasks.
         *
         * @return a non-null instance
         */
        RejectedHandler getRejectedHandler();

        /**
         * Returns the handler of failed tasks.
         *
         * @return a non-null instance
         */
        FailedHandler getFailedHandler();

        /**
         * Returns an optional thread group.
         *
         * @return a non-null instance
         */
        Optional<String> getThreadGroup();

        /**
         * Returns an optional thread factory.
         *
         * @return a non-null instance
         */
        Optional<ThreadFactory> getThreadFactory();

        /**
         * Returns the prefix used to generate thread names.
         * <p>
         * The name of the thread will be the prefix + a generated index from 1 to MAX SIZE.
         *
         * @return a non-null instance
         */
        String getNamePrefix();

        /**
         * Returns weather the thread pool is running with daemon threads.
         *
         * @return <code>true</code> if daemon, <code>false</code> otherwise
         */
        boolean isDaemon();

        /**
         * Returns weather the thread pool is running with virtual threads.
         *
         * @return <code>true</code> if daemon, <code>false</code> otherwise
         */
        boolean isVirtual();

    }

    /**
     * A callback interface used to intercept task execution.
     */
    interface ExecutionCallback {

        /**
         * Executed before the task is executed by a given thread.
         *
         * @param task   the task
         * @param thread the thread that will execute the task
         * @param <T>    the task's type
         * @return {@code true} to execute the task, {@code false} otherwise
         */
        default <T> boolean beforeExecution(T task, Thread thread) {
            return true;
        }

        /**
         * Executed after the task was executed by a given thread.
         *
         * @param task   the task
         * @param thread the thread that will execute the task
         * @param <T>    the task's type
         */
        default <T> void afterExecution(T task, Thread thread) {
            // empty on purpose
        }

    }

    /**
     * A callback interface used to intercept task scheduling
     */
    interface ScheduleCallback {

        /**
         * Returns whether the task is a singleton.
         * <p>
         * A singleton task ensures that only one task can exist in the queue at any moment in time.
         *
         * @param task the task
         * @param <T>  the task's type
         * @return {@code true} if the task is a singleton, {@code false} otherwise
         */
        default <T> boolean isSingleton(T task) {
            return false;
        }

        /**
         * Returns the key used to determine when a given task is already present in the queue.
         *
         * @param task the task
         * @param <T>  the task's type
         * @return the key, defaults to the task class
         */
        default <T> Object getKey(T task) {
            return task.getClass();
        }

    }

    /**
     * A handler unhandled exceptions raised during task execution.
     */
    interface FailedHandler {

        /**
         * Invoked whenever a scheduled task fails be executed.
         *
         * @param runnable  the runnable
         * @param pool      the executor attempting to execute this task
         * @param throwable the exception
         */
        void failed(Runnable runnable, ThreadPool pool, Throwable throwable);
    }

    /**
     * A handler for tasks that cannot be executed by the thread pool.
     */
    interface RejectedHandler {

        /**
         * Invoked whenever a scheduled task cannot be executed.
         *
         * <p>In the absence of other alternatives, the method may throw
         * an unchecked {@link RejectedExecutionException}, which will be
         * propagated to the caller of {@code execute}.
         *
         * @param runnable the runnable task requested to be executed
         * @param pool     the executor attempting to execute this task
         * @throws RejectedExecutionException if there is no remedy
         */
        void rejected(Runnable runnable, ThreadPool pool);
    }

    /**
     * A handler for tasks that cannot be executed by the thread pool due to limits in number of instances.
     */
    interface SingletonHandler {

        /**
         * Invoked whenever a scheduled task cannot be executed because it reached the number of maximum allowed
         * instances.
         *
         * @param runnable the runnable task requested to be executed
         * @param pool     the executor attempting to execute this task
         */
        void rejected(Runnable runnable, ThreadPool pool);
    }

    /**
     * An interface which provides metrics about a thread pool.
     */
    interface Metrics {

        /**
         * Returns the time when the object pool was created.
         *
         * @return a non-null instance
         */
        ZonedDateTime getCreatedTime();

        /**
         * Returns the number of tasks running.
         *
         * @return a positive integer
         */
        int getRunningTaskCount();

        /**
         * Returns the number of tasks pending for execution.
         *
         * @return a positive integer
         */
        int getPendingTaskCount();

        /**
         * Returns the number of tasks executed by the pool.
         *
         * @return a positive integer
         */
        long getExecutedTaskCount();

        /**
         * Returns the number of threads currently created and available to execute tasks in the pool.
         *
         * @return a positive integer
         */
        int getAvailableThreadCount();

        /**
         * Returns the number of threads created by this pool.
         *
         * @return a positive integer
         */
        int getCreatedThreadCount();

        /**
         * Returns the number of threads destroyed by this pool.
         *
         * @return a positive integer
         */
        int getDestroyedThreadCount();
    }

    /**
     * A builder for a thread pool.
     */
    class Builder {

        private final OptionsImpl options = new OptionsImpl();
        private BlockingQueue<TaskWrapper<?, ?>> queue = new ArrayBlockingQueue<>(options.getQueueSize());
        private Builder(String namePrefix) {
            requireNotEmpty(namePrefix);
            options.namePrefix = namePrefix;
        }

        /**
         * Changes the maximum number of threads.
         *
         * @param maximumSize the maximum number of threads
         * @return self
         * @see Options#getMaximumSize() ()
         */
        public Builder maximumSize(int maximumSize) {
            requireBounded(maximumSize, 1, ThreadPoolUtils.MAXIMUM_POOL_SIZE);
            options.maximumSize = maximumSize;
            return this;
        }

        /**
         * Changes the maximum reuse time of threads.
         *
         * @param maximumReuseTime the maximum reuse time
         * @return self
         */
        public Builder maximumReuseTime(Duration maximumReuseTime) {
            requireNonNull(maximumReuseTime);
            options.maximumReuseTime = maximumReuseTime;
            return this;
        }

        /**
         * Changes the keep alive time.
         *
         * @param keepAliveTime the keep alive time
         * @return self
         */
        public Builder keepAliveTime(Duration keepAliveTime) {
            requireNonNull(keepAliveTime);
            options.keepAliveTime = keepAliveTime;
            return this;
        }

        /**
         * Changes the queue size (defaults to 100).
         * <p>
         * Large queues are usually not common, but the thread pool applies a high limit of 1,000,000 tasks.
         *
         * @param queueSize the new queue size, a value between 1 and 1,000,000
         * @return self
         */
        public Builder queueSize(int queueSize) {
            requireBounded(queueSize, 1, ThreadPoolUtils.MAXIMUM_QUEUE_SIZE);
            options.queueSize = queueSize;
            queue = new ArrayBlockingQueue<>(queueSize);
            return this;
        }

        /**
         * Changes the thread factory.
         * <p>
         * If a custom thread factory is provided, some options do not apply anymore: thread naming, virtual or physical, etc
         *
         * @param threadFactory the thread factory
         * @return self
         */
        public Builder threadFactory(ThreadFactory threadFactory) {
            options.threadFactory = threadFactory;
            return this;
        }

        /**
         * Changes the rejected task handled.
         *
         * @param handler the handler
         * @return self
         */
        public Builder rejectedHandler(RejectedHandler handler) {
            requireNonNull(handler);
            options.rejectedHandler = handler;
            return this;
        }

        /**
         * Changes the singleton task handled.
         *
         * @param handler the handler
         * @return self
         */
        public Builder singletonHandler(SingletonHandler handler) {
            requireNonNull(handler);
            options.singletonHandler = handler;
            return this;
        }

        /**
         * Changes the rejected task handled.
         *
         * @param handler the handler
         * @return self
         */
        public Builder failureHandler(FailedHandler handler) {
            requireNonNull(handler);
            options.failureHandler = handler;
            return this;
        }

        /**
         * Registers a callback to intercept task executions.
         *
         * @param callback the callback
         * @return self
         */
        public Builder executionCallback(ExecutionCallback callback) {
            requireNonNull(callback);
            options.executionCallbacks.add(callback);
            return this;
        }

        /**
         * Registers a callback to intercept task scheduling.
         *
         * @param callback the callback
         * @return self
         */
        public Builder scheduleCallback(ScheduleCallback callback) {
            requireNonNull(callback);
            options.scheduleCallbacks.add(callback);
            return this;
        }

        /**
         * Changes the daemon flag.
         *
         * @param daemon {@code true} to create daemon threads, {@code false} otherwise
         * @return self
         */
        public Builder daemon(boolean daemon) {
            options.daemon = daemon;
            return this;
        }

        /**
         * Changes the virtual threads flag.
         *
         * @param virtual {@code true} to use virtual threads, {@code false} otherwise
         * @return self
         */
        public Builder virtual(boolean virtual) {
            options.virtual = virtual;
            return this;
        }

        /**
         * Creates the thread pool.
         *
         * @return a non-null instance
         */
        public ThreadPool build() {
            return new ThreadPoolImpl(queue, options);
        }
    }
}
