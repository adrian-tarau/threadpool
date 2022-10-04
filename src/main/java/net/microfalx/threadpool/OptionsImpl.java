package net.microfalx.threadpool;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;

class OptionsImpl implements ThreadPool.Options {

    volatile int maximumSize = 10;
    Duration keepAliveTime = ofSeconds(60);
    Duration maximumReuseTime = ofMinutes(15);
    String namePrefix;
    boolean daemon;
    int queueSize = 100;
    ThreadPool.RejectedHandler rejectedHandler = new CallerRunsPolicy();
    ThreadPool.FailedHandler failureHandler = new LogFailedHandler();
    Collection<ThreadPool.ExecutionCallback> executionCallbacks = new CopyOnWriteArrayList<>();
    Collection<ThreadPool.ScheduleCallback> scheduleCallbacks = new CopyOnWriteArrayList<>();

    @Override
    public int getMaximumSize() {
        return maximumSize;
    }

    @Override
    public void setMaximumSize(int maximumSize) {
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

    static class CallerRunsPolicy implements ThreadPool.RejectedHandler {

        @Override
        public void rejected(Runnable runnable, ThreadPool pool) {
            if (!pool.isShutdown()) {
                runnable.run();
            }
        }
    }

    static class LogFailedHandler implements ThreadPool.FailedHandler {

        @Override
        public void failed(Runnable runnable, ThreadPool pool, Throwable throwable) {
            ThreadPoolImpl.LOGGER.error("Received an exception during execution of task " + runnable + " in thread pool " + pool.getOptions().getNamePrefix(), throwable);
        }

    }

}
