package net.microfalx.threadpool;

import org.awaitility.Awaitility;
import org.awaitility.core.ConditionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.*;

class ThreadPoolImplTest {

    private static final int FAST_TASK = 10;
    private static final int AVERAGE_TASK = 100;
    private static final int SLOW_TASK = 500;
    private static final Duration MAX_WAIT = Duration.ofSeconds(10);

    private ThreadPool pool;
    private ThreadPool.Metrics metrics;
    private final Throwable throwable = new IOException("Test failure");
    private Collection<Class<?>> failureClasses = new LinkedBlockingQueue<>();

    private static CountDownLatch countDown;

    @BeforeEach
    void setup() {
        createPool(false);
        failureClasses.clear();
    }

    @AfterEach
    void after() {
        Dispatcher.getInstance().shutdown();
    }

    @Test
    void getDefault() {
        assertNotNull(ThreadPool.get());
    }

    @Test
    void shutdown() {
        pool.shutdown();
        assertTrue(pool.isShutdown());
        assertEquals(0, ThreadPool.list().size());
    }

    @Test
    void shutdownNow() {
        List<Runnable> pending = pool.shutdownNow();
        assertTrue(pool.isShutdown());
        assertEquals(0, pending.size());
        assertEquals(0, ThreadPool.list().size());
    }

    @Test
    void countPools() {
        assertEquals(1, ThreadPool.list().size());
        ThreadPool pool2 = ThreadPool.create("pool2");
        assertEquals(2, ThreadPool.list().size());
        pool2.shutdown();
        assertEquals(1, ThreadPool.list().size());
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
        awaitShort().until(() -> pool.isIdle());
        assertEquals(5, metrics.getExecutedTaskCount());

        pool.suspend();
        assertTrue(pool.isPaused());
        fireSingleRun(FAST_TASK, 5);
        Thread.sleep(1000);
        assertEquals(5, metrics.getExecutedTaskCount());

        pool.resume();
        assertFalse(pool.isPaused());
        Thread.sleep(1000);
        awaitShort()
                .untilAsserted(() -> assertEquals(10, metrics.getExecutedTaskCount()));
    }

    @Test
    void virtualThreads() {
        createPool(true);
        fireSingleRun(FAST_TASK, 5);
        awaitShort().until(() -> pool.isIdle());
        assertEquals(5, metrics.getExecutedTaskCount());
    }

    @Test
    void submitRunnable() throws ExecutionException, InterruptedException {
        pool.submit(new RunnableTask(FAST_TASK));
        awaitShort().untilAsserted(() -> assertEquals(1, metrics.getExecutedTaskCount()));

        Future<String> future = pool.submit(new RunnableTask(FAST_TASK), "test");
        awaitShort().untilAsserted(() -> assertEquals(2, metrics.getExecutedTaskCount()));
        assertEquals("test", future.get());
    }

    @Test
    void submitCallable() throws ExecutionException, InterruptedException {
        Future<Integer> future = pool.submit(new CallableTask(1, FAST_TASK));
        awaitShort().untilAsserted(() -> assertEquals(1, metrics.getExecutedTaskCount()));
        assertEquals(10, future.get());
    }

    @Test
    void execute() {
        assertFalse(pool.isPaused());
        fireSingleRun(FAST_TASK, 5);
        awaitShort().until(() -> pool.isIdle());
        assertEquals(5, metrics.getExecutedTaskCount());
    }

    @Test
    void executeDelayed() {
        pool.execute(new DelayedTask(AVERAGE_TASK));
        assertEquals(0, metrics.getExecutedTaskCount());
        awaitShort().until(() -> metrics.getExecutedTaskCount() >= 1);
        assertEquals(1, metrics.getExecutedTaskCount());
    }

    @Test
    void scheduleFixedDelay() {
        pool.execute(new ScheduledTask(AVERAGE_TASK, net.microfalx.threadpool.ScheduledTask.Strategy.FIXED_DELAY));
        pool.scheduleWithFixedDelay(new RunnableTask(AVERAGE_TASK), AVERAGE_TASK, AVERAGE_TASK, TimeUnit.MILLISECONDS);
        assertEquals(0, metrics.getExecutedTaskCount());
        awaitLong().until(() -> metrics.getExecutedTaskCount() >= 10);
        assertThat(metrics.getExecutedTaskCount()).isGreaterThanOrEqualTo(10);
    }

    @Test
    void scheduleFixedDelayAndFailure() {
        pool.execute(new ScheduledTask(AVERAGE_TASK, net.microfalx.threadpool.ScheduledTask.Strategy.FIXED_DELAY, throwable));
        pool.scheduleWithFixedDelay(new RunnableTask(AVERAGE_TASK, throwable), AVERAGE_TASK, AVERAGE_TASK, TimeUnit.MILLISECONDS);
        assertEquals(0, metrics.getExecutedTaskCount());
        awaitLong().until(() -> metrics.getExecutedTaskCount() >= 10);
        assertThat(metrics.getExecutedTaskCount()).isGreaterThanOrEqualTo(10);
        assertThat(failureClasses.size()).isGreaterThan(10);
    }

