package net.microfalx.threadpool;

import static net.microfalx.threadpool.ThreadPoolUtils.requireNonNull;

abstract class TaskWrapper<T, R> {

    private final ThreadPoolImpl threadPool;
    private final T task;

    volatile Mode mode = Mode.SINGLE;
    volatile long lastScheduled = System.nanoTime();
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

    R execute() throws Exception {
        beforeExecute();
        R result = null;
        try {
            result = doExecute();
        } finally {
            afterExecute(result);
        }
        return result;
    }

    abstract R doExecute() throws Exception;

    void beforeExecute() {

    }

    void afterExecute(R result) {

    }

    enum Mode {
        SINGLE,
        FIXED_RATE,
        FIXED_DELAY
    }
}
