package net.microfalx.threadpool;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.threadpool.ThreadPoolUtils.getThreadPoolId;

public class JdkThreadPoolImpl extends AbstractExecutorService implements ThreadPool {

    private final String id;
    private final OptionsImpl options;
    private final ScheduledThreadPoolExecutor executor;
    private final MetricsImpl metrics = new MetricsImpl();

    private static final ZonedDateTime startTime = ZonedDateTime.now();

    JdkThreadPoolImpl(BlockingQueue<TaskWrapper<?, ?>> taskQueue, OptionsImpl options) {
        requireNonNull(taskQueue);
        requireNonNull(options);
        executor = new ScheduledThreadPoolExecutor(options.getMaximumSize(), new ThreadPoolImpl(),
                new RejectedExecutionHandlerImpl());
        this.id = getThreadPoolId(options);
        this.options = options;
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
        return 0;
    }

    @Override
    public void suspend() {
        // No operation for JDK thread pool
    }

    @Override
    public void resume() {
        // No operation for JDK thread pool
    }

    @Override
    public boolean isPaused() {
        return false;
    }

    @Override
    public boolean isActive() {
        return false;
    }

    @Override
    public boolean isIdle() {
        return false;
    }

    @Override
    public void setMaximumSize(int maximumSize) {
        // No operation for JDK thread pool
    }

    @Override
    public Collection<TaskDescriptor> getRunningTasks() {
        return List.of();
    }

    @Override
    public Collection<TaskDescriptor> getPendingTasks() {
        return List.of();
    }

    @Override
    public Collection<TaskDescriptor> getScheduledTasks() {
        return List.of();
    }

    @Override
    public Collection<TaskDescriptor> getCompletedTasks() {
        return List.of();
    }

    @Override
    public Collection<Thread> getThreads() {
        return List.of();
    }

    @Override
    public Metrics getMetrics() {
        return metrics;
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable task, Trigger trigger) {
        if (trigger instanceof IntervalAwareTrigger intervalAwareTrigger) {
            return scheduleAtFixedRate(task, intervalAwareTrigger.getInterval());
        } else if (trigger instanceof CronTrigger cronTrigger) {
            return scheduleAtFixedRate(task, cronTrigger.getInterval());
        } else {
            throw new IllegalArgumentException("Unsupported trigger type: " + trigger.getClass());
        }
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period) {
        return executor.scheduleAtFixedRate(task, period.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration initialDelay, Duration period) {
        return executor.scheduleAtFixedRate(task, initialDelay.toMillis(), period.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay) {
        return executor.scheduleWithFixedDelay(task, delay.toMillis(), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration initialDelay, Duration delay) {
        return executor.scheduleWithFixedDelay(task, initialDelay.toMillis(), delay.toMillis(), TimeUnit.MILLISECONDS);
    }

    @Override
    public ScheduledFuture<?> schedule(Runnable command, long delay, TimeUnit unit) {
        return executor.schedule(command, delay, unit);
    }

    @Override
    public <V> ScheduledFuture<V> schedule(Callable<V> callable, long delay, TimeUnit unit) {
        return executor.schedule(callable, delay, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleAtFixedRate(Runnable command, long initialDelay, long period, TimeUnit unit) {
        return executor.scheduleAtFixedRate(command, initialDelay, period, unit);
    }

    @Override
    public ScheduledFuture<?> scheduleWithFixedDelay(Runnable command, long initialDelay, long delay, TimeUnit unit) {
        return executor.scheduleWithFixedDelay(command, initialDelay, delay, unit);
    }

    @Override
    public void shutdown() {
        executor.shutdown();
    }

    @Override
    public List<Runnable> shutdownNow() {
        return executor.shutdownNow();
    }

    @Override
    public boolean isShutdown() {
        return executor.isShutdown();
    }

    @Override
    public boolean isTerminated() {
        return executor.isTerminated();
    }

    @Override
    public boolean awaitTermination(long timeout, TimeUnit unit) throws InterruptedException {
        return executor.awaitTermination(timeout, unit);
    }

    @Override
    public void execute(Runnable command) {
        executor.execute(command);
    }

    private class ThreadPoolImpl implements java.util.concurrent.ThreadFactory {

        private final AtomicInteger threadPoolIndex = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread thread = new Thread(r);
            thread.setDaemon(options.isDaemon());
            thread.setName(options.getNamePrefix() + "-" + threadPoolIndex.getAndIncrement());
            if (threadPoolIndex.get() >= options.getMaximumSize()) threadPoolIndex.set(1);
            return thread;
        }
    }

    private class RejectedExecutionHandlerImpl implements RejectedExecutionHandler {

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            RejectedHandler rejectedHandler = options.getRejectedHandler();
            if (rejectedHandler != null) rejectedHandler.rejected(JdkThreadPoolImpl.this, r);
        }
    }

    private class MetricsImpl implements Metrics {

        @Override
        public ZonedDateTime getCreatedTime() {
            return startTime;
        }

        @Override
        public int getRunningTaskCount() {
            return 0;
        }

        @Override
        public int getPendingTaskCount() {
            return 0;
        }

        @Override
        public int getFailedTaskCount() {
            return 0;
        }

        @Override
        public long getExecutedTaskCount() {
            return 0;
        }

        @Override
        public int getAvailableThreadCount() {
            return 0;
        }

        @Override
        public int getCreatedThreadCount() {
            return 0;
        }

        @Override
        public int getDestroyedThreadCount() {
            return 0;
        }
    }

}
