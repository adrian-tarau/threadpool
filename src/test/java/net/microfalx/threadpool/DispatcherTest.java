package net.microfalx.threadpool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

class DispatcherTest {

    private static final int MAX_EXECUTED = 10_000;

    private Dispatcher dispatcher;
    private ThreadPoolImpl threadPool;
    private AtomicInteger executionCount = new AtomicInteger();

    @BeforeEach
    void setup() {
        dispatcher = new Dispatcher();
        threadPool = (ThreadPoolImpl) ThreadPool.create("Test");
        executionCount.set(0);

        dispatcher.register(threadPool);
    }

    @Test
    public void unregister() {
        ThreadPoolImpl newPool = (ThreadPoolImpl) ThreadPool.create();
        dispatcher.register(newPool);
        assertEquals(2, dispatcher.getThreadPools().size());
        dispatcher.unregister(newPool);
        assertEquals(1, dispatcher.getThreadPools().size());
    }

    @Test
    public void handleRunnable() {
        threadPool.execute(() -> executionCount.incrementAndGet());
        await().untilAsserted(() -> assertEquals(1, executionCount.get()));
    }

    @Test
    public void handleSchedulingStorm() throws Exception {
        for (int i = 0; i < MAX_EXECUTED; i++) {
            threadPool.execute(() -> executionCount.incrementAndGet());
        }
        await().untilAsserted(() -> assertEquals(MAX_EXECUTED, executionCount.get()));
    }

}