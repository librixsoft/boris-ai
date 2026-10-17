package com.boris.edit;

import com.boris.tooling.DiffLineNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.boris.context.ProjectContext;

/**
 * SearchReplaceEditTool - Ediciones parciales estilo Aider
 *
 * Formato SEARCH/REPLACE:
 * El modelo envía bloques de búsqueda/reemplazo.
 * Boris valida TODOS los reemplazos ANTES de aplicar.
 * Si hay errores (no encontrado, ambiguo), reporta todos juntos.
 * Solo aplica cuando TODO está válido.
 *
 * Ideal para ediciones parciales donde no quieres reescribir todo el archivo.
 */
public class SearchReplaceEditTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Ejecuta múltiples SEARCH/REPLACE en un archivo.
     * Valida todos primero, aplica solo si todos son válidos.
     *
     * @param path Ruta del archivo
     * @param replacements Lista de {search, replace}
     * @return JSON con resultado
     */
    public static String search_replace(String path, List<Map<String, String>> replacements) {
        if (path == null || path.isBlank()) {
            return formatError("path es requerido");
        }

        // Resolver ruta relativa usando el proyecto actual
        path = ProjectContext.resolveRelativePath(path);

        if (replacements == null || replacements.isEmpty()) {
            return formatError("replacements es requerido");
        }

        try {
            Path filePath = Paths.get(path);
            if (!Files.exists(filePath)) {
                return formatError("Archivo no existe: " + path);
            }

            String originalContent = Files.readString(filePath);
            String[] originalLines = originalContent.split("\n", -1);

            Path contextFile = findContextFile(path);
            SymbolCache symbolCache = new SymbolCache();
            if (contextFile != null && Files.exists(contextFile)) {
                symbolCache.loadFromContext(contextFile);
            }
            LineValidator validator = new LineValidator(symbolCache);

            // FASE 1: Validar que todos los SEARCH existen y son únicos
            List<ReplaceError> errors = new ArrayList<>();
            List<ValidatedReplacement> validReplacements = new ArrayList<>();

            for (int i = 0; i < replacements.size(); i++) {
                Map<String, String> replacement = replacements.get(i);
                String search = replacement.get("search");
                String replace = replacement.get("replace");

                if (search == null || search.isEmpty()) {
                    errors.add(new ReplaceError(i + 1, "SEARCH vacío", null));
                    continue;
                }

                int count = countOccurrences(originalContent, search);
                if (count == 0) {
                    errors.add(new ReplaceError(i + 1, "SEARCH no encontrado", truncate(search, 50)));
                } else if (count > 1) {
                    errors.add(new ReplaceError(i + 1, "SEARCH ambiguo (" + count + " ocurrencias)", truncate(search, 50)));
                } else {
                    validReplacements.add(new ValidatedReplacement(search, replace != null ? replace : ""));
                }
            }

            if (!errors.isEmpty()) {
                return formatReplaceErrors(path, errors);
            }

            // FASE 2: Aplicar reemplazos y validar líneas nuevas
            String newContent = originalContent;
            List<ValidationError> validationErrors = new ArrayList<>();

            for (ValidatedReplacement vr : validReplacements) {
                newContent = newContent.replace(vr.search, vr.replace);
            }

            // Validar líneas del nuevo contenido
            String[] newLines = newContent.split("\n", -1);
            for (int i = 0; i < newLines.length; i++) {
                LineValidator.ValidationResult result = validator.validate(newLines[i]);
                if (!result.isValid()) {
                    validationErrors.add(new ValidationError(
                        i + 1,
                        newLines[i],
                        result.errorMessage(),
                        result.suggestions()
                    ));
                }
            }

            if (!validationErrors.isEmpty()) {
                return formatValidationErrors(path, validationErrors, newLines.length);
            }

            // FASE 3: Todo válido -> Escribir
            Files.writeString(filePath, newContent);

            // Mostrar diff
            showDiff(path, originalLines, newLines);

            return formatSuccess(path, validReplacements.size());

        } catch (IOException e) {
            return formatError("Error IO: " + e.getMessage());
        }
    }

    private static void showDiff(String path, String[] oldLines, String[] newLines) {
        DiffLineNotifier.notify("@@@ " + Path.of(path).getFileName() + " @@@");

        int maxLen = Math.max(oldLines.length, newLines.length);
        int added = 0, removed = 0;

        for (int i = 0; i < maxLen; i++) {
            String oldLine = i < oldLines.length ? oldLines[i] : null;
            String newLine = i < newLines.length ? newLines[i] : null;

            if (oldLine == null && newLine != null) {
                DiffLineNotifier.notify(String.format("%3d │+ %s", i + 1, newLine));
                added++;
            } else if (oldLine != null && newLine == null) {
                DiffLineNotifier.notify(String.format("%3d │- %s", i + 1, oldLine));
                removed++;
            } else if (!oldLine.equals(newLine)) {
                DiffLineNotifier.notify(String.format("%3d │- %s", i + 1, oldLine));
                DiffLineNotifier.notify(String.format("%3d │+ %s", i + 1, newLine));
                added++;
                removed++;
            }
        }

        DiffLineNotifier.notify(String.format("─── +%d -%d ───", added, removed));
    }

    private static int countOccurrences(String text, String search) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(search, idx)) != -1) {
            count++;
            idx += search.length();
        }
        return count;
    }

    private static String formatReplaceErrors(String path, List<ReplaceError> errors) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("success", false);
            root.put("path", path);
            root.put("errorCount", errors.size());
            root.put("message", "SEARCH/REPLACE fallido: " + errors.size() + " errores. Corrige y re-envía.");

            ArrayNode errorsArray = MAPPER.createArrayNode();
            for (ReplaceError error : errors) {
                ObjectNode errorNode = MAPPER.createObjectNode();
                errorNode.put("block", error.blockNumber);
                errorNode.put("error", error.message);
                if (error.content != null) {
                    errorNode.put("search", error.content);
                }
                errorsArray.add(errorNode);
            }
            root.set("errors", errorsArray);

            DiffLineNotifier.notify("@@@ SEARCH/REPLACE FALLIDO: " + path + " @@@");
            for (ReplaceError error : errors) {
                DiffLineNotifier.notify("Block " + error.blockNumber + ": " + error.message);
                if (error.content != null) {
                    DiffLineNotifier.notify("  SEARCH: " + error.content);
                }
            }

            return MAPPER.writeValueAsString(root);
        } catch (IOException e) {
            return "{\"success\":false,\"message\":\"Error formateando\"}";
        }
    }

    private static String formatValidationErrors(String path, List<ValidationError> errors, int totalLines) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("success", false);
            root.put("path", path);
            root.put("totalLines", totalLines);
            root.put("errorCount", errors.size());
            root.put("message", "Validación fallida: " + errors.size() + " errores en código resultante.");

            ArrayNode errorsArray = MAPPER.createArrayNode();
            for (ValidationError error : errors) {
                ObjectNode errorNode = MAPPER.createObjectNode();
                errorNode.put("line", error.lineNumber);
                errorNode.put("content", truncate(error.lineContent, 60));
                errorNode.put("error", error.message);
                if (error.suggestions != null && !error.suggestions.isEmpty()) {
                    ArrayNode sugg = MAPPER.createArrayNode();
                    error.suggestions.forEach(sugg::add);
                    errorNode.set("suggestions", sugg);
                }
                errorsArray.add(errorNode);
            }
            root.set("errors", errorsArray);

            return MAPPER.writeValueAsString(root);
        } catch (IOException e) {
            return "{\"success\":false,\"message\":\"Error formateando\"}";
        }
    }

    private static String formatSuccess(String path, int replacements) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("success", true);
            root.put("path", path);
            root.put("replacements", replacements);
            root.put("message", "OK: " + replacements + " reemplazos aplicados en " + path);
            return MAPPER.writeValueAsString(root);
        } catch (IOException e) {
            return "{\"success\":true}";
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

    private record ReplaceError(int blockNumber, String message, String content) {}
    private record ValidatedReplacement(String search, String replace) {}
    private record ValidationError(int lineNumber, String lineContent, String message, List<String> suggestions) {}
}
