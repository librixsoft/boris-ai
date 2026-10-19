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

    public static String search(String query, String repo, String path) {
        if ((query == null || query.isBlank()) && (path == null || path.isBlank())) {
            return error("query o path es requerido");
        }

        if (path != null && !path.isBlank()) {
            return readByPath(repo, path);
        }

        ToolProgressNotifier.notify("query_repo_content", "Buscando en repos indexados: " + query);

        try {
            List<DocResult> results = ContentRepository.search(query, repo);
            return formatResults(query, results);
        } catch (Exception e) {
            return error("Error buscando contenido: " + e.getMessage());
        }
    }

    private static String readByPath(String repo, String path) {
        ToolProgressNotifier.notify("query_repo_content", "Leyendo archivo: " + path);

        try {
            List<DocResult> results = ContentRepository.readByPath(repo, path);

            if (results.isEmpty()) {
                return formatResults(path, results);
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

    private static String formatResults(String query, List<DocResult> results) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("query", query);

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

                root.put("hint", "Usa query_repo_content(path=\"...\") para leer un archivo completo. Los contenidos pueden tener varias secciones (chunk_1, chunk_2...).");
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
