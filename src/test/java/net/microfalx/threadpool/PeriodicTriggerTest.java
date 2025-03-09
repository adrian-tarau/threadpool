package net.microfalx.threadpool;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PeriodicTriggerTest extends AbstractTriggerTestCase {

    @Test
    void fixedRateWithTimeUnit() {
        PeriodicTrigger trigger = new PeriodicTrigger(30, TimeUnit.SECONDS, true);
        assertEquals(Duration.ofSeconds(30), trigger.getInterval());
        assertEquals(ScheduledTask.Strategy.FIXED_RATE, trigger.getStrategy());
    }

    @Test
    void fixedRate() {
        PeriodicTrigger trigger = new PeriodicTrigger(Duration.ofSeconds(30), true);
        assertEquals(Duration.ofSeconds(30), trigger.getInterval());
        assertEquals(ScheduledTask.Strategy.FIXED_RATE, trigger.getStrategy());
        assertEquals(30, (trigger.nextExecution().toEpochMilli() - scheduledTime) / 1000);
        updateTrigger(trigger);
        assertEquals(30_000, trigger.nextExecution().toEpochMilli() - scheduledTime);
    }

    @Test
    void fixedDelay() {
        PeriodicTrigger trigger = new PeriodicTrigger(Duration.ofSeconds(20), false);
        assertEquals(Duration.ofSeconds(20), trigger.getInterval());
        assertEquals(ScheduledTask.Strategy.FIXED_DELAY, trigger.getStrategy());
        assertEquals(20, (trigger.nextExecution().toEpochMilli() - scheduledTime) / 1000);
        updateTrigger(trigger);
        assertEquals(20_000, trigger.nextExecution().toEpochMilli() - completionTime);
    }



}