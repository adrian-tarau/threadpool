package net.microfalx.threadpool;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static net.microfalx.threadpool.ThreadPoolUtils.requireNonNull;

class ThreadFactory {

    private final static Logger LOGGER = LoggerFactory.getLogger(ThreadFactory.class);

    private final String namePrefix;
    private final Map<ThreadImpl, Integer> indexesByThread = new ConcurrentHashMap<>();
    private final Map<Integer, ThreadImpl> threadByIndex = new ConcurrentHashMap<>();
    private final ThreadGroup threadGroup = new ThreadGroup("MicroFalx Group");

    final AtomicInteger created = new AtomicInteger();
    final AtomicInteger destroyed = new AtomicInteger();

    ThreadPoolImpl threadPool;

    ThreadFactory(ThreadPoolImpl threadPool, String namePrefix) {
        requireNonNull(threadPool);
        requireNonNull(namePrefix);

        this.threadPool = threadPool;
        this.namePrefix = namePrefix;
    }

    ThreadImpl createThread() {
        synchronized (indexesByThread) {
            int index = getNextAvailableIndex();
            String name = namePrefix + " " + index;
            LOGGER.debug("Create thread, name " + name);
            ThreadImpl thread = new ThreadImpl(threadPool, threadGroup, name, index);
            thread.setDaemon(threadPool.getOptions().isDaemon());
            thread.start();
            threadByIndex.put(index, thread);
            indexesByThread.put(thread, index);
            return thread;
        }
    }

    void destroyThread(ThreadImpl object) {
        synchronized (indexesByThread) {
            object.stopThread();
            Integer index = indexesByThread.get(object);
            indexesByThread.remove(object);
            if (index != null) threadByIndex.remove(index);
        }
    }

    int getSize() {
        synchronized (indexesByThread) {
            return indexesByThread.size();
        }
    }

    private void releaseThreads() {
        synchronized (indexesByThread) {
            for (Map.Entry<ThreadImpl, Integer> entry : indexesByThread.entrySet()) {
                if (entry.getKey().isDead()) {
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
                if (!threadByIndex.containsKey(index)) {
                    return index;
                }
                index++;
            }
        }
    }
}
