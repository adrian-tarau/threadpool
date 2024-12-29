package net.microfalx.threadpool;

import java.time.Duration;
import java.util.Collection;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadFactory;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static net.microfalx.lang.ArgumentUtils.requireBounded;
import static net.microfalx.threadpool.ThreadPoolUtils.MAXIMUM_POOL_SIZE;

class OptionsImpl implements ThreadPool.Options {

    volatile int maximumSize = 5;
    Duration keepAliveTime = ofSeconds(60);
    Duration maximumReuseTime = ofMinutes(15);
    ThreadFactory threadFactory;
    String threadGroup;
    String namePrefix = "Default";
    boolean daemon = true;
    boolean virtual = false;
    int queueSize = 100;
    ThreadPool.RejectedHandler rejectedHandler = new CallerRunsPolicy();
    ThreadPool.FailedHandler failureHandler = new LogFailedHandler();
    ThreadPool.SingletonHandler singletonHandler;
    Collection<ThreadPool.ExecutionCallback> executionCallbacks = new CopyOnWriteArrayList<>();
    Collection<ThreadPool.ScheduleCallback> scheduleCallbacks = new CopyOnWriteArrayList<>();

    @Override
    public int getMaximumSize() {
        return maximumSize;
    }

    @Override
    public void setMaximumSize(int maximumSize) {
        requireBounded(maximumSize, 1, MAXIMUM_POOL_SIZE);
        this.maximumSize = maximumSize;
    }

    @Override
    public int getQueueSize() {
        return queueSize;
    }

    @Override
    public String getNamePrefix() {
        return namePrefix;
    }

    @Override
    public boolean isDaemon() {
        return daemon;
    }

    @Override
    public boolean isVirtual() {
        return virtual;
    }

    @Override
    public Duration getKeepAliveTime() {
        return keepAliveTime;
    }

    @Override
    public Duration getMaximumReuseTime() {
        return maximumReuseTime;
    }

    @Override
    public ThreadPool.RejectedHandler getRejectedHandler() {
        return rejectedHandler;
    }

    @Override
    public ThreadPool.FailedHandler getFailedHandler() {
        return failureHandler;
    }

    @Override
    public Optional<String> getThreadGroup() {
        return Optional.ofNullable(threadGroup);
    }

    @Override
    public Optional<ThreadFactory> getThreadFactory() {
        return Optional.ofNullable(threadFactory);
    }

    static class CallerRunsPolicy implements ThreadPool.RejectedHandler {

        @Override
        public void rejected(Runnable runnable, ThreadPool pool) {
            if (!pool.isShutdown()) runnable.run();
        }
    }

    static class LogFailedHandler implements ThreadPool.FailedHandler {

        @Override
        public void failed(Runnable runnable, ThreadPool pool, Throwable throwable) {
            ThreadPoolImpl.LOGGER.error("Received an exception during execution of task " + runnable + " in thread pool " + pool.getOptions().getNamePrefix(), throwable);
        }

    }

}
