package net.microfalx.threadpool;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static java.time.Duration.ofSeconds;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class ThreadPoolImplTest {

    private static final int FAST_TASK = 10;
    private static final int AVERAGE_TASK = 100;
    private static final int SLOW_TASK = 500;

    private ThreadPool pool;
    private ThreadPool.Metrics metrics;

    @BeforeEach
    void setup() {
        pool = ThreadPool.builder("Test").build();
        metrics = pool.getMetrics();
    }

    @AfterEach
    void after() {
        Dispatcher.getInstance().shutdown();
    }

    @Test
    void shutdown() {
        pool.shutdown();
        assertTrue(pool.isShutdown());
        assertEquals(0, Dispatcher.getInstance().getThreadPools().size());
    }

    @Test
    void shutdownNow() {
        List<Runnable> pending = pool.shutdownNow();
        assertTrue(pool.isShutdown());
        assertEquals(0, pending.size());
        assertEquals(0, Dispatcher.getInstance().getThreadPools().size());
    }

    @Test
    void awaitTermination() throws Exception {
        pool.shutdown();
        pool.awaitTermination(2, TimeUnit.SECONDS);
    }

    @Test
    void suspendAndResume() throws InterruptedException {
        assertFalse(pool.isPaused());
        fireSingleRun(FAST_TASK, 5);
        await().until(() -> pool.isIdle());
        assertEquals(5, metrics.getExecutedTaskCount());

        pool.suspend();
        assertTrue(pool.isPaused());
        fireSingleRun(FAST_TASK, 5);
        Thread.sleep(1000);
        assertEquals(5, metrics.getExecutedTaskCount());

        pool.resume();
        await().atMost(ofSeconds(2))
                .untilAsserted(() -> assertEquals(10, metrics.getExecutedTaskCount()));
    }

    @Test
    void submitRunnable() throws ExecutionException, InterruptedException {
        pool.submit(new RunnableTask(FAST_TASK));
        await().atMost(ofSeconds(2))
                .untilAsserted(() -> assertEquals(1, metrics.getExecutedTaskCount()));

        Future<String> future = pool.submit(new RunnableTask(FAST_TASK), "test");
        await().atMost(ofSeconds(2))
                .untilAsserted(() -> assertEquals(2, metrics.getExecutedTaskCount()));
        assertEquals("test", future.get());
    }

    @Test
    void submitCallable() throws ExecutionException, InterruptedException {
        Future<Integer> future = pool.submit(new CallableTask(1, FAST_TASK));
        await().atMost(ofSeconds(2))
                .untilAsserted(() -> assertEquals(1, metrics.getExecutedTaskCount()));
        assertEquals(1, future.get());
    }

    @Test
    void execute() {
        assertFalse(pool.isPaused());
        fireSingleRun(FAST_TASK, 5);
        await().until(() -> pool.isIdle());
        assertEquals(5, metrics.getExecutedTaskCount());
    }

    @Test
    void executeDelayed() {
        pool.execute(new DelayedTask(AVERAGE_TASK));
        assertEquals(0, metrics.getExecutedTaskCount());
        await().until(() -> metrics.getExecutedTaskCount() > 0);
        assertEquals(1, metrics.getExecutedTaskCount());
    }

    @Test
    void executeScheduledFast() {
        pool.execute(new ScheduledTask(FAST_TASK));
        assertEquals(0, metrics.getExecutedTaskCount());
        await().untilAsserted(() -> Assertions.assertThat(metrics.getExecutedTaskCount()).isGreaterThan(300));
    }

    @Test
    void executeScheduledAverage() {
        pool.execute(new ScheduledTask(AVERAGE_TASK));
        assertEquals(0, metrics.getExecutedTaskCount());
        await().untilAsserted(() -> Assertions.assertThat(metrics.getExecutedTaskCount()).isGreaterThan(70));
    }

    @Test
    void executeScheduledSlow() {
        pool.execute(new ScheduledTask(SLOW_TASK));
        assertEquals(0, metrics.getExecutedTaskCount());
        await().untilAsserted(() -> Assertions.assertThat(metrics.getExecutedTaskCount()).isGreaterThan(15));
    }

    private void fireSingleRun(int executionTime, int count) {
        for (int i = 0; i < count; i++) {
            pool.execute(new RunnableTask(executionTime));
        }
    }

    static class DelayedTask implements net.microfalx.threadpool.DelayedTask, Runnable {

        private final int executionTime;

        public DelayedTask(int executionTime) {
            this.executionTime = executionTime;
        }

        @Override
        public Duration getDelay() {
            return Duration.ofMillis(executionTime / 3);
        }

        @Override
        public void run() {
            try {
                Thread.sleep(executionTime);
            } catch (InterruptedException e) {
                // just stop
            }
        }
    }

    static class ScheduledTask implements net.microfalx.threadpool.ScheduledTask, Runnable {

        private final int executionTime;

        public ScheduledTask(int executionTime) {
            this.executionTime = executionTime;
        }

        @Override
        public Duration getInitialDelay() {
            return Duration.ofMillis(executionTime / 3);
        }

        @Override
        public Duration getInterval() {
            return Duration.ofMillis(Math.max(1, executionTime / 3));
        }

        @Override
        public void run() {
            try {
                Thread.sleep(executionTime);
            } catch (InterruptedException e) {
                // just stop
            }
        }
    }

    static class RunnableTask implements Runnable {

        private final int executionTime;

        RunnableTask(int executionTime) {
            this.executionTime = executionTime;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(executionTime);
            } catch (InterruptedException e) {
                // just stop
            }
        }
    }

    static class CallableTask implements Callable<Integer> {

        private Integer value;
        private int executionTime;

        CallableTask(Integer value, int executionTime) {
            this.value = value;
            this.executionTime = executionTime;
        }

        @Override
        public Integer call() throws Exception {
            try {
                Thread.sleep(executionTime);
            } catch (InterruptedException e) {
                // just stop
            }
            return value;
        }
    }
}