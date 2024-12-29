package net.microfalx.threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

class ThreadFactory implements java.util.concurrent.ThreadFactory {

    private final static Logger LOGGER = LoggerFactory.getLogger(ThreadFactory.class);

    private final String namePrefix;
    private final Map<Thread, Integer> indexesByThread = new ConcurrentHashMap<>();
    private final Map<Thread, RunnableProxy> proxies = new ConcurrentHashMap<>();
    private final Map<Integer, Thread> threadByIndex = new ConcurrentHashMap<>();
    private final ThreadGroup threadGroup;
    private final AtomicInteger threadNumber = new AtomicInteger(1);

    final AtomicInteger created = new AtomicInteger();
    final AtomicInteger destroyed = new AtomicInteger();

    ThreadPoolImpl threadPool;
    ThreadPool.Options options;

    ThreadFactory(ThreadPoolImpl threadPool) {
        requireNonNull(threadPool);
        this.threadPool = threadPool;
        this.options = threadPool.getOptions();
        ThreadPool.Options options = threadPool.getOptions();
        this.namePrefix = options.getNamePrefix();
        if (options.getThreadGroup().isPresent()) {
            threadGroup = new ThreadGroup(options.getThreadGroup().get());
        } else {
            threadGroup = new ThreadGroup("Microfalx");
        }
    }

    @Override
    public Thread newThread(Runnable r) {
        return createThread(r, false);
    }

    Thread createThread(Runnable r, boolean register) {
        synchronized (indexesByThread) {
            if (register && isMaxedOut()) return null;
            int index = register ? getNextAvailableIndex() : threadNumber.getAndIncrement();
            String name = namePrefix + " " + index;
            LOGGER.debug("Create thread, name " + name);
            Thread thread = createThread(r, name, register);
            if (register) {
                threadByIndex.put(index, thread);
                indexesByThread.put(thread, index);
            }
            return thread;
        }
    }

    void destroyThread(Thread object) {
        synchronized (indexesByThread) {
            Integer index = indexesByThread.remove(object);
            if (index != null) threadByIndex.remove(index);
            RunnableProxy proxy = proxies.remove(object);
            if (proxy != null) proxy.stop();
            destroyed.incrementAndGet();
        }
    }

    int getSize() {
        synchronized (indexesByThread) {
            return indexesByThread.size();
        }
    }

    boolean hasIdle() {
        for (RunnableProxy proxy : proxies.values()) {
            if (!proxy.isRunning()) return true;
        }
        return false;
    }

    boolean isMaxedOut() {
        return getSize() >= options.getMaximumSize();
    }

    int getIndex(Thread thread) {
        Integer index = indexesByThread.get(thread);
        if (index == null) {
            throw new IllegalStateException("Thread '" + thread.getClass().getName() + "' is not part of the thread pool");
        }
        return index;
    }

    private Thread createThread(Runnable r, String name, boolean register) {
        TaskWrapper<?, ?> task = r != null ? new RunnableTaskWrapper(threadPool, r) : null;
        RunnableProxy proxy = new RunnableProxy(threadPool, task);
        java.util.concurrent.ThreadFactory threadFactory = threadPool.getOptions().getThreadFactory().orElse(null);
        if (threadFactory != null) {
            return threadFactory.newThread(proxy);
        } else {
            Thread thread;
            Thread.Builder threadBuilder;
            if (options.isVirtual()) {
                threadBuilder = Thread.ofVirtual();
            } else {
                threadBuilder = Thread.ofPlatform().group(threadGroup).daemon(options.isDaemon());
            }
            threadBuilder.name(name);
            thread = threadBuilder.unstarted(proxy);
            if (register) proxies.put(thread, proxy);
            thread.start();
            created.incrementAndGet();
            return thread;
        }
    }

    private void releaseThreads() {
        synchronized (indexesByThread) {
            for (Map.Entry<Thread, Integer> entry : indexesByThread.entrySet()) {
                if (!entry.getKey().isAlive()) {
                    indexesByThread.remove(entry.getKey());
                    threadByIndex.remove(entry.getValue());
                }
            }
        }
    }

    private int getNextAvailableIndex() {
        releaseThreads();
        int index = 1;
        synchronized (indexesByThread) {
            for (; ; ) {
                if (!threadByIndex.containsKey(index)) return index;
                index++;
            }
        }
    }
}
