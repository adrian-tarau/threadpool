package net.microfalx.threadpool;

import net.microfalx.objectpool.ObjectPool;
import net.microfalx.objectpool.ObjectPoolUtils;

import java.util.concurrent.ExecutorService;

/**
 * A thread pool abstraction.
 */
public interface ThreadPool extends ExecutorService {

    /**
     * Returns the options of this thread pool.
     *
     * @return a non-null instance
     */
    Options getOptions();

    /**
     * Options to control the thread pool
     */
    interface Options extends ObjectPool.Options<Thread> {

    }

    /**
     * A builder for a thread pool.
     */
    class Builder {

        private OptionsImpl options = new OptionsImpl();

        private Builder(ThreadFactory factory) {
            options.factory = ObjectPoolUtils.requireNonNull(factory);
        }

        ThreadPool build() {
            ThreadPoolImpl pool = new ThreadPoolImpl(options);
            return pool;
        }
    }
}
