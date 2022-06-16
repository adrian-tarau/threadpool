package net.microfalx.threadpool;

import net.microfalx.objectpool.ObjectFactory;
import net.microfalx.objectpool.ObjectPool;

import java.time.Duration;

class OptionsImpl implements ThreadPool.Options {

    ThreadFactory factory;
    ObjectPool.Options<Thread> options;

    @Override
    public int getMinimum() {
        return options.getMinimum();
    }

    @Override
    public int getMaximum() {
        return options.getMaximum();
    }

    @Override
    public Duration getTimeToLiveTimeout() {
        return options.getTimeToLiveTimeout();
    }

    @Override
    public Duration getAbandonedTimeout() {
        return options.getAbandonedTimeout();
    }

    @Override
    public Duration getInactiveTimeout() {
        return options.getInactiveTimeout();
    }

    @Override
    public Duration getMaximumWait() {
        return options.getMaximumWait();
    }

    @Override
    public Duration getMaximumReuseTime() {
        return options.getMaximumReuseTime();
    }

    @Override
    public int getMaximumReuseCount() {
        return options.getMaximumReuseCount();
    }

    @Override
    public ObjectPool.Strategy getStrategy() {
        return options.getStrategy();
    }

    @Override
    public ObjectFactory<Thread> getFactory() {
        return options.getFactory();
    }
}
