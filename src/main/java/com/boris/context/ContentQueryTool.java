package com.boris.context;

import com.boris.context.ContentRepository.DocResult;
import com.boris.tooling.ToolProgressNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

public class ContentQueryTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int MAX_CONTENT_PER_RECORD = 2000;
    private static final int MAX_FULL_FILE_CONTENT = 20000;
    private static final int MAX_SYMBOLS = 20;

    public static String search(String query, String repo, String path) {
        if ((query == null || query.isBlank()) && (path == null || path.isBlank())) {
            return error("query o path es requerido");
        }

        if (path != null && !path.isBlank()) {
            return readByPath(repo, path);
        }

        ToolProgressNotifier.notify("query_repo_content", "Buscando en repos indexados: " + query);

        try {
            List<DocResult> docs = ContentRepository.search(query, repo);
            List<CodeSymbol> symbols = SymbolRepository.search(query, null, repo);
            return formatResults(query, docs, symbols);
        } catch (Exception e) {
            return error("Error buscando contenido: " + e.getMessage());
        }
    }

    private static String readByPath(String repo, String path) {
        ToolProgressNotifier.notify("query_repo_content", "Leyendo archivo: " + path);

        try {
            List<DocResult> results = ContentRepository.readByPath(repo, path);

            if (results.isEmpty()) {
                return formatResults(path, results, List.of());
            }

            String resultRepo = results.getFirst().repo();
            ObjectNode root = MAPPER.createObjectNode();
            root.put("success", true);
            root.put("path", resultRepo + "/" + results.getFirst().path());

            StringBuilder contentBuilder = new StringBuilder();
            for (DocResult chunk : results) {
                contentBuilder.append(chunk.content());
            }
            String fullContent = contentBuilder.toString();
            root.put("content", fullContent.length() > MAX_FULL_FILE_CONTENT
                ? fullContent.substring(0, MAX_FULL_FILE_CONTENT) + "... [truncado]"
                : fullContent);

            root.set("symbols_in_file",
                symbolsArray(com.boris.context.SymbolRepository.findSymbolsByFile(results.getFirst().path())));

            ArrayNode moreArray = root.putArray("repo_files");
            int added = 0;
            for (String related : ContentRepository.listFiles(resultRepo)) {
                if (related.endsWith(path)) {
                    continue;
                }
                relatedFilesNode(moreArray, related);
                added++;
                if (added >= 25) {
                    break;
                }
            }

            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return error("Error leyendo archivo: " + e.getMessage());
        }
    }

    private static void relatedFilesNode(ArrayNode array, String related) {
        ObjectNode node = array.addObject();
        node.put("path", related);
        int idx = related.lastIndexOf('/');
        if (idx + 1 > 0 && idx < related.length() - 1) {
            node.put("name", related.substring(idx + 1));
        }
    }

    private static ArrayNode symbolsArray(List<CodeSymbol> symbols) {
        ArrayNode symbolsArray = MAPPER.createArrayNode();
        for (CodeSymbol s : symbols) {
            ObjectNode symNode = symbolsArray.addObject();
            symNode.put("name", s.getName());
            symNode.put("type", s.getType().name());
            symNode.put("signature", s.getSignature());
            symNode.put("in", s.getParent());
            symNode.put("file", s.getFile());
            symNode.put("line", s.getLine());
            symNode.put("repo", s.getRepo());
        }
        return symbolsArray;
    }

    private static String formatResults(String query, List<DocResult> results, List<CodeSymbol> symbols) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("query", query);

            root.set("symbols", symbolsArray(symbols != null ? symbols : List.of()));

            if (results.isEmpty()) {
                root.put("found", 0);
                root.put("message", "Sin resultados. Cambia el termino de busqueda o verifica con index_status() que el repo este indexado.");
            } else {
                root.put("found", results.size());

                ArrayNode recordsArray = root.putArray("records");
                for (int i = 0; i < results.size(); i++) {
                    DocResult record = results.get(i);
                    ObjectNode recordNode = recordsArray.addObject();
                    recordNode.put("repo", record.repo());
                    recordNode.put("path", record.path());
                    recordNode.put("section", record.chunkIndex() == 0 ? "start" : "chunk_" + record.chunkIndex());
                    recordNode.put("title", record.title());

                    String content = record.content();
                    if (content.length() > MAX_CONTENT_PER_RECORD) {
                        content = content.substring(0, MAX_CONTENT_PER_RECORD) + "... [truncado]";
                    }
                    recordNode.put("content_excerpt", content);
                }

                root.put("hint", "Resultado unificado: 'symbols' trae clases/metodos Java y 'records' trae contenido de cualquier tipo. Usa query_repo_content(path=\"...\") para leer un archivo completo (incluye symbols_in_file).");
            }

            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return error("Error formateando resultados: " + e.getMessage());
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
