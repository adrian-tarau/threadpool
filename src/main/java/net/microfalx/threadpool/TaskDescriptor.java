package net.microfalx.threadpool;

import net.microfalx.lang.Identifiable;

import java.time.Duration;
import java.time.LocalDateTime;

/**
 * A descriptor for a task.
 */
public interface TaskDescriptor extends Task, Identifiable<Long> {

    /**
     * Returns the thread pool where the task is registered.
     *
     * @return a non-null instance
     */
    ThreadPool getThreadPool();

    /**
     * Returns the thread which currently executes the task.
     *
     * @return the thread, null if the task is not in execution
     */
    Thread getThread();

    /**
     * Returns the class of the task.
     *
     * @return a non-null instance
     */
    Class<?> getTaskClass();

    /**
     * Returns the timestamp when the task was started.
     *
     * @return the timestamp, null if it was not started
     */
    LocalDateTime getStartedAt();

    /**
     * Returns the amount of time spent executing this task.
     *
     * @return the duration, null if the task is still running (or was not executed)
     */
    Duration getDuration();

    /**
     * Returns whether this task is periodic.
     *
     * @return {@code true} if this task is periodic, {@code false} otherwise
     */
    boolean isPeriodic();

    /**
     * Returns the exception raised by the task.
     *
     * @return the exception if it failes, null otherwise
     */
    Throwable getThrowable();
}
