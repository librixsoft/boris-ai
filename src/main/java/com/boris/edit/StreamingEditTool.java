package com.boris.edit;

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

public class StreamingEditTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static LineByLineEditor activeEditor;
    private static Path projectRoot;

    public static void setProjectRoot(Path root) {
        projectRoot = root;
    }

    public static ToolDefinition streaming_edit_start() {
        var pathProp = Map.of("type", "string", "description", "Ruta del archivo a crear/modificar");
        var modeProp = Map.of("type", "string", "description", "Modo: create, modify, o append");

        var properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProp);
        properties.put("mode", modeProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new String[]{"path", "mode"});

        return ToolDefinition.of(
            "streaming_edit_start",
            "Inicia una sesión de edición línea por línea. Después usa streaming_edit_line para cada línea.",
            schema
        );
    }

    public static String streaming_edit_start(Map<String, Object> args) {
        String pathStr = (String) args.get("path");
        String mode = (String) args.getOrDefault("mode", "create");

        if (pathStr == null || pathStr.isBlank()) {
            return formatResponse(false, "Error: path es requerido", null);
        }

        try {
            Path contextFile = findContextFile(pathStr);
            activeEditor = new LineByLineEditor(contextFile);

            Path filePath = Paths.get(pathStr);
            EditSession session;

            switch (mode.toLowerCase()) {
                case "create" -> session = activeEditor.startCreate(filePath);
                case "modify" -> session = activeEditor.startModify(filePath);
                case "append" -> session = activeEditor.startAppend(filePath);
                default -> {
                    return formatResponse(false, "Modo inválido: " + mode, null);
                }
            }

            // Mostrar header del diff en UI (mismo formato que EditTool)
            DiffLineNotifier.notify("@@@ " + filePath.getFileName() + " @@@");

            return formatResponse(true,
                "Sesión iniciada para " + pathStr + " (modo: " + mode + ")",
                "Envía líneas una por una con streaming_edit_line"
            );

        } catch (IOException e) {
            return formatResponse(false, "Error iniciando sesión: " + e.getMessage(), null);
        }
    }

    public static ToolDefinition streaming_edit_line() {
        var lineProp = Map.of("type", "string", "description", "Línea de código a agregar");

        var properties = new LinkedHashMap<String, Object>();
        properties.put("line", lineProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new String[]{"line"});

        return ToolDefinition.of(
            "streaming_edit_line",
            "Agrega una línea al archivo en edición. Retorna OK o ERROR con sugerencias.",
            schema
        );
    }

    public static String streaming_edit_line(Map<String, Object> args) {
        if (activeEditor == null || !activeEditor.hasActiveSession()) {
            return formatResponse(false,
                "No hay sesión activa. Usa streaming_edit_start primero.",
                null
            );
        }

        String line = (String) args.getOrDefault("line", "");

        LineByLineEditor.LineResult result = activeEditor.processLine(line);

        if (result.isOk()) {
            EditSession session = activeEditor.getCurrentSession();
            int lineNum = session.getCurrentLineNumber() - 1;

            // Formato igual que EditTool: "  N │+ contenido"
            DiffLineNotifier.notify(String.format("%3d │+ %s", lineNum, line));

            return formatResponse(true,
                "✓ OK",
                String.format("Línea %d agregada", lineNum)
            );
        } else if (result.needsCorrection()) {
            EditSession session = activeEditor.getCurrentSession();
            int lineNum = session.getCurrentLineNumber();

            // Error de validación
            DiffLineNotifier.notify(String.format("%3d │⚠ %s", lineNum, truncate(line, 50)));
            DiffLineNotifier.notify("    └─ ERROR: " + result.message());
            if (!result.suggestions().isEmpty()) {
                DiffLineNotifier.notify("    └─ Usa: " + String.join(", ", result.suggestions()));
            }

            StringBuilder msg = new StringBuilder();
            msg.append("❌ ERROR: ").append(result.message());
            if (!result.suggestions().isEmpty()) {
                msg.append("\nAlternativas: ").append(String.join(", ", result.suggestions()));
            }
            msg.append("\nUsa streaming_edit_correct para corregir");
            return formatResponse(false, msg.toString(), null);
        } else {
            return formatResponse(false, result.message(), null);
        }
    }

    public static ToolDefinition streaming_edit_correct() {
        var lineProp = Map.of("type", "string", "description", "Línea corregida");

        var properties = new LinkedHashMap<String, Object>();
        properties.put("line", lineProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new String[]{"line"});

        return ToolDefinition.of(
            "streaming_edit_correct",
            "Corrige la última línea que tuvo error de validación.",
            schema
        );
    }

    public static String streaming_edit_correct(Map<String, Object> args) {
        if (activeEditor == null || !activeEditor.hasActiveSession()) {
            return formatResponse(false, "No hay sesión activa", null);
        }

        String line = (String) args.getOrDefault("line", "");
        LineByLineEditor.LineResult result = activeEditor.correctLine(line);

        if (result.isOk()) {
            EditSession session = activeEditor.getCurrentSession();
            int lineNum = session.getCurrentLineNumber() - 1;

            // Formato igual que EditTool
            DiffLineNotifier.notify(String.format("%3d │+ %s", lineNum, line));

            return formatResponse(true, "✓ Corregido OK", null);
        } else if (result.needsCorrection()) {
            // Mostrar error persistente
            DiffLineNotifier.notify("    └─ Aún hay error: " + result.message());

            StringBuilder msg = new StringBuilder();
            msg.append("❌ Aún hay error: ").append(result.message());
            if (!result.suggestions().isEmpty()) {
                msg.append("\nAlternativas: ").append(String.join(", ", result.suggestions()));
            }
            return formatResponse(false, msg.toString(), null);
        } else {
            return formatResponse(false, result.message(), null);
        }
    }

    public static ToolDefinition streaming_edit_finish() {
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", Map.of());

        return ToolDefinition.of(
            "streaming_edit_finish",
            "Finaliza la sesión de edición y guarda el archivo.",
            schema
        );
    }

    public static String streaming_edit_finish(Map<String, Object> args) {
        if (activeEditor == null || !activeEditor.hasActiveSession()) {
            return formatResponse(false, "No hay sesión activa", null);
        }

        try {
            EditSession session = activeEditor.getCurrentSession();
            LineByLineEditor.FinishResult result = activeEditor.finish();

            if (result.success()) {
                // Mostrar footer del diff (mismo formato que EditTool)
                DiffLineNotifier.notify(String.format("─── +%d -%d ───", result.totalLines(), 0));

                activeEditor = null;
                return formatResponse(true,
                    "✓ Archivo guardado: " + result.session().getFilePath(),
                    ""
                );
            } else {
                return formatResponse(false, result.message(), null);
            }
        } catch (IOException e) {
            return formatResponse(false, "Error guardando: " + e.getMessage(), null);
        }
    }

    public static ToolDefinition streaming_edit_abort() {
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", Map.of());

        return ToolDefinition.of(
            "streaming_edit_abort",
            "Cancela la sesión de edición sin guardar.",
            schema
        );
    }

    public static String streaming_edit_abort(Map<String, Object> args) {
        if (activeEditor != null) {
            activeEditor.abort();
            activeEditor = null;
        }
        return formatResponse(true, "Sesión cancelada", null);
    }

    private static Path findContextFile(String filePath) {
        Path file = Paths.get(filePath).toAbsolutePath();
        Path current = file.getParent();

        while (current != null) {
            Path contextFile = current.resolve(".boris/CONTEXT.md");
            if (Files.exists(contextFile)) {
                return contextFile;
            }

            if (Files.exists(current.resolve("pom.xml")) ||
                Files.exists(current.resolve("build.gradle")) ||
                Files.exists(current.resolve("package.json"))) {
                return current.resolve(".boris/CONTEXT.md");
            }

            current = current.getParent();
        }

        if (projectRoot != null) {
            return projectRoot.resolve(".boris/CONTEXT.md");
        }

        return null;
    }

    private static String formatResponse(boolean success, String message, String detail) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", success);
            node.put("message", message);
            if (detail != null && !detail.isEmpty()) {
                node.put("detail", detail);
            }
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            return "{\"success\":" + success + ",\"message\":\"" + message + "\"}";
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}
