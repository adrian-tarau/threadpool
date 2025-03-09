package net.microfalx.threadpool;

import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.Descriptable;
import net.microfalx.lang.Nameable;
import net.microfalx.metrics.Metrics;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.StringJoiner;

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

    volatile long lastScheduledExecution = currentTimeMillis();
    volatile long lastActualExecution;
    volatile long lastCompletion;
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
        return lastActualExecution == 0 ? null : toLocalDateTime(lastActualExecution);
    }

    @Override
    public Duration getDuration() {
        if (thread != null) {
            return ofMillis(currentTimeMillis() - lastActualExecution);
        } else {
            return duration;
        }
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
        this.lastActualExecution = currentTimeMillis();
        long start = System.nanoTime();
        try {
            result = METRICS.timeCallable(ClassUtils.getCompactName(getTaskClass()), this::doExecute);
        } catch (Throwable e) {
            throwable = e;
            threadPool.failedTask(this, e);
        } finally {
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
