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
     * Returns the number of times the task was executed.
     * <p>
     * It is only meaningful for periodic tasks.
     *
     * @return a non-null instance
     */
    default Integer getExecutionCount() {
        return null;
    }

    /**
     * Returns the last execution time.
     *
     * @return the execution time, null if it was not executed before
     */
    LocalDateTime getLastExecutionTime();

    /**
     * Returns the next execution time.
     *
     * @return the next execution time, null if it is not scheduled for execution
     */
    default LocalDateTime getNextExecutionTime() {
        return null;
    }

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
