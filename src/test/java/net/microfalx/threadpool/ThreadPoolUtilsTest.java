package net.microfalx.threadpool;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class ThreadPoolUtilsTest {

    private volatile boolean stopped;

    @AfterEach
    void destroy() {
        stopped = true;
    }

    @Test
    void getCarrierPlatformThread() {
        Thread thread = Thread.ofVirtual().unstarted(this::doSomething);
        assertNull(ThreadPoolUtils.getCarrier(thread));

    }

    @Test
    void getCarrierVirtualThreadUnstarted() {
        Thread thread = Thread.ofVirtual().unstarted(this::doSomething);
        assertNull(ThreadPoolUtils.getCarrier(thread));
    }

    @Test
    void getCarrierPlatformThreadStarted() {
        Thread thread = Thread.ofVirtual().start(this::doSomething);
        assertNotNull(ThreadPoolUtils.getCarrier(thread));
    }

    private void doSomething() {
        long count = 0;
        while (!stopped) {
            count++;
        }
        System.out.println("Iterations: " + count);
    }

}