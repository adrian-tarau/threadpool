package net.microfalx.threadpool;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.TimeUnit;

/**
 * A trigger use to schedule tasks with a fixed rate or delay.
 */
class PeriodicTrigger extends IntervalAwareTrigger {

    private final boolean fixedRate;

    PeriodicTrigger(long period, TimeUnit unit, boolean fixedRate) {
        this(Duration.ofNanos(TimeUnit.NANOSECONDS.convert(period, unit)), fixedRate);
    }

    PeriodicTrigger(Duration period, boolean fixedRate) {
        super(period);
        this.fixedRate = fixedRate;
    }

    public boolean isFixedRate() {
        return fixedRate;
    }

    @Override
    public Instant nextExecution() {
        if (fixedRate) {
            Instant scheduled = getLastScheduledExecution();
            if (scheduled == null) scheduled = Instant.now();
            return scheduled.plus(getInterval());
        } else {
            Instant completion = getLastCompletion();
            if (completion == null) completion = Instant.now();
            return completion.plus(getInterval());
        }
    }

    @Override
    public ScheduledTask.Strategy getStrategy() {
        return fixedRate ? ScheduledTask.Strategy.FIXED_RATE : ScheduledTask.Strategy.FIXED_DELAY;
    }
}
