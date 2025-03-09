package net.microfalx.threadpool;

import java.util.concurrent.Callable;

/**
 * Base class for all tasks based on {@link Callable}.
 *
 * @param <V> the result type
 */
public abstract class AbstractCallable<V> extends AbstractTask implements Callable<V> {
}
