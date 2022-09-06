package net.microfalx.threadpool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static java.time.Duration.ofMinutes;
import static java.time.Duration.ofSeconds;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class OptionsImplTest {

    private ThreadPool threadPool;

    @BeforeEach
    void setup() {
        threadPool = ThreadPool.builder("Test").build();
    }

    @Test
    void getDefaultOptions() {
        ThreadPool.Options options = threadPool.getOptions();
        assertNotNull(options);
        assertEquals(10, options.getMaximumSize());
        assertEquals(100, options.getQueueSize());
        assertEquals(ofSeconds(60), options.getKeepAliveTime());
        assertEquals(ofMinutes(15), options.getMaximumReuseTime());
        assertNotNull(options.toString());
    }

    @Test
    void getCustomOptions() {
        threadPool = ThreadPool.builder("Test").maximumSize(15)
                .maximumReuseTime(ofMinutes(5))
                .keepAliveTime(ofSeconds(50))
                .queueSize(200)
                .build();
        ThreadPool.Options options = threadPool.getOptions();
        assertNotNull(options);
        assertEquals(15, options.getMaximumSize());
        assertEquals(200, options.getQueueSize());
        assertEquals(ofSeconds(50), options.getKeepAliveTime());
        assertEquals(ofMinutes(5), options.getMaximumReuseTime());
        assertNotNull(options.toString());
    }

}