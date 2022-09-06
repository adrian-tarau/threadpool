package net.microfalx.threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;

/**
 * The pooled thread implementation.
 */
final class ThreadImpl extends Thread {

    private final static Logger LOGGER = LoggerFactory.getLogger(ThreadImpl.class);

    private ThreadPoolImpl threadPool;
    private final int index;

    private volatile boolean stopping;
    private volatile boolean stopped;
    private volatile long lastPickedTask = System.currentTimeMillis();

    private final BlockingQueue<TaskWrapper> queue = new ArrayBlockingQueue<>(10);

    ThreadImpl(ThreadPoolImpl threadPool, ThreadGroup threadGroup, String name, int index) {
        super(threadGroup, name);
        this.threadPool = threadPool;
        this.index = index;
    }

    LocalDateTime getLastUsedTime() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(lastPickedTask), ZoneId.systemDefault());
    }

    int getIndex() {
        return index;
    }

    @Override
    public void run() {
        try {
            while (!stopping) {
                waitForTasks();
            }
        } catch (InterruptedException e) {
            // nothing to do, let be recycled
        } finally {
            stopped = true;
        }
    }

    void awakeThread(TaskWrapper task) {
        queue.add(task);
    }

    void stopThread() {
        stopping = true;
        awakeThread(new Dummy(threadPool));
    }

    boolean isStopped() {
        return stopped;
    }

    boolean isDead() {
        return isStopped() || isTerminated();
    }

    boolean isTerminated() {
        return getState() == Thread.State.TERMINATED;
    }

    private void waitForTasks() throws InterruptedException {
        TaskWrapper<?, ?> task = queue.take();
        if (shouldSkip(task)) return;
        touch();
        try {
            task.execute();
        } catch (Throwable e) {
            threadPool.failedTask(this, task, e);
        } finally {
            threadPool.completeTask(this, task);
        }
    }

    private boolean shouldSkip(TaskWrapper<?, ?> task) {
        return task instanceof Dummy;
    }

    private void touch() {
        lastPickedTask = System.currentTimeMillis();
    }

    static class Dummy extends TaskWrapper<Object, Object> {

        public Dummy(ThreadPoolImpl threadPool) {
            super(threadPool, dummy);
        }

        @Override
        Object doExecute() {
            throw new IllegalStateException("Should never be called");
        }
    }

    private final static Runnable dummy = () -> {
    };
}
