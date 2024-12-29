package net.microfalx.threadpool;

import net.microfalx.lang.ExceptionUtils;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

abstract class TaskWrapper<T, R> {

    private final ThreadPoolImpl threadPool;
    private final T task;

    volatile Mode mode = Mode.SINGLE;
    volatile long lastScheduled = System.currentTimeMillis();
    volatile long lastExecuted;

    TaskWrapper(ThreadPoolImpl threadPool, T task) {
        requireNonNull(threadPool);
        requireNonNull(task);
        this.threadPool = threadPool;
        this.task = task;
    }

    T getTask() {
        return task;
    }

    R execute() {
        beforeExecute();
        R result = null;
        try {
            result = doExecute();
        } catch (Throwable e) {
            threadPool.failedTask(this, e);
            if (e instanceof InterruptedException ie) ExceptionUtils.rethrowInterruptedException(ie);
        } finally {
            lastExecuted = System.currentTimeMillis();
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
        // empty by design
    }

    enum Mode {
        SINGLE,
        FIXED_RATE,
        FIXED_DELAY
    }
}
