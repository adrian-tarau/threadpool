package net.microfalx.threadpool;

/**
 * A factory used to create thread instances.
 */
public interface ThreadFactory {

    /**
     * Creates a thread instance to be managed by the pool.
     *
     * @return a newly created thread.
     */
    Thread makeThread();

    /**
     * Destroys an instance no longer needed by the pool.
     * <p>
     * Most thread factories do not need to destroy the thread. The thread object will be collectd and all
     * resources will be released.
     *
     * @param thread the thread to be destroyed
     */
    default void destroyThread(Thread thread) {

    }
}
