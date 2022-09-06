package net.microfalx.threadpool;

/**
 * A task which asks the pool to limit the number of scheduled tasks matching a key to {@code 1}.
 */
public interface SingletonTask extends Task {

    /**
     * Returns the singleton key.
     * <p>
     * By default it uses the worker class. A {@code NULL} value also uses the class of the task.
     *
     * @return a non-null instance
     */
    default Object getKey() {
        return getClass();
    }
}
