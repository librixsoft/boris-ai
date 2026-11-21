package com.boris.edit;

import com.boris.tooling.DiffLineNotifier;
import com.boris.context.ProjectContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * StreamingFileWriter
 *
 * Parsea texto del modelo en streaming y filtra tags de pensamiento.
 * El formato de creación de archivos por bloques ````lenguaje:ruta```` ha sido
 * eliminado: la creación/edición de archivos se realiza exclusivamente por las
 * tools nativas {@code write_file} / {@code apply_edit} con preview vía
 * {@link WriteArgsPreviewStreamer}.
 */
public class StreamingFileWriter {

    // Patrón para detectar tags de pensamiento y evitar que se filtren en la interfaz
    private static final Pattern THINK_TAG_PATTERN = Pattern.compile(
        "^\\s*`*</?(?:think|thinking|thought|reasoning)\\b.*?>?\\s*`*$",
        Pattern.CASE_INSENSITIVE
    );

    private StringBuilder buffer = new StringBuilder();
    private boolean inThinkingBlock = false;

    public String processChunk(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }

        buffer.append(chunk);
        StringBuilder nonCodeOutput = new StringBuilder();

        int newlineIdx;
        while ((newlineIdx = buffer.indexOf("\n")) != -1) {
            String line = buffer.substring(0, newlineIdx);
            buffer.delete(0, newlineIdx + 1);

            String result = processLine(line);
            if (result != null) {
                nonCodeOutput.append(result).append("\n");
            }
        }

        if (!inThinkingBlock && buffer.length() > 0) {
            String pending = buffer.toString();
            if (pending.matches("(?i).*`*</?(?:think|thinking|thought|reasoning)>`*.*")) {
                buffer.setLength(0);
            } else if (looksLikePartialThinkTag(pending)) {
                // No emitir ni limpiar: retener el fragmento hasta que llegue más texto
            } else if (!pending.startsWith("`")) {
                nonCodeOutput.append(pending);
                buffer.setLength(0);
            }
        }

        return nonCodeOutput.toString();
    }

    /**
     * Detecta si el texto pendiente podría ser el inicio de un tag de thinking
     * que fue partido entre chunks del stream.
     */
    private boolean looksLikePartialThinkTag(String text) {
        String lower = text.toLowerCase().trim();
        String[] partialPrefixes = {
            "<t", "<th", "<thi", "<thin", "<think", "<thinki", "<thinkin", "<thinking",
            "</t", "</th", "</thi", "</thin", "</think", "</thinki", "</thinkin", "</thinking",
            "<r", "<re", "<rea", "<reas", "<reaso", "<reason", "<reasoni", "<reasonin", "<reasoning",
            "</r", "</re", "</rea", "</reas", "</reaso", "</reason", "</reasoni", "</reasonin", "</reasoning"
        };
        for (String prefix : partialPrefixes) {
            if (lower.endsWith(prefix) || lower.equals(prefix)) {
                return true;
            }
        }
        return false;
    }

    private String processLine(String line) {
        String trimmed = line.trim();

        // Filtrar tags y contenido de pensamiento
        if (trimmed.matches("(?i)^`*<(?:think|thinking|thought|reasoning)\\b.*")) {
            inThinkingBlock = true;
            if (trimmed.matches("(?i).*</(?:think|thinking|thought|reasoning)>`*$")) {
                inThinkingBlock = false;
                return null;
            }
            return null;
        }

        if (inThinkingBlock) {
            if (trimmed.matches("(?i).*</(?:think|thinking|thought|reasoning)>`*$")) {
                inThinkingBlock = false;
            }
            return null;
        }

        if (trimmed.matches("(?i)^`*</(?:think|thinking|thought|reasoning)>`*$") || THINK_TAG_PATTERN.matcher(trimmed).matches()) {
            inThinkingBlock = false;
            return null;
        }

        return line;
    }

    public void reset() {
        buffer.setLength(0);
        inThinkingBlock = false;
    }

    public void finish() {
        if (buffer.length() > 0) {
            processLine(buffer.toString());
            buffer.setLength(0);
        }
    }

    public boolean isWritingFile() {
        return false;
    }

    public String getCurrentFilePath() {
        return null;
    }

    /**
     * Escribe un archivo mostrando cada línea en el chat (wrapper para tools).
     */
    public static void writeFileWithPreview(String pathStr, String content) throws IOException {
        writeFileWithPreview(pathStr, content, null);
    }

    public static void writeFileWithPreview(String pathStr, String content, Integer streamedLines) throws IOException {
        Path filePath = Paths.get(pathStr);

        if (streamedLines == null) {
            DiffLineNotifier.notify("@@@ " + filePath.getFileName() + " (crear) @@@");
            String[] lines = content.split("\n", -1);
            for (int i = 0; i < lines.length; i++) {
                DiffLineNotifier.notify(String.format("%3d │+ %s", i + 1, lines[i]));
            }
            Files.createDirectories(parentOrCurrent(filePath));
            Files.writeString(filePath, content);
            DiffLineNotifier.notify(String.format("─── +%d ✓ %s guardado ───", lines.length, pathStr));
            return;
        }

        Files.createDirectories(parentOrCurrent(filePath));
        Files.writeString(filePath, content);
        DiffLineNotifier.notify(String.format("─── +%d ✓ %s guardado ───", streamedLines, pathStr));
    }

    private static Path parentOrCurrent(Path filePath) {
        Path parent = filePath.getParent();
        return parent != null ? parent : Paths.get(".");
    }
}
