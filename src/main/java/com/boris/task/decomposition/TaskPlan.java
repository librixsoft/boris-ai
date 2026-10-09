package com.boris.task.decomposition;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public class TaskPlan {

    public enum PlanStatus {
        CREATED, IN_PROGRESS, COMPLETED, FAILED, ABORTED
    }

    private final String id;
    private final String originalTask;
    private final TaskComplexity complexity;
    private final List<Microtask> microtasks;
    private PlanStatus status;
    private int currentStep;
    private long createdAt;
    private long completedAt;
    private boolean requiresConfirmation;

    public TaskPlan(String originalTask, TaskComplexity complexity) {
        this.id = UUID.randomUUID().toString().substring(0, 12);
        this.originalTask = originalTask;
        this.complexity = complexity;
        this.microtasks = new ArrayList<>();
        this.status = PlanStatus.CREATED;
        this.currentStep = 0;
        this.createdAt = System.currentTimeMillis();
        this.requiresConfirmation = complexity.requiresDecomposition();
    }

    public String getId() {
        return id;
    }

    public String getOriginalTask() {
        return originalTask;
    }

    public TaskComplexity getComplexity() {
        return complexity;
    }

    public List<Microtask> getMicrotasks() {
        return Collections.unmodifiableList(microtasks);
    }

    public PlanStatus getStatus() {
        return status;
    }

    public void setStatus(PlanStatus status) {
        this.status = status;
        if (status == PlanStatus.COMPLETED || status == PlanStatus.FAILED || status == PlanStatus.ABORTED) {
            this.completedAt = System.currentTimeMillis();
        }
    }

    public int getCurrentStep() {
        return currentStep;
    }

    public boolean isRequiresConfirmation() {
        return requiresConfirmation;
    }

    public void setRequiresConfirmation(boolean requiresConfirmation) {
        this.requiresConfirmation = requiresConfirmation;
    }

    public void addMicrotask(Microtask task) {
        microtasks.add(task);
    }

    public void addMicrotask(String description) {
        microtasks.add(new Microtask(microtasks.size() + 1, description));
    }

    public void addMicrotask(String description, String expectedOutput) {
        microtasks.add(new Microtask(microtasks.size() + 1, description, expectedOutput));
    }

    public Optional<Microtask> getCurrentMicrotask() {
        if (currentStep < microtasks.size()) {
            return Optional.of(microtasks.get(currentStep));
        }
        return Optional.empty();
    }

    public Optional<Microtask> getNextMicrotask() {
        if (currentStep < microtasks.size()) {
            Microtask task = microtasks.get(currentStep);
            currentStep++;
            return Optional.of(task);
        }
        return Optional.empty();
    }

    public boolean hasMoreTasks() {
        return currentStep < microtasks.size();
    }

    public int getTotalTasks() {
        return microtasks.size();
    }

    public int getCompletedTasks() {
        return (int) microtasks.stream()
                .filter(t -> t.getStatus() == Microtask.Status.COMPLETED)
                .count();
    }

    public double getProgress() {
        if (microtasks.isEmpty()) return 0.0;
        return (double) getCompletedTasks() / microtasks.size() * 100;
    }

    public String getProgressBar() {
        int total = microtasks.size();
        int completed = getCompletedTasks();
        int barWidth = 20;
        int filled = (int) ((double) completed / total * barWidth);
        StringBuilder bar = new StringBuilder("[");
        for (int i = 0; i < barWidth; i++) {
            bar.append(i < filled ? "█" : "░");
        }
        bar.append(String.format("] %d/%d (%.0f%%)", completed, total, getProgress()));
        return bar.toString();
    }

    public String toSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format("📋 Plan: %s\n", id));
        sb.append(String.format("   Complejidad: %s\n", complexity.getLabel()));
        sb.append(String.format("   Progreso: %s\n", getProgressBar()));
        sb.append("   Tareas:\n");
        for (Microtask task : microtasks) {
            sb.append("   ").append(task.toProgressString()).append("\n");
        }
        return sb.toString();
    }

    public String toJson() {
        StringBuilder json = new StringBuilder();
        json.append("{\n");
        json.append(String.format("  \"id\": \"%s\",\n", id));
        json.append(String.format("  \"complexity\": \"%s\",\n", complexity.getLabel()));
        json.append(String.format("  \"status\": \"%s\",\n", status));
        json.append(String.format("  \"progress\": %.1f,\n", getProgress()));
        json.append("  \"microtasks\": [\n");
        for (int i = 0; i < microtasks.size(); i++) {
            Microtask t = microtasks.get(i);
            json.append(String.format("    {\"order\": %d, \"status\": \"%s\", \"description\": \"%s\"}",
                    t.getOrder(), t.getStatus(), escapeJson(t.getDescription())));
            if (i < microtasks.size() - 1) json.append(",");
            json.append("\n");
        }
        json.append("  ]\n}");
        return json.toString();
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
