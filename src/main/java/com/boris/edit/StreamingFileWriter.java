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
 * Parsea texto del modelo en streaming y muestra el código en el chat.
 * Soporta formato para CREAR archivo nuevo:
 * ```java:src/MiClase.java
 * contenido completo...
 * ```
 *
 * FLUJO:
 * 1. Muestra cada línea en el chat conforme llega (rápido, sin I/O)
 * 2. Acumula el contenido en memoria
 * 3. Guarda el archivo al final cuando cierra el bloque ```
 */
public class StreamingFileWriter {

    // Patrón para CREAR: ```lenguaje:ruta/archivo.ext
    private static final Pattern CREATE_BLOCK_START = Pattern.compile("^```(\\w+):(.+\\.[a-zA-Z0-9]+)$");
    private static final Pattern BLOCK_END = Pattern.compile("^```\\s*$");

    // Patrones para detectar XML de herramientas (el modelo a veces mezcla formatos)
    private static final Pattern TOOL_XML_PATTERN = Pattern.compile(
        "^\\s*</?(invoke|tool_call|tool_calls|function_calls|function_call|antml:[a-zA-Z0-9_-]+)\\b.*>\\s*$"
    );

    // Patrón para detectar tags de pensamiento y evitar que se filtren en la interfaz
    private static final Pattern THINK_TAG_PATTERN = Pattern.compile(
        "^\\s*`*</?(?:think|thinking|thought|reasoning)\\b.*?>?\\s*`*$",
        Pattern.CASE_INSENSITIVE
    );

    private StringBuilder buffer = new StringBuilder();
    private String currentFilePath = null;
    private String currentLanguage = null;
    private List<String> contentLines = new ArrayList<>();
    private int lineNumber = 0;
    private boolean inCodeBlock = false;
    private boolean inThinkingBlock = false;
    private FileSaver saveFunction;

    public interface FileSaver {
        void save(String path, String content, int shownLines);
    }

    public void setSaveFunction(FileSaver saver) {
        this.saveFunction = saver;
    }

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

        if (!inCodeBlock && buffer.length() > 0) {
            String pending = buffer.toString();
            if (pending.matches("(?i).*`*</?(?:think|thinking|thought|reasoning)>`*.*")) {
                buffer.setLength(0);
            } else if (looksLikePartialThinkTag(pending)) {
                // No emitir ni limpiar: retener el fragmento hasta que llegue más texto
                // y se pueda confirmar si es un tag de thinking partido entre chunks.
            } else if (!pending.startsWith("`")) {
                nonCodeOutput.append(pending);
                buffer.setLength(0);
            }
        }

        return nonCodeOutput.toString();
    }

    /**
     * Detecta si el texto pendiente podría ser el inicio de un tag de thinking
     * que fue partido entre chunks del stream. En ese caso se retiene en el buffer
     * hasta que llegue más texto para confirmar si es un tag completo o texto normal.
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
        // Detectar apertura de bloque thinking
        if (trimmed.matches("(?i)^`*<(?:think|thinking|thought|reasoning)\\b.*")) {
            inThinkingBlock = true;
            // Si el tag de cierre está en la misma línea, verificar si todo el contenido está dentro
            if (trimmed.matches("(?i).*</(?:think|thinking|thought|reasoning)>`*$")) {
                inThinkingBlock = false;
                return null; // Línea completa con opening y closing tags, filtrar todo
            }
            // Línea solo con opening tag, filtrar pero mantener estado inThinkingBlock
            return null;
        }
        
        // Dentro de bloque thinking: filtrar todo hasta encontrar cierre
        if (inThinkingBlock) {
            if (trimmed.matches("(?i).*</(?:think|thinking|thought|reasoning)>`*$")) {
                inThinkingBlock = false;
            }
            return null; // Filtrar todo mientras esté en thinking block
        }
        
        // Detectar tag de cierre sin estar en bloque (caso de tag huérfano)
        if (trimmed.matches("(?i)^`*</(?:think|thinking|thought|reasoning)>`*$") || THINK_TAG_PATTERN.matcher(trimmed).matches()) {
            inThinkingBlock = false;
            return null;
        }

        // ¿Inicio de bloque CREATE?
        Matcher createMatcher = CREATE_BLOCK_START.matcher(trimmed);
        if (createMatcher.matches()) {
            String lang = createMatcher.group(1);
            String path = createMatcher.group(2);
            startCreateBlock(path, lang);
            return null;
        }

        // ¿Fin de bloque?
        if (inCodeBlock && BLOCK_END.matcher(trimmed).matches()) {
            endBlock();
            return null;
        }

        // ¿XML de herramientas? Cerrar bloque
        if (inCodeBlock && TOOL_XML_PATTERN.matcher(line).matches()) {
            endBlock();
            return null;
        }

        // Dentro de bloque CREATE
        if (inCodeBlock) {
            accumulateCreateLine(line);
            return null;
        }

        // XML de herramientas fuera de bloque: filtrar para no mostrar ruido en el chat
        if (TOOL_XML_PATTERN.matcher(trimmed).matches()) {
            return null;
        }

        return line;
    }

    private void startCreateBlock(String path, String language) {
        if (inCodeBlock) {
            endBlock();
        }

        String sanitizedPath = com.boris.tooling.tool.WriteTool.sanitizeTargetPath(path);
        String resolvedPath = ProjectContext.resolveRelativePath(sanitizedPath);
        currentFilePath = resolvedPath;
        currentLanguage = language;
        lineNumber = 0;
        contentLines.clear();
        inCodeBlock = true;

        Path filePath = Paths.get(resolvedPath);
        DiffLineNotifier.notify("@@@ " + filePath.getFileName() + " (crear) @@@");
    }

    private void accumulateCreateLine(String line) {
        lineNumber++;
        contentLines.add(line);
        DiffLineNotifier.notify(String.format("%3d │+ %s", lineNumber, line));
    }

    private void endBlock() {
        if (currentFilePath == null) {
            resetState();
            return;
        }

        saveNewFile();
        resetState();
    }

    private void saveNewFile() {
        if (contentLines.isEmpty()) {
            return;
        }

        String targetPath = currentFilePath;
        String content = String.join("\n", contentLines);
        if (!content.endsWith("\n")) {
            content += "\n";
        }
        int shownLines = lineNumber;

        if (saveFunction != null) {
            try {
                saveFunction.save(targetPath, content, shownLines);
            } catch (Exception e) {
                DiffLineNotifier.notify("⚠ Error guardando " + targetPath + ": " + e.getMessage());
            }
            return;
        }

        try {
            Path filePath = Paths.get(targetPath);
            Files.createDirectories(parentOrCurrent(filePath));
            Files.writeString(filePath, content);

            DiffLineNotifier.notify(String.format("─── +%d ✓ %s guardado ───", shownLines, targetPath));

        } catch (IOException e) {
            DiffLineNotifier.notify("⚠ Error guardando: " + e.getMessage());
        }
    }

    private static Path parentOrCurrent(Path filePath) {
        Path parent = filePath.getParent();
        return parent != null ? parent : Paths.get(".");
    }

    private void resetState() {
        currentFilePath = null;
        currentLanguage = null;
        contentLines.clear();
        lineNumber = 0;
        inCodeBlock = false;
    }

    private void resetFileState() {
        currentFilePath = null;
        currentLanguage = null;
        contentLines.clear();
        lineNumber = 0;
        inCodeBlock = false;
    }

    public void reset() {
        buffer.setLength(0);
        resetFileState();
        inThinkingBlock = false; // Solo se resetea en reset completo (inicio de nueva conversación)
    }

    public void finish() {
        if (buffer.length() > 0) {
            processLine(buffer.toString());
            buffer.setLength(0);
        }

        if (inCodeBlock) {
            endBlock();
        }
    }

    public boolean isWritingFile() {
        return inCodeBlock;
    }

    public String getCurrentFilePath() {
        return currentFilePath;
    }

    /**
     * Escribe un archivo mostrando cada línea en el chat (wrapper para tools).
     * Simula el comportamiento de streaming con contenido completo.
     */
    public static void writeFileWithPreview(String pathStr, String content) throws IOException {
        writeFileWithPreview(pathStr, content, null);
    }

    /**
     * Si streamedLines != null, el contenido ya fue mostrado en vivo durante la generación,
     * por lo que solo se valida la escritura y se emite el footer con las líneas contadas.
     */
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
}
