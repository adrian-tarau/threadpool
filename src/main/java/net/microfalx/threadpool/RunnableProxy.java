package net.microfalx.threadpool;

import java.util.StringJoiner;

import static java.lang.System.currentTimeMillis;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.lang.TimeUtils.millisSince;

/**
 * A proxy for the runnable passed to a thread.
 * <p>
 * The proxy executes first the runnable given during construction, and after that it polls for
 * new tasks, if available.
 */
class RunnableProxy implements Runnable {

    private final ThreadPoolImpl threadPool;
    private final ThreadPool.Options options;
    private volatile TaskWrapper<?, ?> task;
    private final long startTime = currentTimeMillis();
    private volatile long lastCompletion;
    private volatile boolean running;
    private volatile boolean loop = true;
    private volatile boolean stopped;

    static final ThreadLocal<ThreadPool> CURRENT_THREAD_POOL = new ThreadLocal<>();

    RunnableProxy(ThreadPoolImpl threadPool, TaskWrapper<?, ?> task) {
        requireNonNull(threadPool);
        this.threadPool = threadPool;
        this.options = threadPool.getOptions();
        this.task = task;
    }

    boolean isRunning() {
        return running;
    }

    boolean isStopped() {
        return !loop && stopped;
    }

    void stop() {
        loop = false;
    }

    @Override
    public void run() {
        try {
            while (loop) {
                processTask();
                if (shouldStop()) break;
            }
        } finally {
            stopped = true;
            threadPool.destroyThread(Thread.currentThread());
        }
    }

    private void processTask() {
        CURRENT_THREAD_POOL.set(threadPool);
        try {
            if (task == null) task = threadPool.nextTask();
            if (task != null) {
                running = true;
                threadPool.beforeTask(task);
                task.execute();
            }
        } catch (InterruptedException e) {
            loop = false;
        } finally {
            CURRENT_THREAD_POOL.remove();
            if (task != null) lastCompletion = currentTimeMillis();
            running = false;
            task = null;
        }
    }

    private boolean shouldStop() {
        if (millisSince(lastCompletion) >= options.getKeepAliveTime().toMillis()) {
            return true;
        } else {
            return millisSince(startTime) >= options.getMaximumReuseTime().toMillis();
        }
    }

    @Override
    public String toString() {
        return new StringJoiner(", ", RunnableProxy.class.getSimpleName() + "[", "]")
                .add("threadPool=" + threadPool.getOptions().getNamePrefix())
                .add("task=" + task)
                .add("startTime=" + startTime)
                .add("lastCompletion=" + lastCompletion)
                .add("running=" + running)
                .add("loop=" + loop)
                .add("stopped=" + stopped)
                .toString();
    }
}
