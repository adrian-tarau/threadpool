package net.microfalx.threadpool;

import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.ExceptionUtils;
import net.microfalx.lang.IdGenerator;
import net.microfalx.metrics.Metrics;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.FutureTask;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

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

    static final Metrics METRICS = Metrics.of("Thread Pool");

    static final IdGenerator ID_GENERATOR = IdGenerator.get("thread_pool");

    private static Field CARRIER_THREAD_FIELD;
    private static Class<?> RUNNABLE_ADAPTER_CLASS;
    private static Field RUNNABLE_ADAPTER_FIELD;
    private static Field FUTURE_TASK_FIELD;
    private static Method GET_ALL_THREADS;

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
     * Changes the default thread pool.
     * <p>
     * If the default pool needs to be changed, this call should be done early in the application startup,
     * before any service starts scheduling tasks.
     *
     * @param threadPool the new default thread pool
     */
    public static void setDefault(ThreadPool threadPool) {
        requireNonNull(threadPool);
        DEFAULT = threadPool;
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

    /**
     * Unwrap known {@link Runnable} or {@link java.util.concurrent.Callable} wrappers.
     *
     * @param task the task
     * @return the result
     */
    public static Object unwrapTask(Object task) {
        for (int i = 0; i < 5; i++) {
            Object prevTask = task;
            task = doUnwrapTask(task);
            if (task == prevTask) break;
        }
        return task;
    }

    /**
     * Returns all active threads.
     *
     * @return a non-null instance
     */
    public static Thread[] getThreads() {
        return METRICS.time("Extract Threads", ThreadPoolUtils::doGetThreads);
    }

    private static Thread[] doGetThreads() {
        Thread[] threads = null;
        if (GET_ALL_THREADS != null) {
            try {
                threads = (Thread[]) GET_ALL_THREADS.invoke(null);
            } catch (Exception e) {
                // ignore
            }
        }
        if (threads == null) {
            threads = Thread.getAllStackTraces().keySet().toArray(new Thread[0]);
        }
        return threads;
    }

    private static Object doUnwrapTask(Object task) {
        if (task == null) return null;
        if (RUNNABLE_ADAPTER_CLASS != null && ClassUtils.isSubClassOf(task, RUNNABLE_ADAPTER_CLASS)) {
            try {
                return RUNNABLE_ADAPTER_FIELD.get(task);
            } catch (IllegalAccessException e) {
                // ignore
            }
        } else if (FUTURE_TASK_FIELD != null && ClassUtils.isSubClassOf(task, FutureTask.class)) {
            try {
                return FUTURE_TASK_FIELD.get(task);
            } catch (IllegalAccessException e) {
                // ignore
            }
        }
        return task;
    }

    static {
        try {
            Class<?> virtualThreadClass = Class.forName("java.lang.VirtualThread");
            CARRIER_THREAD_FIELD = virtualThreadClass.getDeclaredField("carrierThread");
            CARRIER_THREAD_FIELD.setAccessible(true);
            RUNNABLE_ADAPTER_CLASS = Class.forName("java.util.concurrent.Executors$RunnableAdapter");
            RUNNABLE_ADAPTER_FIELD = RUNNABLE_ADAPTER_CLASS.getDeclaredField("task");
            RUNNABLE_ADAPTER_FIELD.setAccessible(true);
            FUTURE_TASK_FIELD = FutureTask.class.getDeclaredField("callable");
            FUTURE_TASK_FIELD.setAccessible(true);

            GET_ALL_THREADS = Thread.class.getDeclaredMethod("getAllThreads");
            GET_ALL_THREADS.setAccessible(true);
        } catch (Throwable e) {
            System.err.println("Failed to introspect internal thread structures: " + ExceptionUtils.getRootCauseMessage(e));
        }
    }
}
