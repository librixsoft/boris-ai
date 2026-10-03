package com.boris.task;

public class TaskAborter {

    private volatile Thread currentTask;
    private volatile boolean aborted;

    public void startTask(Thread task) {
        synchronized (this) {
            if (this.aborted) {
                return;
            }
            this.currentTask = task;
        }
    }

    public synchronized void abort() {
        this.aborted = true;
        if (currentTask != null && currentTask.isAlive()) {
            currentTask.interrupt();
        }
    }

    public synchronized boolean isAborted() {
        return aborted;
    }

    public synchronized void reset() {
        this.aborted = false;
        this.currentTask = null;
    }
}
