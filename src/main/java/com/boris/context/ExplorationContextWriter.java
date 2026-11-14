package com.boris.context;

import com.boris.context.ExplorationContextWriterException;
import com.boris.exceptions.BorisException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Escribe un registro de exploración local en {PROYECTO}/.boris/{SESSION_ID}/exploration.md.
 * El archivo es append-only y sobrevive a turnos cortados (abort, guards, límites).
 * El agente lee este bloque al inicio de cada turno para no re-explorar rutas ya conocidas.
 */
public final class ExplorationContextWriter {

    private static final String FILE_HEADER = "# Memoria de exploración — Sesión ";

    /** Tools que se registran como exploración del proyecto local. */
    public static final Set<String> EXPLORATION_TOOLS = Set.of("list_files", "batch_read_files");

    /** Máximo de caracteres del extracto de resultado por entrada. */
    public static final int MAX_ENTRY_RESULT_CHARS = 500;

    /** Máximo de entradas a inyectar en el prompt por turno. */
    public static final int MAX_ENTRIES_PER_INJECTION = 10;

    /** Máximo de caracteres totales del bloque inyectado. */
    public static final int MAX_INJECTION_CHARS = 3000;

    private ExplorationContextWriter() {
    }

    /**
     * Registra una entrada de exploración.
     *
     * @param toolName nombre de la herramienta
     * @param args     entrada de la herramienta (JSON)
     * @param result   resultado crudo de la herramienta
     * @throws ExplorationContextWriterException si no puede escribir
     */
    public static synchronized void append(String toolName, String args, String result) {
        if (!isExplorationTool(toolName)) {
            return;
        }

        String sessionId;
        try {
            sessionId = SearchSessionContext.requireSessionId();
        } catch (BorisException e) {
            throw new ExplorationContextWriterException(e.getMessage(), e);
        }
        String projectPath = ProjectContext.getProjectPath();
        if (projectPath == null || projectPath.isBlank()) {
            throw new ExplorationContextWriterException(
                "La sesión no tiene project path seteado. Declará la ruta del proyecto en el mensaje para que la exploración quede registrada en exploration.md.");
        }

        Path file = SessionDirectoryResolver.explorationFile();
        try {
            Files.createDirectories(file.getParent());
        } catch (Exception e) {
            throw new ExplorationContextWriterException("No se pudo crear el directorio de sesión", e);
        }

        boolean writeHeader = !Files.exists(file);
        if (!writeHeader) {
            try {
                writeHeader = Files.size(file) == 0;
            } catch (IOException e) {
                writeHeader = true;
            }
        }

        String sessionHeader = FILE_HEADER + sessionId + "\n\n";
        String extracto = truncarResultado(result);
        String entry = String.format(
            "[%s] TOOL %s args=%s → %s%n",
            LocalDateTime.now().toString(),
            toolName,
            args != null ? args : "",
            extracto
        );

        String content = (writeHeader ? sessionHeader : "") + entry;

        try {
            Files.writeString(file, content, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (Exception e) {
            throw new ExplorationContextWriterException("No se pudo escribir el registro de exploración", e);
        }
    }

    /**
     * Lee el bloque de memoria para inyección en el prompt.
     * Devuelve las últimas {@link #MAX_ENTRIES_PER_INJECTION} entradas sin exceder
     * {@link #MAX_INJECTION_CHARS} caracteres totales.
     *
     * @return bloque formateado con header/footer, o bloque vacío si el archivo no existe
     */
    public static synchronized String readMemoryBlock() {
        String sessionId;
        try {
            sessionId = SearchSessionContext.requireSessionId();
        } catch (BorisException e) {
            throw new ExplorationContextWriterException(e.getMessage(), e);
        }
        String projectPath = ProjectContext.getProjectPath();
        if (projectPath == null || projectPath.isBlank()) {
            throw new ExplorationContextWriterException(
                "La sesión no tiene project path seteado.");
        }

        Path file = SessionDirectoryResolver.explorationFile();
        if (!Files.exists(file)) {
            return "";
        }

        List<String> lines;
        try {
            lines = Files.readAllLines(file);
        } catch (IOException e) {
            throw new ExplorationContextWriterException("No se pudo leer el registro de exploración", e);
        }

        if (lines.isEmpty()) {
            return "";
        }

        // Tomar las últimas MAX_ENTRIES_PER_INJECTION entradas (cada entrada es una línea)
        int start = Math.max(0, lines.size() - MAX_ENTRIES_PER_INJECTION);
        List<String> entradas = lines.subList(start, lines.size());

        // Construir bloque respetando MAX_INJECTION_CHARS
        StringBuilder sb = new StringBuilder();
        sb.append("===== EXPLORATION MEMORY (ya explorado en esta sesión, no repetir) =====\n");
        int charsUsed = 0;
        for (String linea : entradas) {
            if (charsUsed + linea.length() + 1 > MAX_INJECTION_CHARS) {
                break;
            }
            sb.append(linea).append("\n");
            charsUsed += linea.length() + 1;
        }
        sb.append("===== END EXPLORATION MEMORY =====\n");

        return sb.toString();
    }

    /**
     * Indica si una herramienta debe registrarse como exploración.
     */
    public static boolean isExplorationTool(String toolName) {
        return EXPLORATION_TOOLS.contains(toolName);
    }

    /**
     * Trunca el resultado de una herramienta a MAX_ENTRY_RESULT_CHARS.
     */
    private static String truncarResultado(String result) {
        if (result == null) {
            return "";
        }
        if (result.length() <= MAX_ENTRY_RESULT_CHARS) {
            return result;
        }
        return result.substring(0, MAX_ENTRY_RESULT_CHARS)
            + "\n... [truncated, " + result.length() + " chars total]";
    }
}
