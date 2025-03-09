package net.microfalx.threadpool;

import java.time.Duration;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * An extension of the {@link java.util.concurrent.ScheduledExecutorService}, which more ways of scheduling tasks.
 */
public interface ScheduledExecutorService extends java.util.concurrent.ScheduledExecutorService {

    /**
     * Submits a periodic action using a trigger to calculate the next execution time.
     *
     * @param task    the task to execute
     * @param trigger the trigger
     * @return a non-null instance
     * @see #scheduleAtFixedRate(Runnable, long, long, TimeUnit)
     */
    ScheduledFuture<?> schedule(Runnable task, Trigger trigger);

    /**
     * Submits a periodic action that becomes enabled first after the
     * given initial delay, and subsequently with the given period.
     * <p>
     * The initial delay is randomly picked based on the period, but not longer than 60 seconds.
     *
     * @param task   the task to execute
     * @param period the period between successive executions
     * @return a non-null instance
     * @see #scheduleAtFixedRate(Runnable, long, long, TimeUnit)
     */
    ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration period);

    /**
     * Submits a periodic action that becomes enabled first after the
     * given initial delay, and subsequently with the given period.
     *
     * @param task         the task to execute
     * @param initialDelay the time to delay first execution
     * @param period       the period between successive executions
     * @return a non-null instance
     * @see #scheduleAtFixedRate(Runnable, long, long, TimeUnit)
     */
    ScheduledFuture<?> scheduleAtFixedRate(Runnable task, Duration initialDelay, Duration period);

    /**
     * Submits a periodic action that becomes enabled first after the
     * given initial delay, and subsequently with the given delay
     * between the termination of one execution and the commencement of
     * the next.
     * <p>
     * The initial delay is randomly picked based on the period, but not longer than 60 seconds.
     *
     * @param task  the task to execute
     * @param delay the delay between the termination of one execution and the commencement of the next
     * @return a non-null instance
     * @see #scheduleWithFixedDelay(Runnable, long, long, TimeUnit)
     */
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration delay);

    /**
     * Submits a periodic action that becomes enabled first after the
     * given initial delay, and subsequently with the given delay
     * between the termination of one execution and the commencement of
     * the next.
     *
     * @param task         the task to execute
     * @param initialDelay the time to delay first execution
     * @param delay        the delay between the termination of one execution and the commencement of the next
     * @return a non-null instance
     * @see #scheduleWithFixedDelay(Runnable, long, long, TimeUnit)
     */
    ScheduledFuture<?> scheduleWithFixedDelay(Runnable task, Duration initialDelay, Duration delay);
}
