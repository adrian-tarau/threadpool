package net.microfalx.threadpool;

import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.Descriptable;
import net.microfalx.lang.Nameable;
import net.microfalx.lang.TimeUtils;
import net.microfalx.metrics.Metrics;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicInteger;

import static java.lang.System.currentTimeMillis;
import static java.time.Duration.ofMillis;
import static java.time.Duration.ofNanos;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.StringUtils.beautifyCamelCase;
import static net.microfalx.lang.TimeUtils.toLocalDateTime;
import static net.microfalx.threadpool.ThreadPoolUtils.ID_GENERATOR;

abstract class TaskWrapper<T, R> implements TaskDescriptor {

    static Metrics METRICS = ThreadPoolUtils.METRICS.withGroup("Execute");

    private final ThreadPoolImpl threadPool;
    private final T task;
    private final long id;

    private final AtomicInteger executionCount = new AtomicInteger();
    volatile long lastScheduledExecution = Long.MIN_VALUE;
    volatile long lastActualExecution = Long.MIN_VALUE;
    volatile long lastCompletion = Long.MIN_VALUE;
    volatile long nextScheduledExecution = Long.MIN_VALUE;
    volatile Throwable throwable;
    volatile Duration duration;
    volatile Thread thread;
    volatile Object unwrappedTask;

    TaskWrapper(ThreadPoolImpl threadPool, T task) {
        requireNonNull(threadPool);
        requireNonNull(task);
        this.threadPool = threadPool;
        this.task = task;
        this.unwrappedTask = unwrapTask();
        this.id = ID_GENERATOR.next();
    }

    @Override
    public Long getId() {
        return id;
    }

    @Override
    public String getName() {
        Object unwrappedTask = unwrapTask();
        return unwrappedTask instanceof Nameable ? ((Nameable) unwrappedTask).getName() : beautifyCamelCase(getTaskClass().getSimpleName());
    }

    @Override
    public String getDescription() {
        Object unwrappedTask = unwrapTask();
        return unwrappedTask instanceof Descriptable ? ((Descriptable) unwrappedTask).getDescription() : null;
    }

    @Override
    public ThreadPool getThreadPool() {
        return threadPool;
    }

    @Override
    public Thread getThread() {
        return thread;
    }

    @Override
    public Class<?> getTaskClass() {
        return unwrapTask().getClass();
    }

    @Override
    public LocalDateTime getStartedAt() {
        return toLocalDateTime(lastActualExecution);
    }

    @Override
    public Duration getDuration() {
        if (thread != null) {
            return lastActualExecution > 0 ? ofMillis(currentTimeMillis() - lastActualExecution) : null;
        } else {
            return duration;
        }
    }

    @Override
    public Integer getExecutionCount() {
        return executionCount.get();
    }

    @Override
    public LocalDateTime getLastExecutionTime() {
        return TimeUtils.toLocalDateTime(lastScheduledExecution);
    }

    @Override
    public LocalDateTime getNextExecutionTime() {
        return TimeUtils.toLocalDateTime(nextScheduledExecution);
    }

    @Override
    public boolean isPeriodic() {
        return false;
    }

    @Override
    public Throwable getThrowable() {
        return throwable;
    }

    Object unwrapTask() {
        if (unwrappedTask == null) {
            unwrappedTask = ThreadPoolUtils.unwrapTask(task);
        }
        return unwrappedTask != null ? unwrappedTask : task;
    }

    T getTask() {
        return task;
    }

    @SuppressWarnings("UnusedReturnValue")
    R execute() {
        beforeExecute();
        this.thread = Thread.currentThread();
        R result = null;
        if (this.lastScheduledExecution <= 0) this.lastScheduledExecution = currentTimeMillis();
        this.lastActualExecution = currentTimeMillis();
        long start = System.nanoTime();
        try {
            result = METRICS.timeCallable(ClassUtils.getCompactName(getTaskClass()), this::doExecute);
        } catch (Throwable e) {
            throwable = e;
            threadPool.failedTask(this, e);
        } finally {
            executionCount.incrementAndGet();
            this.thread = null;
            duration = ofNanos(System.nanoTime() - start);
            lastCompletion = currentTimeMillis();
            threadPool.completeTask(this);
            afterExecute(result);
        }
        return result;
    }

    abstract R doExecute() throws Exception;

    void beforeExecute() {
        // empty by design
    }

    void afterExecute(R result) {
    }

    void updateToString(StringJoiner joiner) {
        // empty by design
    }

    @Override
    public String toString() {
        StringJoiner joiner = new StringJoiner(", ", getClass().getSimpleName() + "[", "]")
                .add("threadPool=" + threadPool.getOptions().getNamePrefix())
                .add("task=" + ClassUtils.getName(task))
                .add("lastScheduled=" + toLocalDateTime(lastScheduledExecution))
                .add("lastExecuted=" + toLocalDateTime(lastActualExecution))
                .add("lastCompleted=" + toLocalDateTime(lastCompletion));
        updateToString(joiner);
        return joiner.toString();
    }
}
