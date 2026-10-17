package com.boris.edit;

public record DiffLine(
    int lineNumber,
    DiffLineType type,
    String content,
    ValidationStatus status
) {
    public enum ValidationStatus {
        PENDING,    // Esperando validación
        VALIDATED,  // Validado OK
        ERROR,      // Error de validación
        CORRECTED   // Corregido después de error
    }

    public static DiffLine added(int lineNumber, String content) {
        return new DiffLine(lineNumber, DiffLineType.ADDED, content, ValidationStatus.PENDING);
    }

    public static DiffLine removed(int lineNumber, String content) {
        return new DiffLine(lineNumber, DiffLineType.REMOVED, content, ValidationStatus.VALIDATED);
    }

    public static DiffLine context(int lineNumber, String content) {
        return new DiffLine(lineNumber, DiffLineType.CONTEXT, content, ValidationStatus.VALIDATED);
    }

    public DiffLine validated() {
        return new DiffLine(lineNumber, type, content, ValidationStatus.VALIDATED);
    }

    public DiffLine withError() {
        return new DiffLine(lineNumber, type, content, ValidationStatus.ERROR);
    }

    public DiffLine corrected(String newContent) {
        return new DiffLine(lineNumber, type, newContent, ValidationStatus.CORRECTED);
    }

    public String prefix() {
        return switch (type) {
            case ADDED -> "+ ";
            case REMOVED -> "- ";
            case MODIFIED -> "~ ";
            case CONTEXT -> "  ";
        };
    }

    public String statusIcon() {
        return switch (status) {
            case PENDING -> "⏳";
            case VALIDATED -> "✓";
            case ERROR -> "❌";
            case CORRECTED -> "✓*";
        };
    }
}
