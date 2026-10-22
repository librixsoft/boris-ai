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
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * BulkEditTool - Crea/reemplaza archivos con visualización línea por línea.
 */
public class BulkEditTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ToolDefinition bulk_edit() {
        var pathProp = Map.of("type", "string", "description", "Ruta del archivo a crear/reemplazar");
        var contentProp = Map.of("type", "string", "description", "Contenido COMPLETO del archivo");

        var properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProp);
        properties.put("content", contentProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new String[]{"path", "content"});

        return ToolDefinition.of(
            "bulk_edit",
            "Crea o reemplaza un archivo. Valida TODO antes de escribir. Si hay errores, reporta TODOS juntos para corregir en una sola iteración.",
            schema
        );
    }

    /**
     * Ejecuta la edición bulk con visualización en tiempo real.
     */
    public static String bulk_edit(Map<String, Object> args) {
        String pathStr = (String) args.get("path");
        String content = (String) args.getOrDefault("content", "");

        if (pathStr == null || pathStr.isBlank()) {
            return formatError("path es requerido");
        }

        // Resolver ruta relativa usando el proyecto actual
        pathStr = ProjectContext.resolveRelativePath(pathStr);

        try {
            Path filePath = Paths.get(pathStr);
            String[] lines = content.split("\n", -1);

            // Mostrar header INMEDIATAMENTE para feedback visual
            DiffLineNotifier.notify("@@@ " + filePath.getFileName() + " @@@");

            // Mostrar cada línea en tiempo real
            for (int i = 0; i < lines.length; i++) {
                DiffLineNotifier.notify(String.format("%3d │+ %s", i + 1, lines[i]));
            }

            // Escribir archivo
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content);
            DiffLineNotifier.notify(String.format("─── +%d ✓ guardado ───", lines.length));

            return formatSuccess(pathStr, lines.length);

        } catch (IOException e) {
            return formatError("Error IO: " + e.getMessage());
        }
    }

    private static String formatSuccess(String path, int totalLines) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("success", true);
            root.put("path", path);
            root.put("lines", totalLines);
            root.put("message", "Archivo creado: " + path + " (" + totalLines + " líneas)");
            return MAPPER.writeValueAsString(root);
        } catch (IOException e) {
            return "{\"success\":true,\"message\":\"Archivo creado: " + path + "\"}";
        }
    }

    private static String formatError(String message) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("success", false);
            root.put("message", message);
            return MAPPER.writeValueAsString(root);
        } catch (IOException e) {
            return "{\"success\":false,\"message\":\"" + message + "\"}";
        }
    }
}
