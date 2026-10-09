package com.boris.task.decomposition;

public enum TaskComplexity {
    SIMPLE("simple", 1, 2),
    MEDIUM("medium", 3, 5),
    COMPLEX("complex", 6, 10),
    VERY_COMPLEX("very_complex", 11, Integer.MAX_VALUE);

    private final String label;
    private final int minSteps;
    private final int maxSteps;

    TaskComplexity(String label, int minSteps, int maxSteps) {
        this.label = label;
        this.minSteps = minSteps;
        this.maxSteps = maxSteps;
    }

    public String getLabel() {
        return label;
    }

    public int getMinSteps() {
        return minSteps;
    }

    public int getMaxSteps() {
        return maxSteps;
    }

    public boolean requiresDecomposition() {
        return this == COMPLEX || this == VERY_COMPLEX;
    }

    public static TaskComplexity fromLabel(String label) {
        if (label == null) return SIMPLE;
        String lower = label.toLowerCase().trim();
        for (TaskComplexity c : values()) {
            if (c.label.equals(lower)) return c;
        }
        if (lower.contains("complex") || lower.contains("large")) return COMPLEX;
        if (lower.contains("medium") || lower.contains("moderate")) return MEDIUM;
        return SIMPLE;
    }
}
