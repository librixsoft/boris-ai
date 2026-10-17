package com.boris.edit;

import com.boris.tooling.DiffLineNotifier;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * StreamingFileWriter - Estilo Aider
 *
 * Parsea texto del modelo en streaming y escribe archivos conforme llegan.
 * Detecta bloques con formato:
 *
 * ```java:src/MiClase.java
 * contenido...
 * ```
 *
 * Escribe cada línea al archivo conforme llega, mostrando en UI.
 */
public class StreamingFileWriter {

    // Patrón: ```lenguaje:ruta/archivo.ext o ```ruta/archivo.ext
    private static final Pattern BLOCK_START = Pattern.compile("^```(\\w+)?:?(.+\\.[a-zA-Z0-9]+)$");
    private static final Pattern BLOCK_END = Pattern.compile("^```\\s*$");

    private StringBuilder buffer = new StringBuilder();
    private String currentFilePath = null;
    private String currentLanguage = null;
    private BufferedWriter currentWriter = null;
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
     * @return La línea si es texto normal, null si es código (ya fue escrito)
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
            writeLineToFile(line);
            return null;
        }

        // Texto normal
        return line;
    }

    private void startFileBlock(String path, String language) {
        try {
            // Cerrar archivo anterior si existe
            if (currentWriter != null) {
                endFileBlock();
            }

            currentFilePath = path;
            currentLanguage = language;
            lineNumber = 0;
            inCodeBlock = true;

            Path filePath = Paths.get(path);
            Files.createDirectories(filePath.getParent());
            currentWriter = Files.newBufferedWriter(filePath);

            // Notificar UI
            DiffLineNotifier.notify("@@@ " + filePath.getFileName() + " @@@");

        } catch (IOException e) {
            DiffLineNotifier.notify("⚠ Error creando archivo: " + e.getMessage());
            inCodeBlock = false;
            currentWriter = null;
        }
    }

    private void writeLineToFile(String line) {
        if (currentWriter == null) {
            return;
        }

        try {
            lineNumber++;
            currentWriter.write(line);
            currentWriter.newLine();
            currentWriter.flush(); // Flush inmediato para ver progreso

            // Notificar UI
            DiffLineNotifier.notify(String.format("%3d │+ %s", lineNumber, line));

        } catch (IOException e) {
            DiffLineNotifier.notify("⚠ Error escribiendo línea: " + e.getMessage());
        }
    }

    private void endFileBlock() {
        if (currentWriter != null) {
            try {
                currentWriter.close();
                DiffLineNotifier.notify(String.format("─── +%d ✓ %s ───", lineNumber, currentFilePath));
            } catch (IOException e) {
                DiffLineNotifier.notify("⚠ Error cerrando archivo: " + e.getMessage());
            }
        }

        currentWriter = null;
        currentFilePath = null;
        currentLanguage = null;
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

        // Cerrar archivo si quedó abierto
        if (inCodeBlock) {
            endFileBlock();
        }
    }

    /**
     * Resetear estado para nuevo mensaje.
     */
    public void reset() {
        if (currentWriter != null) {
            try {
                currentWriter.close();
            } catch (IOException ignored) {}
        }
        buffer.setLength(0);
        currentWriter = null;
        currentFilePath = null;
        currentLanguage = null;
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
