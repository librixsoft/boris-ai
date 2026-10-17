package com.boris.edit;

import com.boris.tooling.DiffLineNotifier;
import com.boris.tooling.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * BulkEditTool - Estilo Aider
 *
 * El modelo envía el contenido completo UNA vez.
 * Boris valida TODAS las líneas ANTES de escribir.
 * Si hay errores, reporta TODOS juntos (no uno por uno).
 * Solo escribe cuando TODO está válido.
 *
 * Ventaja: Reduce de N round-trips (uno por error) a máximo 2-3.
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
     * Muestra cada línea mientras valida (experiencia visual de streaming).
     * Si hay errores, reporta todos sin escribir.
     * Si todo está válido, escribe.
     */
    public static String bulk_edit(Map<String, Object> args) {
        String pathStr = (String) args.get("path");
        String content = (String) args.getOrDefault("content", "");

        System.out.println("[BulkEditTool] Iniciando bulk_edit para: " + pathStr);
        System.out.println("[BulkEditTool] Content length: " + (content != null ? content.length() : 0));

        if (pathStr == null || pathStr.isBlank()) {
            System.out.println("[BulkEditTool] ERROR: path es requerido");
            return formatError("path es requerido");
        }

        try {
            Path contextFile = findContextFile(pathStr);
            System.out.println("[BulkEditTool] Context file: " + contextFile);
            SymbolCache symbolCache = new SymbolCache();
            if (contextFile != null && Files.exists(contextFile)) {
                symbolCache.loadFromContext(contextFile);
                System.out.println("[BulkEditTool] SymbolCache loaded");
            }
            LineValidator validator = new LineValidator(symbolCache);

            Path filePath = Paths.get(pathStr);
            String[] lines = content.split("\n", -1);
            List<ValidationError> errors = new ArrayList<>();

            // Mostrar header INMEDIATAMENTE para feedback visual
            DiffLineNotifier.notify("@@@ " + filePath.getFileName() + " @@@");

            // FASE 1: Validar y MOSTRAR cada línea en tiempo real
            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                LineValidator.ValidationResult result = validator.validate(line);

                if (result.isValid()) {
                    // Línea OK - mostrar en verde (será escrita si todo pasa)
                    DiffLineNotifier.notify(String.format("%3d │+ %s", i + 1, line));
                } else {
                    // Línea con error - mostrar en rojo con detalles
                    DiffLineNotifier.notify(String.format("%3d │⚠ %s", i + 1, truncate(line, 50)));
                    DiffLineNotifier.notify("    └─ " + result.errorMessage());
                    if (result.hasSuggestions()) {
                        DiffLineNotifier.notify("    └─ Usa: " + String.join(", ", result.suggestions()));
                    }
                    errors.add(new ValidationError(
                        i + 1,
                        line,
                        result.errorMessage(),
                        result.suggestions()
                    ));
                }
            }

            // FASE 2: Si hay errores, NO escribir
            if (!errors.isEmpty()) {
                System.out.println("[BulkEditTool] ERRORES encontrados: " + errors.size());
                for (ValidationError err : errors) {
                    System.out.println("[BulkEditTool]   Línea " + err.lineNumber + ": " + err.message);
                }
                DiffLineNotifier.notify(String.format("─── ⚠ %d errores, NO guardado ───", errors.size()));
                return formatValidationErrors(pathStr, errors, lines.length);
            }

            // FASE 3: Todo válido -> Escribir archivo
            System.out.println("[BulkEditTool] Todo válido, escribiendo archivo...");
            Files.createDirectories(filePath.getParent());
            Files.writeString(filePath, content);
            System.out.println("[BulkEditTool] Archivo escrito: " + filePath);
            DiffLineNotifier.notify(String.format("─── +%d ✓ guardado ───", lines.length));

            return formatSuccess(pathStr, lines.length);

        } catch (IOException e) {
            System.out.println("[BulkEditTool] ERROR IO: " + e.getMessage());
            e.printStackTrace();
            return formatError("Error IO: " + e.getMessage());
        }
    }

    private static String formatValidationErrors(String path, List<ValidationError> errors, int totalLines) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("success", false);
            root.put("path", path);
            root.put("totalLines", totalLines);
            root.put("errorCount", errors.size());
            root.put("message", String.format(
                "Validación fallida: %d errores en %d líneas. Corrige TODOS y re-envía el contenido completo.",
                errors.size(), totalLines
            ));

            ArrayNode errorsArray = MAPPER.createArrayNode();
            for (ValidationError error : errors) {
                ObjectNode errorNode = MAPPER.createObjectNode();
                errorNode.put("line", error.lineNumber);
                errorNode.put("content", truncate(error.lineContent, 60));
                errorNode.put("error", error.message);

                if (error.suggestions != null && !error.suggestions.isEmpty()) {
                    ArrayNode suggestionsArray = MAPPER.createArrayNode();
                    for (String suggestion : error.suggestions) {
                        suggestionsArray.add(suggestion);
                    }
                    errorNode.set("suggestions", suggestionsArray);
                }
                errorsArray.add(errorNode);
            }
            root.set("errors", errorsArray);

            // Los errores ya se mostraron en tiempo real durante el procesamiento
            return MAPPER.writeValueAsString(root);
        } catch (IOException e) {
            return "{\"success\":false,\"message\":\"Error formateando respuesta\"}";
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

    private static String truncate(String s, int maxLen) {
        if (s == null) return "";
        if (s.length() <= maxLen) return s;
        return s.substring(0, maxLen - 3) + "...";
    }

    private record ValidationError(
        int lineNumber,
        String lineContent,
        String message,
        List<String> suggestions
    ) {}
}
