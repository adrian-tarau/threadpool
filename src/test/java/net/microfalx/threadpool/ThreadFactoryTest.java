package net.microfalx.threadpool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

class ThreadFactoryTest {

    private ThreadFactory factory;
    private ThreadPoolImpl threadPool;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setup() {
        threadPool = Mockito.mock(ThreadPoolImpl.class);
        factory = new ThreadFactory(threadPool, "Default");
        when(threadPool.getIndex(any(Thread.class))).thenCallRealMethod();
        ThreadPool.Options options = Mockito.mock(ThreadPool.Options.class);
        factory.threadPool = threadPool;
        when(threadPool.getOptions()).thenReturn(options);
    }

    @Test
    void createThread() throws Exception {
        Thread thread = factory.createThread();
        assertEquals("Default 1", thread.getName());
        assertEquals(1, threadPool.getIndex(thread));
    }

    @Test
    void destroyThread() throws Exception {
        ThreadImpl thread = factory.createThread();
        assertFalse(thread.isStopped());
        factory.destroyThread(thread);
        await().atMost(Duration.ofSeconds(1)).until(thread::isStopped);
    }

}