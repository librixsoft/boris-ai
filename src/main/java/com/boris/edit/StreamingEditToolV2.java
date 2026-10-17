package com.boris.edit;

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
import java.util.List;
import java.util.Map;

/**
 * V2: El modelo envía todo el contenido de una vez.
 * Boris procesa línea por línea internamente (rápido).
 * Solo pide corrección si hay error.
 */
public class StreamingEditToolV2 {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static SymbolCache symbolCache;
    private static LineValidator validator;
    private static int pendingErrorLine = -1;
    private static String pendingFilePath = null;
    private static List<String> pendingLines = null;
    private static int lastProcessedLine = 0;

    public static ToolDefinition streaming_edit() {
        var pathProp = Map.of("type", "string", "description", "Ruta del archivo a crear");
        var contentProp = Map.of("type", "string", "description", "Contenido completo del archivo");

        var properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProp);
        properties.put("content", contentProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new String[]{"path", "content"});

        return ToolDefinition.of(
            "streaming_edit",
            "Crea o reemplaza un archivo. Boris valida cada línea contra CONTEXT.md y muestra diff en tiempo real.",
            schema
        );
    }

    public static String streaming_edit(Map<String, Object> args) {
        String pathStr = (String) args.get("path");
        String content = (String) args.getOrDefault("content", "");

        if (pathStr == null || pathStr.isBlank()) {
            return formatResponse(false, "Error: path es requerido", null, -1);
        }

        try {
            // Cargar cache de símbolos
            Path contextFile = findContextFile(pathStr);
            symbolCache = new SymbolCache();
            if (contextFile != null && Files.exists(contextFile)) {
                symbolCache.loadFromContext(contextFile);
            }
            validator = new LineValidator(symbolCache);

            Path filePath = Paths.get(pathStr);
            Files.createDirectories(filePath.getParent());

            // Mostrar header
            DiffLineNotifier.notify("@@@ " + filePath.getFileName() + " @@@");

            // Procesar línea por línea
            String[] lines = content.split("\n", -1);
            pendingLines = List.of(lines);
            pendingFilePath = pathStr;
            lastProcessedLine = 0;

            // Limpiar archivo si existe
            Files.writeString(filePath, "");

            for (int i = 0; i < lines.length; i++) {
                String line = lines[i];
                LineValidator.ValidationResult result = validator.validate(line);

                if (result.isValid()) {
                    // Escribir línea al archivo
                    if (i == 0) {
                        Files.writeString(filePath, line + "\n");
                    } else {
                        Files.writeString(filePath, line + "\n", StandardOpenOption.APPEND);
                    }

                    // Mostrar en UI (verde)
                    DiffLineNotifier.notify(String.format("%3d │+ %s", i + 1, line));
                    lastProcessedLine = i + 1;
                } else {
                    // Error - mostrar y pedir corrección
                    DiffLineNotifier.notify(String.format("%3d │⚠ %s", i + 1, truncate(line, 50)));
                    DiffLineNotifier.notify("    └─ ERROR: " + result.errorMessage());
                    if (result.hasSuggestions()) {
                        DiffLineNotifier.notify("    └─ Usa: " + String.join(", ", result.suggestions()));
                    }

                    pendingErrorLine = i;
                    return formatResponse(false,
                        "Error en línea " + (i + 1) + ": " + result.errorMessage(),
                        result.hasSuggestions() ? "Alternativas: " + String.join(", ", result.suggestions()) : null,
                        i + 1
                    );
                }
            }

            // Todo OK
            DiffLineNotifier.notify(String.format("─── +%d -%d ───", lines.length, 0));
            pendingErrorLine = -1;
            pendingFilePath = null;
            pendingLines = null;

            return formatResponse(true,
                "✓ Archivo creado: " + pathStr + " (" + lines.length + " líneas)",
                null, -1
            );

        } catch (IOException e) {
            return formatResponse(false, "Error: " + e.getMessage(), null, -1);
        }
    }

    public static ToolDefinition streaming_edit_fix() {
        var lineProp = Map.of("type", "string", "description", "Línea corregida");

        var properties = new LinkedHashMap<String, Object>();
        properties.put("line", lineProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new String[]{"line"});

        return ToolDefinition.of(
            "streaming_edit_fix",
            "Corrige la línea que tuvo error de validación y continúa procesando.",
            schema
        );
    }

    public static String streaming_edit_fix(Map<String, Object> args) {
        if (pendingErrorLine < 0 || pendingFilePath == null || pendingLines == null) {
            return formatResponse(false, "No hay error pendiente que corregir", null, -1);
        }

        String correctedLine = (String) args.getOrDefault("line", "");

        // Validar la corrección
        LineValidator.ValidationResult result = validator.validate(correctedLine);

        if (!result.isValid()) {
            DiffLineNotifier.notify(String.format("%3d │⚠ %s", pendingErrorLine + 1, truncate(correctedLine, 50)));
            DiffLineNotifier.notify("    └─ Aún hay error: " + result.errorMessage());

            return formatResponse(false,
                "Aún hay error: " + result.errorMessage(),
                result.hasSuggestions() ? "Alternativas: " + String.join(", ", result.suggestions()) : null,
                pendingErrorLine + 1
            );
        }

        try {
            Path filePath = Paths.get(pendingFilePath);

            // Escribir línea corregida
            Files.writeString(filePath, correctedLine + "\n", StandardOpenOption.APPEND);
            DiffLineNotifier.notify(String.format("%3d │+ %s", pendingErrorLine + 1, correctedLine));

            int continueFrom = pendingErrorLine + 1;
            String[] lines = pendingLines.toArray(new String[0]);

            // Continuar con las líneas restantes
            for (int i = continueFrom; i < lines.length; i++) {
                String line = lines[i];
                LineValidator.ValidationResult lineResult = validator.validate(line);

                if (lineResult.isValid()) {
                    Files.writeString(filePath, line + "\n", StandardOpenOption.APPEND);
                    DiffLineNotifier.notify(String.format("%3d │+ %s", i + 1, line));
                    lastProcessedLine = i + 1;
                } else {
                    DiffLineNotifier.notify(String.format("%3d │⚠ %s", i + 1, truncate(line, 50)));
                    DiffLineNotifier.notify("    └─ ERROR: " + lineResult.errorMessage());
                    if (lineResult.hasSuggestions()) {
                        DiffLineNotifier.notify("    └─ Usa: " + String.join(", ", lineResult.suggestions()));
                    }

                    pendingErrorLine = i;
                    return formatResponse(false,
                        "Error en línea " + (i + 1) + ": " + lineResult.errorMessage(),
                        lineResult.hasSuggestions() ? "Alternativas: " + String.join(", ", lineResult.suggestions()) : null,
                        i + 1
                    );
                }
            }

            // Todo completado
            DiffLineNotifier.notify(String.format("─── +%d -%d ───", lines.length, 0));
            pendingErrorLine = -1;
            pendingFilePath = null;
            pendingLines = null;

            return formatResponse(true,
                "✓ Archivo completado: " + filePath.getFileName() + " (" + lines.length + " líneas)",
                null, -1
            );

        } catch (IOException e) {
            return formatResponse(false, "Error: " + e.getMessage(), null, -1);
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

    private static String formatResponse(boolean success, String message, String detail, int errorLine) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", success);
            node.put("message", message);
            if (detail != null && !detail.isEmpty()) {
                node.put("detail", detail);
            }
            if (errorLine > 0) {
                node.put("errorLine", errorLine);
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
