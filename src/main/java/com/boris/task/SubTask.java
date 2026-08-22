package com.boris.task;

import java.util.concurrent.atomic.AtomicReference;

public final class SubTask {

    public enum Status { PENDING, RUNNING, DONE, FAILED }

    private final int index;
    private final String title;
    private final AtomicReference<Status> status = new AtomicReference<>(Status.PENDING);

    public SubTask(int index, String title) {
        if (title == null || title.isBlank()) {
            throw new com.boris.exceptions.BorisException("SubTask title cannot be blank");
        }
        this.index = index;
        this.title = title.trim();
    }

    public int getIndex() {
        return index;
    }

    public String getTitle() {
        return title;
    }

    public Status getStatus() {
        return status.get();
    }

    public boolean markRunning() {
        return status.compareAndSet(Status.PENDING, Status.RUNNING);
    }

    public void markDone() {
        status.set(Status.DONE);
    }

    public void markFailed() {
        status.set(Status.FAILED);
    }
}
