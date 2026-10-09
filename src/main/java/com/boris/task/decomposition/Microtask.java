package com.boris.task.decomposition;

import java.util.UUID;

public class Microtask {

    public enum Status {
        PENDING, IN_PROGRESS, COMPLETED, FAILED, SKIPPED
    }

    private final String id;
    private final int order;
    private final String description;
    private final String expectedOutput;
    private Status status;
    private String result;
    private long startedAt;
    private long completedAt;

    public Microtask(int order, String description) {
        this(order, description, null);
    }

    public Microtask(int order, String description, String expectedOutput) {
        this.id = UUID.randomUUID().toString().substring(0, 8);
        this.order = order;
        this.description = description;
        this.expectedOutput = expectedOutput;
        this.status = Status.PENDING;
    }

    public String getId() {
        return id;
    }

    public int getOrder() {
        return order;
    }

    public String getDescription() {
        return description;
    }

    public String getExpectedOutput() {
        return expectedOutput;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public void markInProgress() {
        this.status = Status.IN_PROGRESS;
        this.startedAt = System.currentTimeMillis();
    }

    public void markCompleted(String result) {
        this.status = Status.COMPLETED;
        this.result = result;
        this.completedAt = System.currentTimeMillis();
    }

    public void markFailed(String error) {
        this.status = Status.FAILED;
        this.result = error;
        this.completedAt = System.currentTimeMillis();
    }

    public long getDurationMs() {
        if (startedAt == 0) return 0;
        long end = completedAt > 0 ? completedAt : System.currentTimeMillis();
        return end - startedAt;
    }

    public String toProgressString() {
        String statusIcon = switch (status) {
            case PENDING -> "○";
            case IN_PROGRESS -> "◐";
            case COMPLETED -> "●";
            case FAILED -> "✗";
            case SKIPPED -> "⊘";
        };
        return String.format("%s [%d] %s", statusIcon, order, description);
    }

    @Override
    public String toString() {
        return String.format("Microtask{id='%s', order=%d, status=%s, description='%s'}",
                id, order, status, description);
    }
}
