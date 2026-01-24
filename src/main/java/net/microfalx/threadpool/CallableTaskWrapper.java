package net.microfalx.threadpool;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.StringJoiner;
import java.util.concurrent.*;

import static java.lang.System.currentTimeMillis;
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
        return unit.convert(time - System.nanoTime(), NANOSECONDS);
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
        if (trigger instanceof CronTrigger) {
            return Duration.ZERO;
        } else {
            return ofNanos(delay);
        }
    }

    @Override
    public Duration getInterval() {
        if (trigger instanceof IntervalAwareTrigger intervalTrigger) {
            return intervalTrigger.getInterval();
        } else if (trigger instanceof CronTrigger cronTrigger) {
            return cronTrigger.getInterval();
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

    @Override
    public LocalDateTime getNextExecutionTime() {
        if (isPeriodic()) {
            return super.getNextExecutionTime();
        } else {
            return null;
        }
    }

    public boolean isPeriodic() {
        return trigger != null;
    }

    void updateDelay() {
        updateTrigger();
        if (trigger != null) {
            Instant nextExecution = trigger.nextExecution();
            nextScheduledExecution = nextExecution.toEpochMilli();
            Duration waitTime = Duration.between(Instant.now(), nextExecution);
            this.delay = waitTime.toNanos();
            this.time = System.nanoTime() + this.delay;
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
            joiner.add("trigger=" + trigger).add("interval=" + formatDuration(getInterval()));
        }
    }

    void markScheduled() {
        lastScheduledExecution = currentTimeMillis();
    }

    private void updateTrigger() {
        if (!(trigger instanceof AbstractTrigger abstractTrigger)) return;
        if (lastScheduledExecution > 0) {
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
