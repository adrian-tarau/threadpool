package net.microfalx.threadpool;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CronTriggerTest extends AbstractTriggerTestCase {

    @Test
    void everyMinute() {
        CronTrigger trigger = new CronTrigger("0 * * * * ? *");
        org.assertj.core.api.Assertions.assertThat(trigger.nextExecution().toEpochMilli() - scheduledTime)
                .isBetween(0L, 60_000L);
    }

    @Test
    void every15Minute() {
        CronTrigger trigger = new CronTrigger("0 0/15 * ? * * *");
        updateTrigger(trigger);
        org.assertj.core.api.Assertions.assertThat(trigger.nextExecution().toEpochMilli() - scheduledTime)
                .isBetween(0L, 15 * 60_000L);
    }

    @Test
    void every6Hours() {
        CronTrigger trigger = new CronTrigger("0 0 0/6 ? * * *");
        org.assertj.core.api.Assertions.assertThat(trigger.nextExecution().toEpochMilli() - scheduledTime)
                .isBetween(0L, 6 * 60 * 60_000L);
    }

    @Test
    void getInterval() {
        CronTrigger trigger = new CronTrigger("* * * * * ? *");
        assertEquals(Duration.ofSeconds(1), trigger.getInterval());
        trigger = new CronTrigger("0 * * * * ? *");
        assertEquals(Duration.ofSeconds(60), trigger.getInterval());
    }

}