package net.microfalx.threadpool;

import net.microfalx.lang.Hashing;
import net.microfalx.lang.Identifiable;

/**
 * A task which can be uniquely identified to be rescheduled.
 * <p>
 * By default, the identifier is a hash based on the class name. When a task has multiple instances
 * of the same class, a unique identifier should be used to distinguish between tasks.
 */
public interface IdentifiableTask extends Task, Identifiable<String> {

    @Override
    default String getId() {
        return Hashing.get(getClass().getName());
    }
}
