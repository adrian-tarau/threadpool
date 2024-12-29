package net.microfalx.threadpool;

/**
 * A task which asks the pool to limit the number of scheduled tasks matching a key to {@code 1}.
 */
public interface SingletonTask extends Task {

    /**
     * Returns whether the task is a singleton.
     *
     * @return {@code true} if singleton, {@code false} otherwise
     */
    default boolean isSingleton() {
        return true;
    }

    /**
     * Returns the singleton key.
     * <p>
     * By default, it uses the task class. A {@code NULL} value also uses the class of the task.
     *
     * @return a non-null instance
     */
    default Object getKey() {
        return getClass();
    }
}
