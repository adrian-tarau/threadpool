package net.microfalx.threadpool;

import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.Descriptable;
import net.microfalx.lang.Nameable;

import static net.microfalx.lang.StringUtils.beautifyCamelCase;

/**
 * A marker interface for an asynchronous task.
 */
public interface Task extends Nameable, Descriptable {

    @Override
    default String getName() {
        return beautifyCamelCase(ClassUtils.getSimpleName(getClass()));
    }

    @Override
    default String getDescription() {
        return null;
    }
}
