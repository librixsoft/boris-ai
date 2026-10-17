package com.boris.edit;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class EditSession {

    public enum Mode {
        CREATE,     // Archivo nuevo
        MODIFY,     // Modificar existente
        APPEND      // Agregar al final
    }

    private final Path filePath;
    private final Mode mode;
    private final List<DiffLine> lines = new ArrayList<>();
    private final List<String> originalContent;
    private int currentLineNumber = 1;
    private int correctionAttempts = 0;
    private static final int MAX_CORRECTION_ATTEMPTS = 3;

    private boolean active = true;
    private int validatedCount = 0;
    private int errorCount = 0;

    public EditSession(Path filePath, Mode mode, List<String> originalContent) {
        this.filePath = filePath;
        this.mode = mode;
        this.originalContent = originalContent != null ? new ArrayList<>(originalContent) : new ArrayList<>();

        if (mode == Mode.MODIFY || mode == Mode.APPEND) {
            this.currentLineNumber = this.originalContent.size() + 1;
        }
    }

    public static EditSession forNewFile(Path filePath) {
        return new EditSession(filePath, Mode.CREATE, null);
    }

    public static EditSession forModify(Path filePath, List<String> existingContent) {
        return new EditSession(filePath, Mode.MODIFY, existingContent);
    }

    public static EditSession forAppend(Path filePath, List<String> existingContent) {
        return new EditSession(filePath, Mode.APPEND, existingContent);
    }

    public void addLine(String content, DiffLine.ValidationStatus status) {
        DiffLine line = new DiffLine(
            currentLineNumber,
            DiffLineType.ADDED,
            content,
            status
        );
        lines.add(line);

        if (status == DiffLine.ValidationStatus.VALIDATED) {
            validatedCount++;
            currentLineNumber++;
            correctionAttempts = 0;
        } else if (status == DiffLine.ValidationStatus.ERROR) {
            errorCount++;
        }
    }

    public void addValidatedLine(String content) {
        addLine(content, DiffLine.ValidationStatus.VALIDATED);
    }

    public void addPendingLine(String content) {
        addLine(content, DiffLine.ValidationStatus.PENDING);
    }

    public void markLastLineValidated() {
        if (!lines.isEmpty()) {
            int lastIdx = lines.size() - 1;
            DiffLine last = lines.get(lastIdx);
            if (last.status() == DiffLine.ValidationStatus.PENDING) {
                lines.set(lastIdx, last.validated());
                validatedCount++;
                currentLineNumber++;
                correctionAttempts = 0;
            }
        }
    }

    public void markLastLineError() {
        if (!lines.isEmpty()) {
            int lastIdx = lines.size() - 1;
            DiffLine last = lines.get(lastIdx);
            if (last.status() == DiffLine.ValidationStatus.PENDING) {
                lines.set(lastIdx, last.withError());
                errorCount++;
                correctionAttempts++;
            }
        }
    }

    public boolean correctLastLine(String newContent) {
        if (lines.isEmpty()) return false;

        int lastIdx = lines.size() - 1;
        DiffLine last = lines.get(lastIdx);

        if (last.status() == DiffLine.ValidationStatus.ERROR) {
            lines.set(lastIdx, last.corrected(newContent));
            errorCount--;
            validatedCount++;
            currentLineNumber++;
            correctionAttempts = 0;
            return true;
        }
        return false;
    }

    public boolean canRetry() {
        return correctionAttempts < MAX_CORRECTION_ATTEMPTS;
    }

    public void finish() {
        this.active = false;
    }

    public void abort() {
        this.active = false;
        this.lines.clear();
    }

    public List<String> buildFinalContent() {
        List<String> result = new ArrayList<>();

        if (mode == Mode.MODIFY) {
            result.addAll(originalContent);
        } else if (mode == Mode.APPEND) {
            result.addAll(originalContent);
        }

        for (DiffLine line : lines) {
            if (line.status() == DiffLine.ValidationStatus.VALIDATED ||
                line.status() == DiffLine.ValidationStatus.CORRECTED) {
                if (mode == Mode.CREATE || mode == Mode.APPEND) {
                    result.add(line.content());
                }
            }
        }

        return result;
    }

    public String generateDiffOutput() {
        StringBuilder sb = new StringBuilder();
        sb.append("@@@ ").append(filePath.getFileName()).append(" @@@\n");

        for (DiffLine line : lines) {
            sb.append(String.format("%4d │%s%s  %s\n",
                line.lineNumber(),
                line.prefix(),
                line.content(),
                line.statusIcon()
            ));
        }

        sb.append(String.format("─── +%d -%d ───\n", getAddedCount(), getRemovedCount()));
        return sb.toString();
    }

    // Getters
    public Path getFilePath() { return filePath; }
    public Mode getMode() { return mode; }
    public List<DiffLine> getLines() { return new ArrayList<>(lines); }
    public int getCurrentLineNumber() { return currentLineNumber; }
    public boolean isActive() { return active; }
    public int getValidatedCount() { return validatedCount; }
    public int getErrorCount() { return errorCount; }
    public int getCorrectionAttempts() { return correctionAttempts; }

    public int getAddedCount() {
        return (int) lines.stream()
            .filter(l -> l.type() == DiffLineType.ADDED)
            .filter(l -> l.status() == DiffLine.ValidationStatus.VALIDATED ||
                        l.status() == DiffLine.ValidationStatus.CORRECTED)
            .count();
    }

    public int getRemovedCount() {
        return (int) lines.stream()
            .filter(l -> l.type() == DiffLineType.REMOVED)
            .count();
    }

    public DiffLine getLastLine() {
        return lines.isEmpty() ? null : lines.get(lines.size() - 1);
    }
}
