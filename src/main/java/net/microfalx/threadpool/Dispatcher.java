package net.microfalx.threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import static java.lang.System.nanoTime;
import static java.util.Collections.unmodifiableCollection;
import static net.microfalx.lang.ArgumentUtils.requireNonNull;
import static net.microfalx.threadpool.ThreadPoolUtils.getThreadPoolId;

class Dispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(Dispatcher.class);

    private static final long STALE_THREAD_INTERVAL = 5_000_000_000L;
    private static final long MAX_WAIT_BETWEEN_ITERATIONS = 10;
    private static final AtomicInteger SCHEDULER_THREAD_COUNTER = new AtomicInteger();

    private static final Dispatcher instance = new Dispatcher();
    private volatile ThreadImpl thread;

    private final Collection<ThreadPoolImpl> threadPools = new CopyOnWriteArrayList<>();
    private final ReentrantLock lock = new ReentrantLock();
    private final Object awaitTasks = new Object();

    static Dispatcher getInstance() {
        return instance;
    }

    Dispatcher() {
        initialize();
    }

    void shutdown() {
        for (ThreadPoolImpl threadPool : threadPools) {
            if (!threadPool.isShutdown()) threadPool.shutdown();
        }
        threadPools.clear();
    }

    synchronized void register(ThreadPoolImpl threadPool) {
        if (threadPools.contains(threadPool)) {
            throw new IllegalArgumentException("A thread pool with identifier '" + threadPool.getId() + "' already exists");
        }
        threadPools.add(requireNonNull(threadPool));
    }

    synchronized ThreadPoolImpl getThreadPool(ThreadPool.Options options) {
        requireNonNull(options);
        String id = getThreadPoolId(options);
        for (ThreadPoolImpl threadPool : threadPools) {
            if (threadPool.getId().equals(id)) return threadPool;
        }
        return null;
    }

    void unregister(ThreadPoolImpl threadPool) {
        threadPools.remove(requireNonNull(threadPool));
    }

    Collection<ThreadPool> getThreadPools() {
        return unmodifiableCollection(threadPools);
    }

    void wakeUp(ThreadPoolImpl threadPool) {
        threadPool.handleTasks();
        synchronized (awaitTasks) {
            awaitTasks.notifyAll();
        }
        if (nanoTime() - thread.lastIteration > STALE_THREAD_INTERVAL) createThread();
    }

    private void initialize() {
        createThread();
    }

    private void createThread() {
        lock.lock();
        try {
            if (thread != null) thread.stopped = true;
            thread = new ThreadImpl();
            thread.start();
        } finally {
            lock.unlock();
        }
    }

    class ThreadImpl extends Thread {

        private volatile long lastIteration;
        private volatile boolean stopped;

        ThreadImpl() {
            setName("Microfalx Dispatcher " + SCHEDULER_THREAD_COUNTER.incrementAndGet());
            setDaemon(true);
        }

        private void handleTasks() {
            for (ThreadPoolImpl threadPool : threadPools) {
                try {
                    threadPool.handleTasks();
                } catch (Exception e) {
                    LOGGER.error("Failed to handle next task for " + threadPool, e);
                }
            }
        }

        private void loop() throws InterruptedException {
            while (!stopped) {
                lastIteration = nanoTime();
                handleTasks();
                synchronized (awaitTasks) {
                    awaitTasks.wait(MAX_WAIT_BETWEEN_ITERATIONS);
                }
            }
        }

        @Override
        public void run() {
            try {
                loop();
            } catch (InterruptedException e) {
                // do nothing, another thread will wake up
            } catch (Throwable e) {
                LOGGER.warn("Dispatcher thread stopped, create new thread", e);
            }
        }
    }
}
