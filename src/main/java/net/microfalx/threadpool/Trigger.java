package net.microfalx.threadpool;

import java.time.Duration;
import java.time.Instant;

/**
 * An interface used to determine the next execution time.
 */
public interface Trigger {

    /**
     * Creates a trigger which schedules a task to be executed at a fixed rate.
     *
     * @param duration the rate
     * @return a non-null instance
     */
    static Trigger fixedRate(Duration duration) {
        return new PeriodicTrigger(duration, true);
    }

    /**
     * Creates a trigger which schedules a task at a fixed delay after the completion of the previous run.
     *
     * @param duration the delay
     * @return a non-null instance
     */
    static Trigger fixedDelay(Duration duration) {
        return new PeriodicTrigger(duration, false);
    }

    /**
     * Creates a trigger which schedules a task every minute at the requested second.
     *
     * @param value the second
     * @return a non-null instance
     */
    static Trigger atSecond(int value) {
        return new FixedTimeTrigger(FixedTimeTrigger.Strategy.AT_SECOND, value);
    }

    /**
     * Creates a trigger which schedules a task every hour at the requested minute.
     *
     * @param value the second
     * @return a non-null instance
     */
    static Trigger atMinute(int value) {
        return new FixedTimeTrigger(FixedTimeTrigger.Strategy.AT_MINUTE, value);
    }

    /**
     * Creates a trigger which schedules a task at the beginning of every hour.
     *
     * @return a non-null instance
     */
    static Trigger everyHour() {
        return new FixedTimeTrigger(FixedTimeTrigger.Strategy.EVERY_HOUR, 0);
    }

    /**
     * Creates a trigger which schedules a task at the beginning of every minute.
     *
     * @return a non-null instance
     */
    static Trigger everyMinute() {
        return new FixedTimeTrigger(FixedTimeTrigger.Strategy.EVERY_MINUTE, 0);
    }

    /**
     * Creates a trigger which schedules a task based on a CRON expression.
     *
     * @param expression the CRON expression
     * @return a non-null instance
     */
    static Trigger cron(String expression) {
        return new CronTrigger(expression);
    }

    /**
     * Invoked to get the next execution time.
     *
     * @return a non-null instance
     */
    Instant nextExecution();
}
