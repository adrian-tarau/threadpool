package net.microfalx.threadpool;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FixedTimeTriggerTest extends AbstractTriggerTestCase {

    @Test
    void atSecond() {
        FixedTimeTrigger trigger = new FixedTimeTrigger(FixedTimeTrigger.Strategy.AT_SECOND, 20);
        updateTrigger(trigger);
        assertEquals(Duration.ofSeconds(60), trigger.getInterval());
        ZonedDateTime nextExecution = trigger.nextExecution().atZone(ZoneId.systemDefault());
        assertEquals(20, nextExecution.getSecond());
        assertEquals(now.getMinute() + 1, nextExecution.getMinute());
    }

    @Test
    void atMinute() {
        FixedTimeTrigger trigger = new FixedTimeTrigger(FixedTimeTrigger.Strategy.AT_MINUTE, 20);
        assertEquals(Duration.ofMinutes(60), trigger.getInterval());
        ZonedDateTime nextExecution = trigger.nextExecution().atZone(ZoneId.systemDefault());
        assertEquals(0, nextExecution.getSecond());
        assertEquals(20, nextExecution.getMinute());
        assertEquals(now.getHour(), nextExecution.getHour());
    }

    @Test
    void everyMinute() {
        FixedTimeTrigger trigger = new FixedTimeTrigger(FixedTimeTrigger.Strategy.EVERY_MINUTE, 20);
        assertEquals(Duration.ofSeconds(60), trigger.getInterval());
        ZonedDateTime nextExecution = trigger.nextExecution().atZone(ZoneId.systemDefault());
        assertEquals(0, nextExecution.getSecond());
        assertEquals(now.getMinute() + 1, nextExecution.getMinute());
        assertEquals(now.getHour(), nextExecution.getHour());
    }

    @Test
    void everyHour() {
        FixedTimeTrigger trigger = new FixedTimeTrigger(FixedTimeTrigger.Strategy.EVERY_HOUR, 20);
        assertEquals(Duration.ofHours(1), trigger.getInterval());
        ZonedDateTime nextExecution = trigger.nextExecution().atZone(ZoneId.systemDefault());
        assertEquals(0, nextExecution.getSecond());
        assertEquals(0, nextExecution.getMinute());
        assertEquals(now.getHour() + 1, nextExecution.getHour());
    }

}