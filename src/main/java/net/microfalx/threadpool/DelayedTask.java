package net.microfalx.threadpool;

import java.time.Duration;

/**
 * A task which is executed with a delay.
 * <p>
 * The thread pool the delay provided by the task.
 */
public interface DelayedTask extends Task {

    /**
     * Returns the delay.
     *
     * @return the delay
     */
    Duration getDelay();
}
