package com.boris.tooling.tool;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.tooling.ToolDefinition;
import com.boris.edit.StreamingFileWriter;
import com.boris.context.ProjectContext;
public class WriteTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

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

        pathStr = sanitizeTargetPath(pathStr);

        // Resolver ruta relativa usando el proyecto actual
        pathStr = ProjectContext.resolveRelativePath(pathStr);

        if (content == null) {
            content = "";
        }

        try {
            var streamedPreview = com.boris.tooling.WriteArgsPreviewStreamer.takeLivePreview();
            Integer streamedLines = streamedPreview != null && pathStr.equals(streamedPreview.path())
                    ? streamedPreview.shownLines()
                    : null;

            // Wrapper: delega a StreamingFileWriter
            StreamingFileWriter.writeFileWithPreview(pathStr, content, streamedLines);
            return formatOutput(true, "File written: " + pathStr);
        } catch (IOException e) {
            return formatOutput(false, "Error writing file: " + e.getMessage());
        }
    }

    public static String sanitizeTargetPath(String rawPath) {
        String trimmed = rawPath.trim();
        if (!trimmed.matches("(?s).*\\s.*")) {
            return trimmed;
        }
        String[] tokens = trimmed.split("[\\r\\n\\t ]+");
        for (int i = tokens.length - 1; i >= 0; i--) {
            String token = tokens[i].trim();
            if (token.matches(".+\\.[a-zA-Z0-9]{1,10}$") && (token.startsWith("/") || token.startsWith("~") || token.startsWith("."))) {
                return token;
            }
        }
        return trimmed;
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
