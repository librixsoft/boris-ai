package com.boris.tooling.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.tooling.ToolDefinition;

public class WriteTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int maxFileTokens;
    private final int maxFileChars;

    public WriteTool(int contextWindow) {
        this.maxFileTokens = FileSizePolicy.maxFileTokens(contextWindow);
        this.maxFileChars = FileSizePolicy.maxFileChars(contextWindow);
    }

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

    public String execute(Map<String, Object> args) {
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

        int estimatedTokens = FileSizePolicy.estimateTokens(content);
        if (estimatedTokens > maxFileTokens) {
            return formatOutput(false, FileSizePolicy.tooLargeMessage("write_file", pathStr, estimatedTokens, maxFileTokens, maxFileChars));
        }

        Path path = Paths.get(pathStr);
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
            long size = Files.size(path);
            return formatOutput(true, "File written successfully: " + pathStr + " (" + size + " bytes)");
        } catch (IOException e) {
            throw new com.boris.exceptions.BorisException("Error writing file: " + pathStr, e);
        }
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
