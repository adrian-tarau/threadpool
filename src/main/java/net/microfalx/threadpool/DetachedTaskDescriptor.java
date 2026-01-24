package net.microfalx.threadpool;

import net.microfalx.lang.NamedIdentityAware;

import java.time.Duration;
import java.time.LocalDateTime;

class DetachedTaskDescriptor extends NamedIdentityAware<Long> implements TaskDescriptor {

    private final ThreadPool threadPool;
    private final Class<?> taskClass;
    private final boolean periodic;
    private final Throwable throwable;
    private final LocalDateTime startedAt;
    private final Duration duration;

    DetachedTaskDescriptor(ThreadPool threadPool, TaskDescriptor taskDescriptor) {
        this.threadPool = threadPool;

        setId(taskDescriptor.getId());
        setName(taskDescriptor.getName());
        setDescription(taskDescriptor.getDescription());

        taskClass = taskDescriptor.getTaskClass();
        periodic = taskDescriptor.isPeriodic();
        throwable = taskDescriptor.getThrowable();
        startedAt = taskDescriptor.getStartedAt();
        duration = taskDescriptor.getDuration();
    }

    @Override
    public ThreadPool getThreadPool() {
        return threadPool;
    }

    @Override
    public Thread getThread() {
        return null;
    }

    @Override
    public Class<?> getTaskClass() {
        return taskClass;
    }

    @Override
    public boolean isPeriodic() {
        return periodic;
    }

    @Override
    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    @Override
    public Duration getDuration() {
        return duration;
    }

    @Override
    public Throwable getThrowable() {
        return throwable;
    }

    @Override
    public LocalDateTime getLastExecutionTime() {
        return startedAt;
    }
}
