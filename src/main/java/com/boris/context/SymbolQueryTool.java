package com.boris.context;

import com.boris.context.CodeSymbol.SymbolType;
import com.boris.tooling.ToolProgressNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class SymbolQueryTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Cache para evitar búsquedas duplicadas en la misma sesión
    private static final Map<String, String> SEARCH_CACHE = new ConcurrentHashMap<>();

    public static void clearCache() {
        SEARCH_CACHE.clear();
    }

    public static String search(String query, String type, String repo) {
        if (query == null || query.isBlank()) {
            return error("query es requerido");
        }

        // Cache key
        String cacheKey = query.toLowerCase() + "|" + (type != null ? type : "") + "|" + (repo != null ? repo : "");

        // Retornar resultado cacheado si existe
        if (SEARCH_CACHE.containsKey(cacheKey)) {
            ToolProgressNotifier.notify("query_symbols", "Cache hit: " + query);
            return SEARCH_CACHE.get(cacheKey);
        }

        ToolProgressNotifier.notify("query_symbols", "Buscando: " + query);

        SymbolType symbolType = null;
        if (type != null && !type.isBlank()) {
            try {
                symbolType = SymbolType.valueOf(type.toUpperCase());
            } catch (IllegalArgumentException e) {
                return error("Tipo inválido: " + type + ". Usa: CLASS, METHOD, CONSTRUCTOR, FIELD, INTERFACE, ENUM");
            }
        }

        List<CodeSymbol> results = SymbolRepository.search(query, symbolType, repo);
        String result = formatResults(query, results);

        // Guardar en cache
        SEARCH_CACHE.put(cacheKey, result);

        return result;
    }

    public static String findMethods(String className) {
        if (className == null || className.isBlank()) {
            return error("className es requerido");
        }

        ToolProgressNotifier.notify("query_symbols", "Métodos de: " + className);

        List<CodeSymbol> results = SymbolRepository.findMethodsOfClass(className);

        return formatResults("methods of " + className, results);
    }

    private static final int MAX_RESULTS = 40;

    public static String listClasses(String repo) {
        ToolProgressNotifier.notify("query_symbols", "Listando clases" + (repo != null ? " de " + repo : ""));

        List<CodeSymbol> results = SymbolRepository.findAllClasses(repo);
        if (results.size() > MAX_RESULTS) {
            results = results.subList(0, MAX_RESULTS);
        }

        return formatResults("classes (primeras " + results.size() + ")", results);
    }

    public static String indexStatus() {
        int total = SymbolRepository.totalCount();
        List<String> repos = SymbolRepository.listRepos();

        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("total_symbols", total);
            root.put("indexed_repos", repos.size());

            ArrayNode reposArray = root.putArray("repos");
            for (String repo : repos) {
                ObjectNode repoNode = reposArray.addObject();
                repoNode.put("name", repo);
                repoNode.put("symbols", SymbolRepository.countByRepo(repo));
            }

            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return error("Error: " + e.getMessage());
        }
    }

    public static String indexProject(String path) {
        Path projectPath = path != null && !path.isBlank()
            ? Path.of(path)
            : Path.of(System.getProperty("user.dir"));

        if (!Files.exists(projectPath)) {
            return error("Ruta no existe: " + projectPath);
        }

        String repoName = projectPath.getFileName().toString();

        // Si ya está indexado, reportar
        if (SymbolRepository.isIndexed(repoName)) {
            int count = SymbolRepository.countByRepo(repoName);
            return success(repoName + " ya indexado con " + count + " símbolos. Usa query_symbols para buscar.");
        }

        try {
            int count = CodeIndexerService.indexRepository(repoName, projectPath);
            return success("Indexados " + count + " símbolos de " + repoName);
        } catch (Exception e) {
            return error("Error indexando: " + e.getMessage());
        }
    }

    private static String formatResults(String query, List<CodeSymbol> results) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("query", query);
            root.put("found", results.size());

            if (results.isEmpty()) {
                root.put("message", "No se encontró '" + query + "'. " +
                    "Verifica el nombre o usa index_project() si el código no está indexado.");
            } else {
                ArrayNode symbolsArray = root.putArray("symbols");

                int limit = Math.min(results.size(), MAX_RESULTS);
                for (int i = 0; i < limit; i++) {
                    CodeSymbol s = results.get(i);
                    ObjectNode sym = symbolsArray.addObject();
                    sym.put("name", s.getName());
                    sym.put("type", s.getType().name());
                    sym.put("signature", s.getSignature());
                    sym.put("in", s.getParent());
                    sym.put("file", s.getFile());
                    sym.put("line", s.getLine());
                    if (s.getRepo() != null && !s.getRepo().isBlank()) {
                        java.nio.file.Path projectRoot = com.boris.knowledge.KnowledgeStorageService.resolveProjectRoot(null);
                        if (com.boris.knowledge.KnowledgeStorageService.hasKnowledge(projectRoot, s.getRepo(), s.getName())) {
                            sym.put("knowledge_card", ".boris/" + s.getRepo() + "/" + s.getName() + ".md");
                        }
                    }
                }

                if (results.size() > MAX_RESULTS) {
                    root.put("truncated", "Mostrando " + MAX_RESULTS + " de " + results.size() + " resultados. Filtra tu búsqueda con un término más específico.");
                }

                root.put("hint", "Usa las signaturas requeridas para tu cambio puntual. No es necesario consultar todas las clases secundarias.");
            }

            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return error("Error formateando: " + e.getMessage());
        }
    }

    private static String success(String message) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("success", true);
            root.put("message", message);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"success\":true,\"message\":\"" + message + "\"}";
        }
    }

    private static String error(String message) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("success", false);
            root.put("error", message);
            return MAPPER.writeValueAsString(root);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"" + message + "\"}";
        }
    }
}
