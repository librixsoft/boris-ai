package com.boris.tooling.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.tooling.ToolProgressNotifier;

/**
 * Tool para buscar clases y métodos en .boris/CONTEXT.md.
 * Devuelve resultados estructurados que el modelo puede usar directamente.
 */
public class SearchContextTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static String execute(String query, String projectPath) {
        if (query == null || query.isBlank()) {
            return formatError("query es requerido");
        }

        Path contextPath;
        if (projectPath != null && !projectPath.isBlank()) {
            contextPath = Paths.get(projectPath, ".boris", "CONTEXT.md");
        } else {
            contextPath = Paths.get(".boris", "CONTEXT.md");
        }

        if (!Files.exists(contextPath)) {
            return formatError("CONTEXT.md no existe en " + contextPath +
                ". Usa create_library_context primero.");
        }

        ToolProgressNotifier.notify("search_context", "Buscando: " + query);

        try {
            String content = Files.readString(contextPath);
            List<SearchResult> results = searchInContext(content, query);

            return formatResults(query, results, contextPath.toString());
        } catch (IOException e) {
            return formatError("Error leyendo CONTEXT.md: " + e.getMessage());
        }
    }

    private static List<SearchResult> searchInContext(String content, String query) {
        List<SearchResult> results = new ArrayList<>();
        String queryLower = query.toLowerCase();
        String[] queryTerms = queryLower.split("\\s+|,|\\|");

        // Buscar secciones de clases que coincidan
        Pattern classPattern = Pattern.compile("### `([^`]+)`\\n([\\s\\S]*?)(?=### `|## |$)");
        Matcher matcher = classPattern.matcher(content);

        while (matcher.find()) {
            String className = matcher.group(1);
            String classContent = matcher.group(2);
            String classNameLower = className.toLowerCase();

            // Verificar si algún término coincide con la clase
            boolean matches = false;
            for (String term : queryTerms) {
                if (term.isBlank()) continue;
                if (classNameLower.contains(term) || classContent.toLowerCase().contains(term)) {
                    matches = true;
                    break;
                }
            }

            if (matches) {
                // Extraer métodos de esta clase
                List<String> methods = new ArrayList<>();
                for (String line : classContent.split("\n")) {
                    line = line.trim();
                    if (line.startsWith("- `") && line.endsWith("`")) {
                        methods.add(line.substring(3, line.length() - 1));
                    }
                }
                results.add(new SearchResult(className, methods));
            }
        }

        // Buscar en imports si no hay resultados de clases
        if (results.isEmpty()) {
            Pattern importPattern = Pattern.compile("import ([^;]+);");
            Matcher importMatcher = importPattern.matcher(content);
            List<String> matchingImports = new ArrayList<>();

            while (importMatcher.find()) {
                String imp = importMatcher.group(1);
                for (String term : queryTerms) {
                    if (term.isBlank()) continue;
                    if (imp.toLowerCase().contains(term)) {
                        matchingImports.add("import " + imp + ";");
                        break;
                    }
                }
            }

            if (!matchingImports.isEmpty()) {
                results.add(new SearchResult("Imports", matchingImports));
            }
        }

        return results;
    }

    private static String formatResults(String query, List<SearchResult> results, String contextPath) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("success", true);
            root.put("query", query);
            root.put("source", contextPath);
            root.put("totalMatches", results.size());

            if (results.isEmpty()) {
                root.put("message", "No se encontró '" + query + "' en CONTEXT.md. " +
                    "Verifica el nombre exacto o usa create_library_context con force=true.");
                root.putArray("results");
            } else {
                ArrayNode resultsArray = root.putArray("results");
                for (SearchResult result : results) {
                    ObjectNode resultNode = resultsArray.addObject();
                    resultNode.put("class", result.className);

                    ArrayNode methodsArray = resultNode.putArray("methods");
                    for (String method : result.methods) {
                        methodsArray.add(method);
                    }
                }

                // Agregar instrucciones de uso
                root.put("instructions",
                    "USA SOLO las clases y métodos listados arriba. " +
                    "NO inventes métodos que no aparezcan aquí.");
            }

            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (IOException e) {
            return formatError("Error formateando resultados: " + e.getMessage());
        }
    }

    private static String formatError(String message) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", false);
            node.put("error", message);
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            return "{\"success\":false,\"error\":\"" + message + "\"}";
        }
    }

    private record SearchResult(String className, List<String> methods) {}
}
