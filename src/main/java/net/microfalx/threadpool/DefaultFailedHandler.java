package net.microfalx.threadpool;

import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.service.Logger;

/**
 * The default handler (unless the thread pool provides one).
 */
public class DefaultFailedHandler implements ThreadPool.FailedHandler {

    static final Logger LOGGER = Logger.get(DefaultFailedHandler.class);

    @Override
    public void failed(ThreadPool pool, Thread thread, Throwable throwable, Object task) {
        LOGGER.atError().setCause(throwable).log("Unhandled exception in thread '{}' while executing task '{}'",
                thread.getName(), ClassUtils.getName(task));
    }
}
