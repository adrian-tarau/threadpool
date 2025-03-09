package net.microfalx.threadpool;

import java.time.Duration;

import static net.microfalx.lang.ArgumentUtils.requireNonNull;

/**
 * A trigger which uses an interval to calculate the next execution time.
 */
public abstract class IntervalAwareTrigger extends AbstractTrigger {

    private final Duration interval;

    protected IntervalAwareTrigger(Duration interval) {
        requireNonNull(interval);
        this.interval = interval;
    }

    public abstract ScheduledTask.Strategy getStrategy();

    public final Duration getInterval() {
        return interval;
    }
}