    @Test
    void scheduleFixedRate() {
        pool.execute(new ScheduledTask(AVERAGE_TASK, net.microfalx.threadpool.ScheduledTask.Strategy.FIXED_RATE));
        pool.scheduleAtFixedRate(new RunnableTask(AVERAGE_TASK), AVERAGE_TASK, AVERAGE_TASK, TimeUnit.MILLISECONDS);
        assertEquals(0, metrics.getExecutedTaskCount());
        awaitLong().until(() -> metrics.getExecutedTaskCount() >= 10);
        assertThat(metrics.getExecutedTaskCount()).isGreaterThanOrEqualTo(10);
    }

    @Test
    void scheduleDelay() {
        pool.schedule(new RunnableTask(AVERAGE_TASK), AVERAGE_TASK, TimeUnit.MILLISECONDS);
        assertEquals(0, metrics.getExecutedTaskCount());
        awaitShort().until(() -> metrics.getExecutedTaskCount() >= 1);
        assertEquals(1, metrics.getExecutedTaskCount());
    }

    @Test
    void scheduleCallable() throws ExecutionException, InterruptedException, TimeoutException {
        ScheduledFuture<Integer> future = pool.schedule(new CallableTask(AVERAGE_TASK, 10), AVERAGE_TASK, TimeUnit.MILLISECONDS);
        assertEquals(0, metrics.getExecutedTaskCount());
        awaitShort().atMost(Duration.ofSeconds(10)).until(() -> metrics.getExecutedTaskCount() >= 1);
        assertEquals(1, metrics.getExecutedTaskCount());
        assertEquals(10, future.get(2, TimeUnit.SECONDS));
    }

    @Test
    void executeScheduledFast() {
        pool.execute(new ScheduledTask(FAST_TASK));
        assertEquals(0, metrics.getExecutedTaskCount());
        awaitShort().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(metrics.getExecutedTaskCount()).isGreaterThan(300));
    }

    @Test
    void executeScheduledAverage() {
        pool.execute(new ScheduledTask(AVERAGE_TASK));
        assertEquals(0, metrics.getExecutedTaskCount());
        awaitShort().atMost(Duration.ofSeconds(20))
                .untilAsserted(() -> assertThat(metrics.getExecutedTaskCount()).isGreaterThan(70));
    }

    @Test
    void executeScheduledSlow() {
        pool.execute(new ScheduledTask(SLOW_TASK));
        assertEquals(0, metrics.getExecutedTaskCount());
        awaitShort().atMost(Duration.ofSeconds(10))
                .untilAsserted(() -> assertThat(metrics.getExecutedTaskCount()).isGreaterThan(15));
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
                .rejectedHandler((p, t) -> {
                    failureCounter.incrementAndGet();
                    ((Runnable) t).run();
                })
                .build();
        int count = pool.getOptions().getMaximumSize() + pool.getOptions().getQueueSize();
        countDown = new CountDownLatch(count + 10);
        fireSingleRun(50, count);
        fireSingleRun(50, 10);
        assertTrue(countDown.await(4, TimeUnit.SECONDS));
        assertThat(failureCounter.get()).isGreaterThan(1);
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
                .failureHandler((pool, thread, throwable, task) -> failureCounter.incrementAndGet())
                .build();
        countDown = new CountDownLatch(1);
        pool.execute(new RunnableTask(50, new IOException("Failure")));
        assertTrue(countDown.await(4, TimeUnit.SECONDS));
        assertEquals(1, failureCounter.get());
    }

    @Test
    void getCompletedTasks() {
        pool.execute(new RunnableTask(50, null));
        awaitShort().until(() -> !pool.getCompletedTasks().isEmpty());
    }

    private ConditionFactory awaitShort() {
        return Awaitility.await().atMost(MAX_WAIT);
    }

    private ConditionFactory awaitLong() {
        return Awaitility.await().atMost(MAX_WAIT.multipliedBy(3));
    }

    private void createPool(boolean virtual) {
        pool = ThreadPool.builder(virtual ? "Virtual" : "Native").virtual(virtual).failureHandler(new FailedHandlerImpl()).build();
        metrics = pool.getMetrics();
    }

    private void fireSingleRun(int executionTime, int count) {
        for (int i = 0; i < count; i++) {
            pool.execute(new RunnableTask(executionTime));
        }
    }

    static class DelayedTask implements net.microfalx.threadpool.DelayedTask, Runnable {

        private final int executionTime;
        private Throwable throwable;

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
            if (throwable != null) throw new IllegalStateException(throwable);
        }
    }

    static class ScheduledTask implements net.microfalx.threadpool.ScheduledTask, Runnable {

        private final int executionTime;
        private final Strategy strategy;
        private Throwable throwable;

        public ScheduledTask(int executionTime) {
            this(executionTime, Strategy.FIXED_RATE);
        }

        public ScheduledTask(int executionTime, Strategy strategy) {
            this.executionTime = executionTime;
            this.strategy = strategy;
        }

        public ScheduledTask(int executionTime, Strategy strategy, Throwable throwable) {
            this.executionTime = executionTime;
            this.strategy = strategy;
            this.throwable = throwable;
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
            if (throwable != null) throw new IllegalStateException(throwable);
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

    private class FailedHandlerImpl implements ThreadPool.FailedHandler {

        @Override
        public void failed(ThreadPool pool, Thread thread, Throwable throwable, Object task) {
            throwable = throwable.getCause() != null ? throwable.getCause() : throwable;
            failureClasses.add(throwable.getClass());
        }
    }
}