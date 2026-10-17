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
 * Detecta bloques con formato:
 *
 * ```java:src/MiClase.java
 * contenido...
 * ```
 *
 * FLUJO:
 * 1. Muestra cada línea en el chat conforme llega (rápido, sin I/O)
 * 2. Acumula el contenido en memoria
 * 3. Guarda TODO al final cuando cierra el bloque ```
 */
public class StreamingFileWriter {

    // Patrón: ```lenguaje:ruta/archivo.ext o ```ruta/archivo.ext
    private static final Pattern BLOCK_START = Pattern.compile("^```(\\w+)?:?(.+\\.[a-zA-Z0-9]+)$");
    private static final Pattern BLOCK_END = Pattern.compile("^```\\s*$");

    private StringBuilder buffer = new StringBuilder();
    private String currentFilePath = null;
    private String currentLanguage = null;
    private List<String> contentLines = new ArrayList<>();
    private int lineNumber = 0;
    private boolean inCodeBlock = false;

    /**
     * Procesa un chunk de texto del modelo.
     * Puede contener líneas parciales, completas, o múltiples líneas.
     *
     * @param chunk Texto recibido del modelo
     * @return Texto que NO es código (para mostrar como chat normal)
     */
    public String processChunk(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return "";
        }

        buffer.append(chunk);
        StringBuilder nonCodeOutput = new StringBuilder();

        // Procesar líneas completas (tienen \n)
        int newlineIdx;
        while ((newlineIdx = buffer.indexOf("\n")) != -1) {
            String line = buffer.substring(0, newlineIdx);
            buffer.delete(0, newlineIdx + 1);

            String result = processLine(line);
            if (result != null) {
                nonCodeOutput.append(result).append("\n");
            }
        }

        // Si NO estamos en bloque de código, devolver buffer parcial inmediatamente
        // Esto permite que texto como "Hey" se muestre sin esperar newline
        if (!inCodeBlock && buffer.length() > 0) {
            // Verificar si el buffer podría ser inicio de bloque de código
            String pending = buffer.toString();
            if (!pending.startsWith("`")) {
                nonCodeOutput.append(pending);
                buffer.setLength(0);
            }
            // Si empieza con `, esperar más datos para ver si es ```lang:path
        }

        return nonCodeOutput.toString();
    }

    /**
     * Procesa una línea completa.
     *
     * @return La línea si es texto normal, null si es código (ya fue mostrado)
     */
    private String processLine(String line) {
        // ¿Inicio de bloque de código con archivo?
        Matcher startMatcher = BLOCK_START.matcher(line.trim());
        if (startMatcher.matches()) {
            String lang = startMatcher.group(1);
            String path = startMatcher.group(2);
            startFileBlock(path, lang);
            return null;
        }

        // ¿Fin de bloque de código?
        if (inCodeBlock && BLOCK_END.matcher(line.trim()).matches()) {
            endFileBlock();
            return null;
        }

        // ¿Dentro de bloque de código?
        if (inCodeBlock) {
            accumulateLine(line);
            return null;
        }

        // Texto normal
        return line;
    }

    private void startFileBlock(String path, String language) {
        // Cerrar archivo anterior si existe
        if (inCodeBlock) {
            endFileBlock();
        }

        // Resolver ruta relativa usando el proyecto actual
        String resolvedPath = ProjectContext.resolveRelativePath(path);

        currentFilePath = resolvedPath;
        currentLanguage = language;
        lineNumber = 0;
        contentLines.clear();
        inCodeBlock = true;

        // Notificar UI - inicio de archivo
        Path filePath = Paths.get(resolvedPath);
        DiffLineNotifier.notify("@@@ " + filePath.getFileName() + " @@@");
    }

    /**
     * Acumula una línea en memoria y la muestra en el chat.
     * NO escribe al disco todavía.
     */
    private void accumulateLine(String line) {
        lineNumber++;
        contentLines.add(line);

        // Notificar UI para mostrar la línea (esto es instantáneo, sin I/O)
        DiffLineNotifier.notify(String.format("%3d │+ %s", lineNumber, line));
    }

    /**
     * Finaliza el bloque y guarda TODO el contenido al archivo de una sola vez.
     */
    private void endFileBlock() {
        if (currentFilePath != null && !contentLines.isEmpty()) {
            try {
                Path filePath = Paths.get(currentFilePath);
                Files.createDirectories(filePath.getParent());

                // Escribir TODO el contenido de una sola vez
                String content = String.join("\n", contentLines);
                if (!content.endsWith("\n")) {
                    content += "\n";
                }
                Files.writeString(filePath, content);

                DiffLineNotifier.notify(String.format("─── +%d ✓ %s guardado ───", lineNumber, currentFilePath));

            } catch (IOException e) {
                DiffLineNotifier.notify("⚠ Error guardando archivo: " + e.getMessage());
            }
        }

        currentFilePath = null;
        currentLanguage = null;
        contentLines.clear();
        lineNumber = 0;
        inCodeBlock = false;
    }

    /**
     * Llamar al final del streaming para cerrar archivos pendientes.
     */
    public void finish() {
        // Procesar buffer restante
        if (buffer.length() > 0) {
            processLine(buffer.toString());
            buffer.setLength(0);
        }

        // Guardar archivo si quedó abierto
        if (inCodeBlock) {
            endFileBlock();
        }
    }

    /**
     * Resetear estado para nuevo mensaje.
     */
    public void reset() {
        buffer.setLength(0);
        currentFilePath = null;
        currentLanguage = null;
        contentLines.clear();
        lineNumber = 0;
        inCodeBlock = false;
    }

    public boolean isWritingFile() {
        return inCodeBlock;
    }

    public String getCurrentFilePath() {
        return currentFilePath;
    }
}
