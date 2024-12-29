package net.microfalx.threadpool;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static java.time.Duration.ofSeconds;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

class ThreadPoolImplTest {

    private static final int FAST_TASK = 10;
    private static final int AVERAGE_TASK = 100;
    private static final int SLOW_TASK = 500;

    private ThreadPool pool;
    private ThreadPool.Metrics metrics;

    private static CountDownLatch countDown;

    @BeforeEach
    void setup() {
        createPool(false);
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
        assertFalse(pool.isPaused());
        Thread.sleep(1000);
        await().atMost(ofSeconds(2))
                .untilAsserted(() -> assertEquals(10, metrics.getExecutedTaskCount()));
    }

    @Test
    void virtualThreads() {
        createPool(true);
        fireSingleRun(FAST_TASK, 5);
        await().until(() -> pool.isIdle());
        assertEquals(5, metrics.getExecutedTaskCount());
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
        assertEquals(10, future.get());
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
        await().until(() -> metrics.getExecutedTaskCount() >= 1);
        assertEquals(1, metrics.getExecutedTaskCount());
    }

    @Test
    void scheduleFixedDelay() {
        pool.execute(new ScheduledTask(AVERAGE_TASK, net.microfalx.threadpool.ScheduledTask.Strategy.FIXED_DELAY));
        pool.scheduleWithFixedDelay(new RunnableTask(AVERAGE_TASK), AVERAGE_TASK, AVERAGE_TASK, TimeUnit.MILLISECONDS);
        assertEquals(0, metrics.getExecutedTaskCount());
        await().until(() -> metrics.getExecutedTaskCount() >= 2);
        Assertions.assertThat(metrics.getExecutedTaskCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void scheduleFixedRate() {
        pool.execute(new ScheduledTask(AVERAGE_TASK, net.microfalx.threadpool.ScheduledTask.Strategy.FIXED_RATE));
        pool.scheduleAtFixedRate(new RunnableTask(AVERAGE_TASK), AVERAGE_TASK, AVERAGE_TASK, TimeUnit.MILLISECONDS);
        assertEquals(0, metrics.getExecutedTaskCount());
        await().until(() -> metrics.getExecutedTaskCount() >= 2);
        Assertions.assertThat(metrics.getExecutedTaskCount()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void scheduleDelay() {
        pool.schedule(new RunnableTask(AVERAGE_TASK), AVERAGE_TASK, TimeUnit.MILLISECONDS);
        assertEquals(0, metrics.getExecutedTaskCount());
        await().until(() -> metrics.getExecutedTaskCount() >= 1);
        assertEquals(1, metrics.getExecutedTaskCount());
    }

    @Test
    void scheduleCallable() throws ExecutionException, InterruptedException, TimeoutException {
        ScheduledFuture<Integer> future = pool.schedule(new CallableTask(AVERAGE_TASK, 10), AVERAGE_TASK, TimeUnit.MILLISECONDS);
        assertEquals(0, metrics.getExecutedTaskCount());
        await().until(() -> metrics.getExecutedTaskCount() >= 1);
        assertEquals(1, metrics.getExecutedTaskCount());
        assertEquals(10, future.get(2, TimeUnit.SECONDS));
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

    @Test
    void executeWithCallerRunsPolicy() throws InterruptedException {
        int count = pool.getOptions().getMaximumSize() + pool.getOptions().getQueueSize();
        countDown = new CountDownLatch(count + 10);
        fireSingleRun(50, count);
        fireSingleRun(50, 10);
        assertTrue(countDown.await(4, TimeUnit.SECONDS));
    }

    @Test
    void executeWithCustomPolicy() throws InterruptedException {
        AtomicInteger failureCounter = new AtomicInteger();
        pool = ThreadPool.builder("Test")
                .rejectedHandler((runnable, pool1) -> {
                    failureCounter.incrementAndGet();
                    runnable.run();
                })
                .build();
        int count = pool.getOptions().getMaximumSize() + pool.getOptions().getQueueSize();
        countDown = new CountDownLatch(count + 10);
        fireSingleRun(50, count);
        fireSingleRun(50, 10);
        assertTrue(countDown.await(4, TimeUnit.SECONDS));
        Assertions.assertThat(failureCounter.get()).isGreaterThan(1);
    }

    @Test
    void executeWithFailure() throws InterruptedException {
        countDown = new CountDownLatch(1);
        pool.execute(new RunnableTask(50, new IOException("Failure")));
        assertTrue(countDown.await(4, TimeUnit.SECONDS));
    }

    @Test
    void executeWithFailureCallback() throws InterruptedException {
        AtomicInteger failureCounter = new AtomicInteger();
        pool = ThreadPool.builder("Test")
                .failureHandler((runnable, pool1, throwable) -> failureCounter.incrementAndGet())
                .build();
        countDown = new CountDownLatch(1);
        pool.execute(new RunnableTask(50, new IOException("Failure")));
        assertTrue(countDown.await(4, TimeUnit.SECONDS));
        assertEquals(1, failureCounter.get());
    }

    private void createPool(boolean virtual) {
        pool = ThreadPool.builder(virtual ? "Virtual" : "Test").virtual(virtual).build();
        metrics = pool.getMetrics();
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
        private final Strategy strategy;

        public ScheduledTask(int executionTime) {
            this(executionTime, Strategy.FIXED_RATE);
        }

        public ScheduledTask(int executionTime, Strategy strategy) {
            this.executionTime = executionTime;
            this.strategy = strategy;
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
        public Strategy getStrategy() {
            return strategy;
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
        private Throwable throwable;

        RunnableTask(int executionTime) {
            this.executionTime = executionTime;
        }

        public RunnableTask(int executionTime, Throwable throwable) {
            this.executionTime = executionTime;
            this.throwable = throwable;
        }

        @Override
        public void run() {
            try {
                Thread.sleep(executionTime);
            } catch (InterruptedException e) {
                // just stop
            } finally {
                if (countDown != null) countDown.countDown();
            }
            if (throwable != null) throw new IllegalStateException(throwable);
        }
    }

    static class CallableTask implements Callable<Integer> {

        private final int executionTime;
        private final int value;
        private Throwable throwable;

        CallableTask(int executionTime, int value) {
            this.executionTime = executionTime;
            this.value = value;
        }

        public CallableTask(int executionTime, int value, Throwable throwable) {
            this.executionTime = executionTime;
            this.value = value;
            this.throwable = throwable;
        }

        @Override
        public Integer call() throws Exception {
            try {
                Thread.sleep(executionTime);
            } catch (InterruptedException e) {
                // just stop
            } finally {
                if (countDown != null) countDown.countDown();
            }
            if (throwable != null) throw new IllegalStateException(throwable);
            return value;
        }
    }
}