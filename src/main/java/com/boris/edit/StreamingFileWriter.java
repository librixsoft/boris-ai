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
 * StreamingFileWriter - Estilo Aider
 *
 * Parsea texto del modelo en streaming y muestra el código en el chat.
 * Soporta DOS formatos:
 *
 * 1. CREAR archivo nuevo:
 * ```java:src/MiClase.java
 * contenido completo...
 * ```
 *
 * 2. EDITAR archivo existente (SEARCH/REPLACE):
 * ```edit:src/MiClase.java
 * <<<<<<< SEARCH
 * código viejo
 * =======
 * código nuevo
 * >>>>>>> REPLACE
 * ```
 *
 * FLUJO:
 * 1. Muestra cada línea en el chat conforme llega (rápido, sin I/O)
 * 2. Acumula el contenido en memoria
 * 3. Guarda/aplica TODO al final cuando cierra el bloque ```
 */
public class StreamingFileWriter {

    // Patrón para CREAR: ```lenguaje:ruta/archivo.ext
    private static final Pattern CREATE_BLOCK_START = Pattern.compile("^```(\\w+):(.+\\.[a-zA-Z0-9]+)$");
    // Patrón para EDITAR: ```edit:ruta/archivo.ext
    private static final Pattern EDIT_BLOCK_START = Pattern.compile("^```edit:(.+\\.[a-zA-Z0-9]+)$");
    private static final Pattern BLOCK_END = Pattern.compile("^```\\s*$");

    // Marcadores SEARCH/REPLACE
    private static final String SEARCH_START = "<<<<<<< SEARCH";
    private static final String DIVIDER = "=======";
    private static final String REPLACE_END = ">>>>>>> REPLACE";

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
    private boolean isEditMode = false;
    private boolean inThinkingBlock = false;
    private FileSaver saveFunction;

    public interface FileSaver {
        void save(String path, String content, int shownLines);
    }

    public void setSaveFunction(FileSaver saver) {
        this.saveFunction = saver;
    }

    // Estado para SEARCH/REPLACE
    private boolean inSearchBlock = false;
    private boolean inReplaceBlock = false;
    private List<String> searchLines = new ArrayList<>();
    private List<String> replaceLines = new ArrayList<>();
    private List<SearchReplaceBlock> pendingEdits = new ArrayList<>();

    private record SearchReplaceBlock(String search, String replace) {}

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
            } else if (!pending.startsWith("`")) {
                nonCodeOutput.append(pending);
                buffer.setLength(0);
            }
        }

        return nonCodeOutput.toString();
    }

    private String processLine(String line) {
        String trimmed = line.trim();

        // Filtrar tags y contenido de pensamiento
        if (trimmed.matches("(?i)^`*<(?:think|thinking|thought|reasoning)\\b.*")) {
            inThinkingBlock = true;
            if (trimmed.matches("(?i).*</(?:think|thinking|thought|reasoning)>`*$")) {
                inThinkingBlock = false;
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

        // ¿Inicio de bloque EDIT?
        Matcher editMatcher = EDIT_BLOCK_START.matcher(trimmed);
        if (editMatcher.matches()) {
            String path = editMatcher.group(1);
            startEditBlock(path);
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

        // Dentro de bloque EDIT (SEARCH/REPLACE)
        if (inCodeBlock && isEditMode) {
            processEditLine(line);
            return null;
        }

        // Dentro de bloque CREATE
        if (inCodeBlock && !isEditMode) {
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
        isEditMode = false;

        Path filePath = Paths.get(resolvedPath);
        DiffLineNotifier.notify("@@@ " + filePath.getFileName() + " (crear) @@@");
    }

    private void startEditBlock(String path) {
        if (inCodeBlock) {
            endBlock();
        }

        String sanitizedPath = com.boris.tooling.tool.WriteTool.sanitizeTargetPath(path);
        String resolvedPath = ProjectContext.resolveRelativePath(sanitizedPath);
        currentFilePath = resolvedPath;
        lineNumber = 0;
        inCodeBlock = true;
        isEditMode = true;
        inSearchBlock = false;
        inReplaceBlock = false;
        searchLines.clear();
        replaceLines.clear();
        pendingEdits.clear();

        Path filePath = Paths.get(resolvedPath);
        DiffLineNotifier.notify("@@@ " + filePath.getFileName() + " (editar) @@@");
    }

    private void accumulateCreateLine(String line) {
        lineNumber++;
        contentLines.add(line);
        DiffLineNotifier.notify(String.format("%3d │+ %s", lineNumber, line));
    }

    private void processEditLine(String line) {
        String trimmed = line.trim();

        if (trimmed.equals(SEARCH_START)) {
            inSearchBlock = true;
            inReplaceBlock = false;
            searchLines.clear();
            DiffLineNotifier.notify("    │ SEARCH:");
            return;
        }

        if (trimmed.equals(DIVIDER)) {
            inSearchBlock = false;
            inReplaceBlock = true;
            replaceLines.clear();
            DiffLineNotifier.notify("    │ REPLACE:");
            return;
        }

        if (trimmed.equals(REPLACE_END)) {
            // Guardar el bloque SEARCH/REPLACE
            String search = String.join("\n", searchLines);
            String replace = String.join("\n", replaceLines);
            pendingEdits.add(new SearchReplaceBlock(search, replace));

            inSearchBlock = false;
            inReplaceBlock = false;
            searchLines.clear();
            replaceLines.clear();
            DiffLineNotifier.notify("    │ ─── bloque listo ───");
            return;
        }

        if (inSearchBlock) {
            searchLines.add(line);
            DiffLineNotifier.notify(String.format("    │- %s", line));
        } else if (inReplaceBlock) {
            replaceLines.add(line);
            DiffLineNotifier.notify(String.format("    │+ %s", line));
        }
    }

    private void endBlock() {
        if (currentFilePath == null) {
            resetState();
            return;
        }

        if (isEditMode) {
            applyEdits();
        } else {
            saveNewFile();
        }

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

        com.boris.tooling.CloneGateService.Verdict cloneVerdict =
            com.boris.tooling.CloneGateService.checkContent(content, true);
        if (cloneVerdict != null && cloneVerdict.blocked()) {
            DiffLineNotifier.notify("⛔ " + cloneVerdict.message());
            return;
        }

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

    private void applyEdits() {
        if (pendingEdits.isEmpty()) {
            DiffLineNotifier.notify("⚠ No hay ediciones pendientes");
            return;
        }

        try {
            Path filePath = Paths.get(currentFilePath);
            if (!Files.exists(filePath)) {
                DiffLineNotifier.notify("⚠ Archivo no existe: " + currentFilePath);
                return;
            }

            StringBuilder replaceAll = new StringBuilder();
            for (SearchReplaceBlock edit : pendingEdits) {
                if (replaceAll.length() > 0) {
                    replaceAll.append("\n");
                }
                replaceAll.append(edit.replace());
            }

            com.boris.tooling.CloneGateService.Verdict cloneVerdict =
                com.boris.tooling.CloneGateService.checkContent(replaceAll.toString(), false);
            if (cloneVerdict != null && cloneVerdict.blocked()) {
                DiffLineNotifier.notify("⛔ " + cloneVerdict.message());
                return;
            }

            String content = Files.readString(filePath);
            int applied = 0;

            for (SearchReplaceBlock edit : pendingEdits) {
                if (content.contains(edit.search)) {
                    content = content.replace(edit.search, edit.replace);
                    applied++;
                } else {
                    DiffLineNotifier.notify("⚠ No encontrado: " + edit.search.substring(0, Math.min(50, edit.search.length())) + "...");
                }
            }

            if (applied > 0) {
                Files.writeString(filePath, content);
                DiffLineNotifier.notify(String.format("─── %d ediciones ✓ %s guardado ───", applied, currentFilePath));
            }

        } catch (IOException e) {
            DiffLineNotifier.notify("⚠ Error aplicando ediciones: " + e.getMessage());
        }
    }

    private void resetState() {
        currentFilePath = null;
        currentLanguage = null;
        contentLines.clear();
        lineNumber = 0;
        inCodeBlock = false;
        isEditMode = false;
        inSearchBlock = false;
        inReplaceBlock = false;
        searchLines.clear();
        replaceLines.clear();
        pendingEdits.clear();
        inThinkingBlock = false;
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

    public void reset() {
        buffer.setLength(0);
        resetState();
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
