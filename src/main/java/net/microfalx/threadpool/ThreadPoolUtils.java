package net.microfalx.threadpool;

import net.microfalx.lang.ExceptionUtils;

import java.lang.reflect.Field;

/**
 * Various utilities around thread pools.
 */
public class ThreadPoolUtils {

    /**
     * The maximum allowed numbers of threads in any pool.
     */
    static final int MAXIMUM_POOL_SIZE = 1_000;

    /**
     * The maximum allowed tasks in the queue.
     */
    static final int MAXIMUM_QUEUE_SIZE = 10_000;

    private static Field CARRIER_THREAD_FIELD;

    private static volatile ThreadPool DEFAULT;

    static ThreadPool getDefault() {
        if (DEFAULT == null) {
            synchronized (ThreadPoolUtils.class) {
                if (DEFAULT == null) {
                    DEFAULT = ThreadPool.create();
                }
            }
        }
        return DEFAULT;
    }

    /**
     * Returns the carrier thread for a virtual thread.
     *
     * @param thread the thread.
     * @return the carrier if virtual and carrier thread is attached, null otherwise
     */
    public static Thread getCarrier(Thread thread) {
        if (thread == null || CARRIER_THREAD_FIELD == null) return null;
        try {
            return (Thread) CARRIER_THREAD_FIELD.get(thread);
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    static {
        try {
            Class<?> virtualThreadClass = Class.forName("java.lang.VirtualThread");
            CARRIER_THREAD_FIELD = virtualThreadClass.getDeclaredField("carrierThread");
            CARRIER_THREAD_FIELD.setAccessible(true);
        } catch (Throwable e) {
            System.err.println("Failed to introspect internal thread structures: " + ExceptionUtils.getRootCauseMessage(e));
        }
    }
}
