package com.boris.edit;

import com.boris.context.CodeSymbol;
import com.boris.context.ProjectContext;
import com.boris.context.SymbolRepository;
import com.boris.tooling.DiffLineNotifier;
import com.boris.tooling.ToolDefinition;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    private static final Set<String> STANDARD_CLASSES = Set.of(
        "ArrayList", "HashMap", "HashSet", "LinkedList", "StringBuilder",
        "String", "Integer", "Long", "Double", "Float", "Boolean",
        "Thread", "Exception", "RuntimeException", "Error",
        "File", "Date", "Random", "SimpleDateFormat", "Arrays", "List",
        "Map", "Set", "Properties", "System", "Math", "Object", "Class",
        "Optional", "Stream", "Collectors", "Path", "Files", "Paths",
        "Pattern", "Matcher", "Scanner", "Consumer", "Supplier", "Function",
        "CompletableFuture", "Future", "ExecutorService", "Executors"
    );

    /**
     * Ejecuta la edición bulk con validación y visualización en tiempo real.
     */
    public static String bulk_edit(Map<String, Object> args) {
        String pathStr = (String) args.get("path");
        String content = (String) args.getOrDefault("content", "");

        if (pathStr == null || pathStr.isBlank()) {
            return formatError("path es requerido");
        }

        // Resolver ruta relativa usando el proyecto actual
        pathStr = ProjectContext.resolveRelativePath(pathStr);

        // Validar contra índice SQLite si hay símbolos indexados y es archivo Java
        if (pathStr.endsWith(".java") && SymbolRepository.totalCount() > 0) {
            String validationError = validateAgainstIndex(content);
            if (validationError != null) {
                return formatError("BLOQUEADO - Clases no encontradas en índice:\n" + validationError +
                    "\n\nUsa query_symbols() para verificar qué clases existen.");
            }
        }

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

    private static String validateAgainstIndex(String code) {
        List<String> errors = new ArrayList<>();

        // Verificar instanciaciones: new ClassName(
        Pattern newPattern = Pattern.compile("new\\s+([A-Z][\\w]+)\\s*[(<]");
        Matcher newMatcher = newPattern.matcher(code);
        while (newMatcher.find()) {
            String className = newMatcher.group(1);
            if (STANDARD_CLASSES.contains(className)) continue;

            List<CodeSymbol> found = SymbolRepository.search(className, CodeSymbol.SymbolType.CLASS, null);
            if (found.isEmpty()) {
                found = SymbolRepository.search(className, CodeSymbol.SymbolType.INTERFACE, null);
            }
            if (found.isEmpty() && !errors.contains("- " + className)) {
                errors.add("- " + className);
            }
        }

        // Verificar extends/implements
        Pattern extendsPattern = Pattern.compile("(?:extends|implements)\\s+([A-Z][\\w]+)");
        Matcher extendsMatcher = extendsPattern.matcher(code);
        while (extendsMatcher.find()) {
            String className = extendsMatcher.group(1);
            if (STANDARD_CLASSES.contains(className)) continue;

            List<CodeSymbol> found = SymbolRepository.search(className, CodeSymbol.SymbolType.CLASS, null);
            if (found.isEmpty()) {
                found = SymbolRepository.search(className, CodeSymbol.SymbolType.INTERFACE, null);
            }
            if (found.isEmpty() && !errors.contains("- " + className)) {
                errors.add("- " + className);
            }
        }

        if (!errors.isEmpty() && errors.size() <= 10) {
            return String.join("\n", errors);
        }
        return null;
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
