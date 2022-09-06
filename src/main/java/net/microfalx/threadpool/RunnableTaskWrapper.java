package net.microfalx.threadpool;

class RunnableTaskWrapper extends TaskWrapper<Runnable, Object> {

    RunnableTaskWrapper(ThreadPoolImpl threadPool, Runnable task) {
        super(threadPool, task);
    }

    @Override
    Object doExecute() {
        getTask().run();
        return null;
    }
}
