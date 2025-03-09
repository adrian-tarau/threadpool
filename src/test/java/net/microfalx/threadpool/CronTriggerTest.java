package net.microfalx.threadpool;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CronTriggerTest extends AbstractTriggerTestCase {

    @Test
    void everyMinute() {
        CronTrigger trigger = new CronTrigger("0 * * * * ? *");
        updateTrigger(trigger);
        org.assertj.core.api.Assertions.assertThat(trigger.nextExecution().toEpochMilli() - scheduledTime)
                .isBetween(0L, 60_000L);
    }

    @Test
    void getInterval() {
        CronTrigger trigger = new CronTrigger("* * * * * ? *");
        assertEquals(Duration.ofSeconds(1), trigger.getInterval());
        trigger = new CronTrigger("0 * * * * ? *");
        assertEquals(Duration.ofSeconds(60), trigger.getInterval());
    }

}