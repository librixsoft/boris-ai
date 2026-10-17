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
 * Edición línea por línea con validación just-in-time.
 * Cada línea se valida contra CONTEXT.md antes de escribirse.
 * Si hay error, se devuelve el contexto relevante para que el modelo corrija.
 *
 * Flujo:
 * 1. Modelo llama validated_line_start(path) - inicia archivo
 * 2. Modelo llama validated_line_write(line) - escribe una línea
 *    - Si válida: se escribe, responde OK
 *    - Si error: NO se escribe, responde con contexto para corregir
 * 3. Modelo corrige y llama validated_line_write(lineCorregida)
 * 4. Repetir hasta terminar
 * 5. Modelo llama validated_line_end() - cierra archivo
 */
public class ValidatedLineEditTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static SymbolCache symbolCache;
    private static LineContextResolver resolver;
    private static Path currentFile;
    private static int currentLine;
    private static int maxRetries = 3;
    private static int currentRetries = 0;

    // ==================== TOOL: START ====================

    public static ToolDefinition validated_line_start() {
        var pathProp = Map.of("type", "string", "description", "Ruta del archivo a crear/editar");

        var properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new String[]{"path"});

        return ToolDefinition.of(
            "validated_line_start",
            "Inicia la edición de un archivo. Cada línea se validará contra CONTEXT.md antes de escribirse.",
            schema
        );
    }

    public static String validated_line_start(Map<String, Object> args) {
        String pathStr = (String) args.get("path");

        if (pathStr == null || pathStr.isBlank()) {
            return error("path es requerido");
        }

        try {
            // Resolver ruta usando ProjectContext (respeta el proyecto del usuario)
            String resolvedPath = ProjectContext.resolveRelativePath(pathStr);
            currentFile = Paths.get(resolvedPath).toAbsolutePath();
            currentLine = 0;
            currentRetries = 0;

            // Crear directorios si no existen
            Files.createDirectories(currentFile.getParent());

            // Limpiar archivo si existe
            Files.writeString(currentFile, "");

            // Cargar contexto
            Path contextFile = findContextFile(pathStr);
            symbolCache = new SymbolCache();
            if (contextFile != null && Files.exists(contextFile)) {
                symbolCache.loadFromContext(contextFile);
                DiffLineNotifier.notify("📚 CONTEXT.md cargado: " + symbolCache.classCount() + " clases");
            } else {
                DiffLineNotifier.notify("⚠ Sin CONTEXT.md - validación limitada");
            }
            resolver = new LineContextResolver(symbolCache);

            // Mostrar header
            DiffLineNotifier.notify("@@@ " + currentFile.getFileName() + " @@@");

            return success("Archivo iniciado: " + currentFile.getFileName() + ". Envía líneas con validated_line_write.");

        } catch (IOException e) {
            return error("Error iniciando archivo: " + e.getMessage());
        }
    }

    // ==================== TOOL: WRITE LINE ====================

    public static ToolDefinition validated_line_write() {
        var lineProp = Map.of("type", "string", "description", "Línea de código a escribir");

        var properties = new LinkedHashMap<String, Object>();
        properties.put("line", lineProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new String[]{"line"});

        return ToolDefinition.of(
            "validated_line_write",
            "Escribe una línea de código. Se valida contra CONTEXT.md. Si hay error, responde con el contexto para corregir.",
            schema
        );
    }

    public static String validated_line_write(Map<String, Object> args) {
        if (currentFile == null) {
            return error("No hay archivo abierto. Usa validated_line_start primero.");
        }

        String line = (String) args.getOrDefault("line", "");
        currentLine++;

        // Resolver contexto para esta línea
        LineContextResolver.LineContext context = resolver.resolve(line);

        if (context.isValid()) {
            // Línea válida - escribir
            try {
                Files.writeString(currentFile, line + "\n", StandardOpenOption.APPEND);
                DiffLineNotifier.notify(String.format("%3d │+ %s", currentLine, line));
                currentRetries = 0;
                return success("OK");
            } catch (IOException e) {
                return error("Error escribiendo: " + e.getMessage());
            }
        } else {
            // Línea con errores - pedir corrección
            currentRetries++;
            currentLine--; // No avanzamos, esperamos corrección

            if (currentRetries > maxRetries) {
                // Demasiados intentos, escribir con warning
                try {
                    Files.writeString(currentFile, line + " // TODO: revisar\n", StandardOpenOption.APPEND);
                    currentLine++;
                    DiffLineNotifier.notify(String.format("%3d │⚠ %s // TODO: revisar", currentLine, truncate(line, 50)));
                    currentRetries = 0;
                    return warning("Máximo de reintentos. Línea escrita con TODO. Continúa con la siguiente.");
                } catch (IOException e) {
                    return error("Error escribiendo: " + e.getMessage());
                }
            }

            // Mostrar error en UI
            DiffLineNotifier.notify(String.format("%3d │✗ %s", currentLine + 1, truncate(line, 50)));

            // Generar prompt de corrección
            String correctionPrompt = resolver.buildCorrectionPrompt(context);

            return needsCorrection(correctionPrompt, currentRetries);
        }
    }

    // ==================== TOOL: END ====================

    public static ToolDefinition validated_line_end() {
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", Map.of());

        return ToolDefinition.of(
            "validated_line_end",
            "Finaliza la edición del archivo actual.",
            schema
        );
    }

    public static String validated_line_end(Map<String, Object> args) {
        if (currentFile == null) {
            return error("No hay archivo abierto.");
        }

        DiffLineNotifier.notify(String.format("─── +%d líneas ✓ guardado ───", currentLine));

        String result = success("Archivo completado: " + currentFile.getFileName() + " (" + currentLine + " líneas)");

        // Reset state
        currentFile = null;
        currentLine = 0;
        symbolCache = null;
        resolver = null;
        currentRetries = 0;

        return result;
    }

    // ==================== HELPERS ====================

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

        return null;
    }

    private static String success(String message) {
        return formatResponse("success", message, null);
    }

    private static String error(String message) {
        return formatResponse("error", message, null);
    }

    private static String warning(String message) {
        return formatResponse("warning", message, null);
    }

    private static String needsCorrection(String correctionPrompt, int attempt) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("status", "needs_correction");
            node.put("attempt", attempt);
            node.put("maxAttempts", maxRetries);
            node.put("prompt", correctionPrompt);
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            return "{\"status\":\"error\",\"message\":\"" + e.getMessage() + "\"}";
        }
    }

    private static String formatResponse(String status, String message, String detail) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("status", status);
            node.put("message", message);
            if (detail != null) {
                node.put("detail", detail);
            }
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            return "{\"status\":\"" + status + "\",\"message\":\"" + message + "\"}";
        }
    }

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }
}
