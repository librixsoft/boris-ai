package com.boris.edit;

import com.boris.edit.ui.DiffPanel;
import com.boris.edit.ui.DiffRenderer;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class LineByLineEditor {

    private final SymbolCache symbolCache;
    private final LineValidator validator;
    private final DiffRenderer renderer;

    private EditSession currentSession;
    private Consumer<String> outputHandler;
    private Consumer<EditSession> uiUpdateHandler;

    public LineByLineEditor(Path contextFile) throws IOException {
        this.symbolCache = new SymbolCache();
        if (contextFile != null && Files.exists(contextFile)) {
            this.symbolCache.loadFromContext(contextFile);
        }
        this.validator = new LineValidator(symbolCache);
        this.renderer = new DiffRenderer();
    }

    public LineByLineEditor(SymbolCache cache) {
        this.symbolCache = cache;
        this.validator = new LineValidator(cache);
        this.renderer = new DiffRenderer();
    }

    public void setOutputHandler(Consumer<String> handler) {
        this.outputHandler = handler;
    }

    public void setUiUpdateHandler(Consumer<EditSession> handler) {
        this.uiUpdateHandler = handler;
    }

    public EditSession startCreate(Path filePath) {
        currentSession = EditSession.forNewFile(filePath);
        notifyUiUpdate();
        output("📝 EDITANDO: " + filePath + " (nuevo)");
        return currentSession;
    }

    public EditSession startModify(Path filePath) throws IOException {
        List<String> existingContent = new ArrayList<>();
        if (Files.exists(filePath)) {
            existingContent = Files.readAllLines(filePath);
        }
        currentSession = EditSession.forModify(filePath, existingContent);
        notifyUiUpdate();
        output("📝 EDITANDO: " + filePath + " (modificar)");
        return currentSession;
    }

    public EditSession startAppend(Path filePath) throws IOException {
        List<String> existingContent = new ArrayList<>();
        if (Files.exists(filePath)) {
            existingContent = Files.readAllLines(filePath);
        }
        currentSession = EditSession.forAppend(filePath, existingContent);
        notifyUiUpdate();
        output("📝 EDITANDO: " + filePath + " (agregar)");
        return currentSession;
    }

    public LineResult processLine(String line) {
        if (currentSession == null || !currentSession.isActive()) {
            return LineResult.error("No hay sesión de edición activa");
        }

        currentSession.addPendingLine(line);
        notifyUiUpdate();

        LineValidator.ValidationResult validation = validator.validate(line);

        if (validation.isValid()) {
            // Escribir línea directamente al archivo
            try {
                writeLineToFile(line);
            } catch (IOException e) {
                return LineResult.error("Error escribiendo al archivo: " + e.getMessage());
            }

            currentSession.markLastLineValidated();
            notifyUiUpdate();

            // Output con formato diff visual (verde +)
            int lineNum = currentSession.getCurrentLineNumber() - 1;
            outputDiffLine(lineNum, DiffLineType.ADDED, line, "✓");

            return LineResult.ok();
        } else {
            currentSession.markLastLineError();
            notifyUiUpdate();

            // Error de validación - NO es línea borrada
            // Solo mostrar mensaje de error, no como diff
            int lineNum = currentSession.getCurrentLineNumber();
            output(String.format("%4d │ ⚠ %s  ❌", lineNum, truncate(line, 50)));
            output(validation.formatError());

            if (!currentSession.canRetry()) {
                return LineResult.aborted("Máximo de intentos alcanzado para esta línea");
            }

            return LineResult.needsCorrection(
                validation.errorMessage(),
                validation.suggestions()
            );
        }
    }

    private void writeLineToFile(String line) throws IOException {
        Path filePath = currentSession.getFilePath();
        Files.createDirectories(filePath.getParent());

        if (currentSession.getCurrentLineNumber() == 1) {
            // Primera línea: crear archivo nuevo
            Files.writeString(filePath, line + "\n");
        } else {
            // Líneas siguientes: append
            Files.writeString(filePath, line + "\n", StandardOpenOption.APPEND);
        }
    }

    public LineResult correctLine(String correctedLine) {
        if (currentSession == null || !currentSession.isActive()) {
            return LineResult.error("No hay sesión de edición activa");
        }

        LineValidator.ValidationResult validation = validator.validate(correctedLine);

        if (validation.isValid()) {
            // Escribir línea corregida al archivo
            try {
                writeLineToFile(correctedLine);
            } catch (IOException e) {
                return LineResult.error("Error escribiendo al archivo: " + e.getMessage());
            }

            currentSession.correctLastLine(correctedLine);
            notifyUiUpdate();

            // Output con formato diff visual (verde + corregido)
            int lineNum = currentSession.getCurrentLineNumber() - 1;
            outputDiffLine(lineNum, DiffLineType.ADDED, correctedLine, "✓*");

            return LineResult.ok();
        } else {
            if (!currentSession.canRetry()) {
                return LineResult.aborted("Máximo de intentos alcanzado");
            }
            return LineResult.needsCorrection(
                validation.errorMessage(),
                validation.suggestions()
            );
        }
    }

    public FinishResult finish() throws IOException {
        if (currentSession == null) {
            return FinishResult.error("No hay sesión activa");
        }

        currentSession.finish();

        if (currentSession.getErrorCount() > 0) {
            return FinishResult.error("Hay líneas con errores sin corregir");
        }

        // El archivo ya está escrito línea por línea, solo mostramos el diff
        String diff = renderer.renderToString(currentSession);
        output("\n" + diff);
        output("✓ Archivo completo: " + currentSession.getFilePath());

        EditSession finishedSession = currentSession;
        int totalLines = finishedSession.getValidatedCount();
        currentSession = null;

        return FinishResult.success(finishedSession, totalLines);
    }

    public void abort() {
        if (currentSession != null) {
            currentSession.abort();
            output("⚠️ Edición cancelada");
            currentSession = null;
        }
    }

    public boolean hasActiveSession() {
        return currentSession != null && currentSession.isActive();
    }

    public EditSession getCurrentSession() {
        return currentSession;
    }

    public SymbolCache getSymbolCache() {
        return symbolCache;
    }

    private void output(String message) {
        if (outputHandler != null) {
            outputHandler.accept(message);
        }
    }

    private void outputDiffLine(int lineNum, DiffLineType type, String content, String status) {
        String prefix = switch (type) {
            case ADDED -> "+ ";
            case REMOVED -> "- ";
            case MODIFIED -> "~ ";
            case CONTEXT -> "  ";
        };

        // Formato: "  12 │ + contenido de la línea  ✓"
        String formatted = String.format("%4d │ %s%s  %s", lineNum, prefix, truncate(content, 50), status);

        // Agregar marcadores de color para el renderizador
        String colorMarker = switch (type) {
            case ADDED -> "{{GREEN}}";
            case REMOVED -> "{{RED}}";
            case MODIFIED -> "{{YELLOW}}";
            case CONTEXT -> "";
        };

        output(colorMarker + formatted);
    }

    private void notifyUiUpdate() {
        if (uiUpdateHandler != null && currentSession != null) {
            uiUpdateHandler.accept(currentSession);
        }
    }

    private String truncate(String s, int maxLen) {
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    // Result classes
    public record LineResult(
        Status status,
        String message,
        List<String> suggestions
    ) {
        public enum Status { OK, NEEDS_CORRECTION, ERROR, ABORTED }

        public static LineResult ok() {
            return new LineResult(Status.OK, null, List.of());
        }

        public static LineResult needsCorrection(String message, List<String> suggestions) {
            return new LineResult(Status.NEEDS_CORRECTION, message, suggestions);
        }

        public static LineResult error(String message) {
            return new LineResult(Status.ERROR, message, List.of());
        }

        public static LineResult aborted(String message) {
            return new LineResult(Status.ABORTED, message, List.of());
        }

        public boolean isOk() { return status == Status.OK; }
        public boolean needsCorrection() { return status == Status.NEEDS_CORRECTION; }
    }

    public record FinishResult(
        boolean success,
        String message,
        EditSession session,
        int totalLines
    ) {
        public static FinishResult success(EditSession session, int totalLines) {
            return new FinishResult(true, "OK", session, totalLines);
        }

        public static FinishResult error(String message) {
            return new FinishResult(false, message, null, 0);
        }
    }
}
