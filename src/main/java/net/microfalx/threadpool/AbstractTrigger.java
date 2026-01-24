package net.microfalx.threadpool;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Base class for all triggers.
 */
public abstract class AbstractTrigger implements Trigger {

    private volatile Instant lastScheduledExecution;
    private volatile Instant lastActualExecution;
    private volatile Instant lastCompletion;

    private final AtomicBoolean initialized = new AtomicBoolean();

    /**
     * Return the last <i>scheduled</i> execution time of the task.
     *
     * @return the last scheduled time, null if first time
     */
    public final Instant getLastScheduledExecution() {
        return lastScheduledExecution;
    }

    /**
     * Return the last <i>actual</i> execution time of the task.
     *
     * @return the actual execution time, null if first time
     */
    public final Instant getLastActualExecution() {
        return lastActualExecution;
    }

    /**
     * Return the last completion time of the task.
     *
     * @return the last completion time, null if was not executed before
     */
    public final Instant getLastCompletion() {
        return lastCompletion;
    }

    /**
     * Updates the time references used to calculate the next execution time.
     *
     * @param lastScheduledExecution the time when the task was executed last time
     * @param lastActualExecution    the time when the task was actually executed (it could sit in the queue)
     * @param lastCompletion         the time when the task was completed
     */
    void update(long lastScheduledExecution, long lastActualExecution, long lastCompletion) {
        this.lastScheduledExecution = toInstant(lastScheduledExecution);
        this.lastActualExecution = toInstant(lastActualExecution);
        this.lastCompletion = toInstant(lastCompletion);
    }

    void initialize() {
        if (!initialized.compareAndSet(false, true)) {
            throw new IllegalStateException("Trigger is already registered: " + this);
        }
    }

    private Instant toInstant(long epochMilli) {
        return epochMilli > 0 ? Instant.ofEpochMilli(epochMilli) : null;
    }
}
