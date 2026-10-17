package com.boris.tooling.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.tooling.ToolDefinition;
public class WriteTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** Contador de intentos bloqueados por archivo */
    private static final ConcurrentHashMap<String, AtomicInteger> BLOCKED_ATTEMPTS = new ConcurrentHashMap<>();
    private static final int MAX_WRITE_ATTEMPTS = 3;

    public static ToolDefinition write_file() {
        var pathProp = Map.of("type", "string", "description", "Absolute or relative file path");
        var contentProp = Map.of("type", "string", "description", "Content to write to the file");
        var properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProp);
        properties.put("content", contentProp);
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return ToolDefinition.of(
                "write_file",
                "Create a new file or overwrite an existing one with the given content. Returns success status and message.",
                schema);
    }

    public static String execute(Map<String, Object> args) {
        @SuppressWarnings("unchecked")
        String pathStr = (String) args.getOrDefault("path", "");
        @SuppressWarnings("unchecked")
        String content = (String) args.getOrDefault("content", "");

        if (pathStr == null || pathStr.isBlank()) {
            return formatOutput(false, "Error: path is required");
        }
        if (content == null) {
            content = "";
        }

        Path path = Paths.get(pathStr);

        // === VALIDACIÓN: Escritura incremental obligatoria ===
        if (isCodeFile(pathStr)) {
            long lineCount = content.lines().count();
            if (lineCount > 10) {
                // Incrementar contador de intentos para este archivo
                AtomicInteger attempts = BLOCKED_ATTEMPTS.computeIfAbsent(pathStr, k -> new AtomicInteger(0));
                int attemptCount = attempts.incrementAndGet();

                if (attemptCount >= MAX_WRITE_ATTEMPTS) {
                    return formatOutput(false,
                        "STOP. DETENERSE AHORA.\n" +
                        "Has intentado " + attemptCount + " veces escribir archivo completo.\n\n" +
                        "EL ESQUELETO YA EXISTE. NO uses write_file de nuevo.\n\n" +
                        "ÚNICA OPCIÓN:\n" +
                        "1. search_context(query: \"Clase\") → buscar métodos\n" +
                        "2. apply_edit(path, old_text, new_text) → añadir código\n\n" +
                        "NO REINTENTES write_file. USA apply_edit.");
                }

                String className = extractClassName(pathStr);
                return formatOutput(false,
                    "BLOQUEADO (intento " + attemptCount + "/" + MAX_WRITE_ATTEMPTS + ").\n" +
                    "El archivo tiene " + lineCount + " líneas (máximo: 10).\n\n" +
                    "Crea SOLO esqueleto, luego usa apply_edit.\n" +
                    "Si insistes, serás bloqueado.");
            } else {
                // Escribió esqueleto OK - resetear contador
                BLOCKED_ATTEMPTS.remove(pathStr);
            }
        }

        // Verificar contexto para archivos .java con imports externos
        if (pathStr.endsWith(".java") && hasExternalImports(content)) {
            Path projectRoot = findProjectRoot(path);
            if (projectRoot != null) {
                Path contextFile = projectRoot.resolve(".boris/CONTEXT.md");
                if (!Files.exists(contextFile)) {
                    return formatOutput(false,
                        "BLOQUEADO: Antes de escribir codigo con bibliotecas externas, " +
                        "debes crear .boris/CONTEXT.md. Usa: create_library_context(repo, version, project_path)");
                }
                // Verificar si el contexto fue leído en esta sesión CON batch_read_files
                // IMPORTANTE: usar "cat" o "execute_command" NO cuenta, debe ser batch_read_files
                if (!ContextTracker.wasRead(projectRoot)) {
                    return formatOutput(false,
                        "BLOQUEADO: Debes leer .boris/CONTEXT.md con batch_read_files ANTES de escribir codigo.\n" +
                        "Ejecuta: batch_read_files([\".boris/CONTEXT.md\"])\n" +
                        "IMPORTANTE: Usar 'cat' o 'execute_command' NO cuenta. Debe ser batch_read_files.\n" +
                        "Luego usa SOLO las clases y métodos documentados en el CONTEXT.md.");
                }

                // Validar que las clases usadas estén en CONTEXT.md
                String validationWarning = validateClassesInContext(content, contextFile);
                if (validationWarning != null) {
                    return formatOutput(false,
                        "ADVERTENCIA DE ALUCINACIÓN: " + validationWarning + "\n" +
                        "Verifica en .boris/repos/ si el método existe: grep -rn 'nombreMetodo' .boris/repos/*/src --include='*.java'");
                }
            }
        }

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
            long size = Files.size(path);
            return formatOutput(true, "File written successfully: " + pathStr + " (" + size + " bytes)");
        } catch (IOException e) {
            throw new com.boris.exceptions.BorisException("Error writing file: " + pathStr, e);
        }
    }

    private static boolean hasExternalImports(String content) {
        return content.lines()
            .filter(line -> line.trim().startsWith("import "))
            .map(line -> line.trim().substring(7).replace(";", "").trim())
            .anyMatch(imp -> !imp.startsWith("java.") &&
                           !imp.startsWith("javax.") &&
                           !imp.startsWith("jdk."));
    }

    private static String extractClassName(String path) {
        String fileName = Paths.get(path).getFileName().toString();
        int dotIndex = fileName.lastIndexOf('.');
        if (dotIndex > 0) {
            return fileName.substring(0, dotIndex);
        }
        return fileName;
    }

    private static boolean isCodeFile(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".java") ||
               lower.endsWith(".kt") ||
               lower.endsWith(".scala") ||
               lower.endsWith(".groovy") ||
               lower.endsWith(".py") ||
               lower.endsWith(".js") ||
               lower.endsWith(".ts") ||
               lower.endsWith(".tsx") ||
               lower.endsWith(".jsx") ||
               lower.endsWith(".go") ||
               lower.endsWith(".rs") ||
               lower.endsWith(".c") ||
               lower.endsWith(".cpp") ||
               lower.endsWith(".h") ||
               lower.endsWith(".cs") ||
               lower.endsWith(".rb") ||
               lower.endsWith(".php") ||
               lower.endsWith(".swift") ||
               lower.endsWith(".m");
    }

    private static Path findProjectRoot(Path filePath) {
        Path current = filePath.toAbsolutePath().getParent();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) ||
                Files.exists(current.resolve("build.gradle")) ||
                Files.exists(current.resolve("package.json"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    /**
     * Valida que las clases instanciadas con "new" estén documentadas en CONTEXT.md
     */
    private static String validateClassesInContext(String code, Path contextFile) {
        try {
            String contextContent = Files.readString(contextFile);

            // Buscar instanciaciones: new ClassName(
            java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("new\\s+([A-Z][\\w]+)\\s*\\(");
            java.util.regex.Matcher matcher = pattern.matcher(code);

            java.util.Set<String> missingClasses = new java.util.TreeSet<>();
            java.util.Set<String> standardClasses = java.util.Set.of(
                "ArrayList", "HashMap", "HashSet", "LinkedList", "StringBuilder",
                "String", "Integer", "Long", "Double", "Thread", "Exception",
                "File", "Date", "Random", "SimpleDateFormat", "Arrays"
            );

            while (matcher.find()) {
                String className = matcher.group(1);
                // Ignorar clases estándar de Java
                if (standardClasses.contains(className)) continue;

                // Verificar si la clase está en CONTEXT.md
                if (!contextContent.contains(className) &&
                    !contextContent.contains("### `" + className + "`") &&
                    !contextContent.contains("import " + className)) {
                    missingClasses.add(className);
                }
            }

            if (!missingClasses.isEmpty() && missingClasses.size() <= 3) {
                return "Clases NO encontradas en CONTEXT.md: " + missingClasses +
                       ". Verifica que existan antes de usarlas.";
            }

        } catch (IOException e) {
            // Si no puede leer el contexto, no bloquear
        }
        return null; // OK
    }

    private static String formatOutput(boolean success, String message) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", success);
            node.put("message", message);
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }
}
