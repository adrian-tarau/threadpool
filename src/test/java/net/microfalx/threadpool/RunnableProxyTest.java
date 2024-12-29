package net.microfalx.threadpool;

import net.microfalx.lang.ExceptionUtils;
import net.microfalx.lang.ThreadUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RunnableProxyTest {

    @Mock
    ThreadPoolImpl threadPool;

    private RunnableProxy proxy;

    @BeforeEach
    void before() {
        when(threadPool.getOptions()).thenReturn(new OptionsImpl());
        new Thread(() -> {
            ThreadUtils.sleepMillis(500);
            proxy.stop();
        }).start();
    }

    @AfterEach
    void after() {
        assertTrue(proxy.isStopped());
        assertFalse(proxy.isRunning());
        verify(threadPool, atLeast(1)).completeTask(any(TaskWrapper.class));
    }

    @Test
    void withSuccess() {
        proxy = new RunnableProxy(threadPool, new RunnableTaskWrapper(threadPool, new RunnableTest()));
        proxy.run();
        verify(threadPool, never()).failedTask(any(TaskWrapper.class), any(Throwable.class));
    }

    @Test
    void withFailure() {
        proxy = new RunnableProxy(threadPool, new RunnableTaskWrapper(threadPool, new RunnableTest(true, false)));
        proxy.run();
        verify(threadPool).failedTask(any(TaskWrapper.class), any(Throwable.class));
    }

    @Test
    void withInterrupted() {
        proxy = new RunnableProxy(threadPool, new RunnableTaskWrapper(threadPool, new RunnableTest(true, true)));
        proxy.run();
        verify(threadPool).failedTask(any(TaskWrapper.class), any(Throwable.class));
    }

    static class RunnableTest implements Runnable {

        private boolean fail;
        private boolean interrupt;

        RunnableTest() {

        }

        RunnableTest(boolean fail, boolean interrupt) {
            this.fail = fail;
            this.interrupt = interrupt;
        }

        @Override
        public void run() {
            if (fail) {
                if (interrupt) {
                    ExceptionUtils.throwException(new InterruptedException("Stop"));
                } else {
                    throw new IllegalArgumentException("Not valid");
                }
            } else {
                ThreadUtils.sleepMillis(10);
            }
        }
    }

}