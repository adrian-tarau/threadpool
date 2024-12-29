package net.microfalx.threadpool;

import java.util.concurrent.*;

import static java.util.concurrent.TimeUnit.NANOSECONDS;

class CallableTaskWrapper<R> extends TaskWrapper<Callable<R>, R> implements Delayed, Callable<R> {

    private final RunnableScheduledFuture<R> future;
    volatile long delay;
    volatile long interval;
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
        return result;
    }

    @Override
    public long getDelay(TimeUnit unit) {
        return Math.max(0, unit.convert(time - System.nanoTime(), NANOSECONDS));
    }

    @Override
    public int compareTo(Delayed o) {
        return Long.compare(delay, ((CallableTaskWrapper<?>) o).delay);
    }

    @Override
    public R call() throws Exception {
        result = getTask().call();
        return result;
    }

    boolean isPeriodic() {
        return interval > 0;
    }

    @SuppressWarnings("NonAtomicOperationOnVolatileField")
    void updateDelay() {
        switch (mode) {
            case FIXED_RATE:
                time = time + interval;
                break;
            case FIXED_DELAY:
                time = System.nanoTime() + interval;
                break;
        }
    }

    CallableTaskWrapper<R> interval(Mode mode, long interval, TimeUnit unit) {
        this.mode = mode;
        this.interval = NANOSECONDS.convert(interval, unit);
        return this;
    }

    RunnableScheduledFuture<R> getFuture() {
        return future;
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
            return false;
        }
    }
}
