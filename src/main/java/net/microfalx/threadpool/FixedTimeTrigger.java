package net.microfalx.threadpool;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static net.microfalx.lang.ArgumentUtils.requireBounded;

/**
 * A trigger used to schedule tasks at a fixed absolute time, similar to a cron expression: at second 0 of every minute,
 * at second 0 every X minutes, etc.
 */
public class FixedTimeTrigger extends IntervalAwareTrigger {

    private final Strategy strategy;
    private final int value;

    public FixedTimeTrigger(Strategy strategy, int value) {
        super(getInterval(strategy));
        this.strategy = strategy;
        if (strategy.requiresValue) requireBounded(value, 0, 59);
        this.value = value;
    }

    @Override
    public Instant nextExecution() {
        Instant scheduled = getLastScheduledExecution();
        if (scheduled == null) scheduled = Instant.now();
        ZonedDateTime dateTime = scheduled.atZone(ZoneId.systemDefault());
        if (strategy == Strategy.AT_SECOND) {
            return dateTime.plusMinutes(1).withSecond(value).toInstant();
        } else if (strategy == Strategy.AT_MINUTE) {
            int hourAdjustment = 0;
            if (dateTime.getMinute() > ZonedDateTime.now().getMinute()) {
                hourAdjustment = 1;
            }
            return dateTime.plusHours(hourAdjustment).withMinute(value).withSecond(0).toInstant();
        } else if (strategy == Strategy.EVERY_MINUTE) {
            return dateTime.plusMinutes(1).withSecond(0).toInstant();
        } else if (strategy == Strategy.EVERY_HOUR) {
            return dateTime.plusHours(1).withMinute(0).withSecond(0).toInstant();
        } else {
            throw new IllegalStateException("Unhandled strategy: " + strategy);
        }
    }

    @Override
    public ScheduledTask.Strategy getStrategy() {
        return ScheduledTask.Strategy.FIXED_RATE;
    }

    private static Duration getInterval(Strategy strategy) {
        switch (strategy) {
            case AT_SECOND, EVERY_MINUTE -> {
                return Duration.ofSeconds(60);
            }
            default -> {
                return Duration.ofMinutes(60);
            }
        }
    }

    enum Strategy {

        AT_SECOND(true),
        AT_MINUTE(true),
        EVERY_MINUTE(false),
        EVERY_HOUR(false);

        final boolean requiresValue;

        Strategy(boolean requiresValue) {
            this.requiresValue = requiresValue;
        }
    }
}
