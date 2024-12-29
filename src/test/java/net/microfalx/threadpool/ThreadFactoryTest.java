package net.microfalx.threadpool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

class ThreadFactoryTest {

    private ThreadFactory factory;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setup() throws InterruptedException {
        ThreadPoolImpl threadPool = Mockito.mock(ThreadPoolImpl.class);
        when(threadPool.getOptions()).thenReturn(new OptionsImpl());
        when(threadPool.nextTask()).thenReturn(null);
        factory = new ThreadFactory(threadPool);
    }

    @Test
    void createThread() throws Exception {
        Thread thread = factory.createThread(() -> {
        }, true);
        assertEquals("Default 1", thread.getName());
        assertEquals(1, factory.getIndex(thread));
    }

    @Test
    void destroyThread() throws Exception {
        Thread thread = factory.createThread(() -> {
        }, true);
        factory.destroyThread(thread);
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            System.gc();
            return thread.isAlive();
        });
    }

}