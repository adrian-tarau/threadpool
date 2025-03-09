package net.microfalx.threadpool;

import org.junit.jupiter.api.BeforeEach;

import java.time.ZonedDateTime;

public abstract class AbstractTriggerTestCase {

    long scheduledTime;
    long executionTime;
    long completionTime;

    final ZonedDateTime now = ZonedDateTime.now();

    @BeforeEach
    void setup() {
        scheduledTime = System.currentTimeMillis();
        executionTime = scheduledTime + 1000;
        completionTime = executionTime + 5000;
    }

    protected final void updateTrigger(AbstractTrigger trigger) {
        trigger.update(scheduledTime, executionTime, completionTime);
    }
}
