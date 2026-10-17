package com.boris.edit;

import com.boris.context.ProjectContext;
import com.boris.tooling.DiffLineNotifier;
import com.boris.tooling.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Edicion linea por linea con visualizacion en tiempo real.
 * Cada linea se muestra en el chat mientras se escribe.
 *
 * Flujo:
 * 1. streaming_line_start(path) - inicia archivo
 * 2. streaming_line_write(line) - escribe una linea (se muestra en chat)
 * 3. Repetir hasta terminar
 * 4. streaming_line_end() - cierra archivo
 */
public class StreamingLineEditTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static Path currentFile;
    private static int currentLine;

    public static ToolDefinition streaming_line_start() {
        var pathProp = Map.of("type", "string", "description", "Ruta del archivo a crear/editar");

        var properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new String[]{"path"});

        return ToolDefinition.of(
            "streaming_line_start",
            "Inicia la escritura de un archivo linea por linea.",
            schema
        );
    }

    public static String streaming_line_start(Map<String, Object> args) {
        String pathStr = (String) args.get("path");

        if (pathStr == null || pathStr.isBlank()) {
            return error("path es requerido");
        }

        try {
            String resolvedPath = ProjectContext.resolveRelativePath(pathStr);
            currentFile = Paths.get(resolvedPath).toAbsolutePath();
            currentLine = 0;

            Files.createDirectories(currentFile.getParent());
            Files.writeString(currentFile, "");

            DiffLineNotifier.notify("@@@ " + currentFile.getFileName() + " @@@");

            return success("Archivo iniciado: " + currentFile.getFileName());

        } catch (IOException e) {
            return error("Error iniciando archivo: " + e.getMessage());
        }
    }

    public static ToolDefinition streaming_line_write() {
        var lineProp = Map.of("type", "string", "description", "Linea de codigo a escribir");

        var properties = new LinkedHashMap<String, Object>();
        properties.put("line", lineProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new String[]{"line"});

        return ToolDefinition.of(
            "streaming_line_write",
            "Escribe una linea de codigo. Se muestra en el chat en tiempo real.",
            schema
        );
    }

    public static String streaming_line_write(Map<String, Object> args) {
        if (currentFile == null) {
            return error("No hay archivo abierto. Usa streaming_line_start primero.");
        }

        String line = (String) args.getOrDefault("line", "");
        currentLine++;

        try {
            Files.writeString(currentFile, line + "\n", StandardOpenOption.APPEND);
            DiffLineNotifier.notify(String.format("%3d │+ %s", currentLine, line));
            return success("OK");
        } catch (IOException e) {
            return error("Error escribiendo: " + e.getMessage());
        }
    }

    public static ToolDefinition streaming_line_end() {
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", Map.of());

        return ToolDefinition.of(
            "streaming_line_end",
            "Finaliza la escritura del archivo.",
            schema
        );
    }

    public static String streaming_line_end(Map<String, Object> args) {
        if (currentFile == null) {
            return error("No hay archivo abierto.");
        }

        String fileName = currentFile.getFileName().toString();
        int totalLines = currentLine;

        DiffLineNotifier.notify(String.format("─── +%d ✓ %s guardado ───", totalLines, fileName));

        currentFile = null;
        currentLine = 0;

        return success("Archivo guardado: " + fileName + " (" + totalLines + " lineas)");
    }

    private static String success(String message) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("success", true);
            root.put("message", message);
            return MAPPER.writeValueAsString(root);
        } catch (IOException e) {
            return "{\"success\":true,\"message\":\"" + message + "\"}";
        }
    }

    private static String error(String message) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("success", false);
            root.put("error", message);
            return MAPPER.writeValueAsString(root);
        } catch (IOException e) {
            return "{\"success\":false,\"error\":\"" + message + "\"}";
        }
    }
}
