package net.microfalx.threadpool;

import java.time.Duration;
import java.time.Instant;
import java.util.StringJoiner;
import java.util.concurrent.*;

import static java.time.Duration.ofNanos;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static net.microfalx.lang.FormatterUtils.formatDuration;

final class CallableTaskWrapper<R> extends TaskWrapper<Callable<R>, R> implements Delayed, Callable<R>, ScheduledTask {

    private final RunnableScheduledFuture<R> future;
    volatile long delay;
    volatile Trigger trigger;
    volatile long time;
    volatile R result;

    CallableTaskWrapper(ThreadPoolImpl threadPool, Callable<R> task, long delay, TimeUnit unit) {
        super(threadPool, task);
        this.delay = NANOSECONDS.convert(delay, unit);
        this.time = System.nanoTime() + this.delay;
        future = new ScheduledFutureWrapper<>(this);
        markScheduled();
    }

    @Override
    R doExecute() throws Exception {
        future.run();
        if (isPeriodic()) {
            return null;
        } else {
            return future.get();
        }
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return Math.max(0, unit.convert(time - System.nanoTime(), NANOSECONDS));
    }

    @Override
    public Strategy getStrategy() {
        if (trigger instanceof IntervalAwareTrigger intervalAwareTrigger) {
            return intervalAwareTrigger.getStrategy();
        } else {
            return Strategy.EXPRESSION;
        }
    }

    @Override
    public Duration getInitialDelay() {
        return Duration.ofNanos(delay);
    }

    @Override
    public Duration getInterval() {
        if (trigger instanceof IntervalAwareTrigger intervalTrigger) {
            return intervalTrigger.getInterval();
        } else {
            return Duration.ZERO;
        }
    }

    @Override
    public int compareTo(Delayed o) {
        return Long.compare(delay, ((CallableTaskWrapper<?>) o).delay);
    }

    @Override
    public R call() throws Exception {
        return getTask().call();
    }

    public boolean isPeriodic() {
        return trigger != null;
    }

    void updateDelay() {
        updateTrigger();
        if (trigger != null) {
            Instant nextExecution = trigger.nextExecution();
            Duration waitTime = Duration.between(Instant.now(), nextExecution);
            time = System.nanoTime() + waitTime.toNanos();
        }
    }

    CallableTaskWrapper<R> trigger(Trigger trigger) {
        if (trigger instanceof AbstractTrigger abstractTrigger) {
            abstractTrigger.initialize();
        }
        this.trigger = trigger;
        return this;
    }

    RunnableScheduledFuture<R> getFuture() {
        return future;
    }

    @Override
    void updateToString(StringJoiner joiner) {
        joiner.add("delay=" + formatDuration(ofNanos(delay)));
        if (trigger != null) {
            joiner.add("trigger=" + trigger)
                    .add("interval=" + formatDuration(getInterval()));
        }
    }

    void markScheduled() {
        lastScheduledExecution = System.currentTimeMillis();
    }

    private void updateTrigger() {
        if (trigger instanceof AbstractTrigger abstractTrigger) {
            abstractTrigger.update(lastScheduledExecution, lastActualExecution, lastCompletion);
        }
    }

    class ScheduledFutureWrapper<V> extends FutureTask<V> implements RunnableScheduledFuture<V> {

        public ScheduledFutureWrapper(Callable<V> callable) {
            super(callable);
        }

        @Override
        public long getDelay(TimeUnit unit) {
            return CallableTaskWrapper.this.getDelay(unit);
        }

        @Override
        public int compareTo(Delayed o) {
            return CallableTaskWrapper.this.compareTo(o);
        }

        @Override
        public boolean isPeriodic() {
            return CallableTaskWrapper.this.isPeriodic();
        }

        @Override
        public void run() {
            if (!isPeriodic())
                super.run();
            else {
                super.runAndReset();
            }
        }
    }
}
