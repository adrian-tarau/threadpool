package net.microfalx.threadpool;

import net.microfalx.lang.ClassUtils;
import net.microfalx.lang.Descriptable;
import net.microfalx.lang.Nameable;
import net.microfalx.lang.StringUtils;

/**
 * Base class for all tasks.
 */
public abstract class AbstractTask implements Nameable, Descriptable {

    private volatile String name;
    private volatile String description;

    @Override
    public final String getName() {
        return name;
    }

    protected final void setName(String name) {
        this.name = StringUtils.isEmpty(name) ? ClassUtils.getSimpleName(this) : name;
    }

    @Override
    public final String getDescription() {
        return description;
    }

    protected final void setDescription(String description) {
        this.description = description;
    }
}
