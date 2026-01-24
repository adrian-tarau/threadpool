package net.microfalx.threadpool;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import static java.time.Duration.ofHours;
import static java.time.Duration.ofMinutes;
import static org.junit.jupiter.api.Assertions.assertNull;

class CallableTaskWrapperTest {

    private final Task task = new Task();
    private ThreadPoolImpl threadPool;

    @BeforeEach
    void setup() {
        threadPool = (ThreadPoolImpl) ThreadPool.get();
    }

    @Test
    void cronExpression15m() {
        Trigger cron = Trigger.cron("0 0/15 * ? * * *");
        CallableTaskWrapper<Integer> taskWrapper = new CallableTaskWrapper<>(threadPool, task, 0, TimeUnit.MILLISECONDS).trigger(cron);
        taskWrapper.updateDelay();
        assertNull(taskWrapper.getLastExecutionTime());
        Assertions.assertThat(taskWrapper.getNextExecutionTime())
                .isCloseTo(LocalDateTime.now(), Assertions.within(ofMinutes(15)));
    }

    @Test
    void cronExpression6h() {
        Trigger cron = Trigger.cron("0 0 0/6 ? * * *");
        CallableTaskWrapper<Integer> taskWrapper = new CallableTaskWrapper<>(threadPool, task, 0, TimeUnit.MILLISECONDS).trigger(cron);
        taskWrapper.updateDelay();
        assertNull(taskWrapper.getLastExecutionTime());
        Assertions.assertThat(taskWrapper.getNextExecutionTime())
                .isCloseTo(LocalDateTime.now(), Assertions.within(ofHours(6)));
    }

    @Test
    void cronExpression24h() {
        Trigger cron = Trigger.cron("0 0 0 * * ?");
        CallableTaskWrapper<Integer> taskWrapper = new CallableTaskWrapper<>(threadPool, task, 0, TimeUnit.MILLISECONDS).trigger(cron);
        taskWrapper.updateDelay();
        assertNull(taskWrapper.getLastExecutionTime());
        Assertions.assertThat(taskWrapper.getNextExecutionTime())
                .isCloseTo(LocalDateTime.now(), Assertions.within(ofHours(24)));
    }

    private static class Task implements Callable<Integer> {

        @Override
        public Integer call() throws Exception {
            return 0;
        }
    }

}