package net.microfalx.threadpool;

import net.microfalx.objectpool.PooledObject;

import java.time.Duration;
import java.time.ZonedDateTime;

final class PooledThreadImpl implements PooledThread {

    private final PooledObject<Thread> pooledObject;

    public PooledThreadImpl(PooledObject<Thread> pooledObject) {
        this.pooledObject = pooledObject;
    }

    @Override
    public State getState() {
        return pooledObject.getState();
    }

    @Override
    public ZonedDateTime getCreatedTime() {
        return pooledObject.getCreatedTime();
    }

    @Override
    public ZonedDateTime getLastBorrowedTime() {
        return pooledObject.getLastBorrowedTime();
    }

    @Override
    public ZonedDateTime getLastReturnedTime() {
        return pooledObject.getLastReturnedTime();
    }

    @Override
    public ZonedDateTime getLastUsedTime() {
        return pooledObject.getLastUsedTime();
    }

    @Override
    public long getBorrowedCount() {
        return pooledObject.getBorrowedCount();
    }

    @Override
    public Duration getBorrowedDuration() {
        return pooledObject.getBorrowedDuration();
    }

    @Override
    public Duration getTotalBorrowedDuration() {
        return pooledObject.getTotalBorrowedDuration();
    }

    @Override
    public Duration getIdleDuration() {
        return pooledObject.getIdleDuration();
    }

    @Override
    public Duration getTotalIdleDuration() {
        return pooledObject.getTotalIdleDuration();
    }

    @Override
    public Thread get() {
        return pooledObject.get();
    }
}
