package net.microfalx.threadpool;

import java.time.Duration;

/**
 * A task which is scheduled periodically.
 * <p>
 * The thread pool will use the strategy, initial delay and the interval provided by the task.
 */
public interface ScheduledTask extends Task {

    /**
     * Returns the scheduling strategy.
     *
     * @return a non-null enum
     */
    default Strategy getStrategy() {
        return Strategy.FIXED_RATE;
    }

    /**
     * Returns the initial delay.
     *
     * @return the delay
     */
    default Duration getInitialDelay() {
        return Duration.ZERO;
    }

    /**
     * Returns the scheduling interval.
     * <p>
     * Based on {@link #getStrategy()} the next execution time is fixed or relative to the last execution.
     *
     * @return the interval
     */
    Duration getInterval();

    /**
     * Holds the strategy of the task.
     */
    enum Strategy {

        /**
         * The task is scheduled at fixed intervals, regardless whether the previous task was completed.
         */
        FIXED_RATE,

        /**
         * The task is scheduled at an interval after the previous run is completed.
         */
        FIXED_DELAY,

        /**
         * The task is scheduled at an interval calculated by an expression or algorithm.
         */
        EXPRESSION
    }
}
