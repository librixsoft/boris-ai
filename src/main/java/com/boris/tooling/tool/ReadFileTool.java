package com.boris.tooling.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.exceptions.BorisException;
import com.boris.tooling.ToolDefinition;

public class ReadFileTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final int maxFileTokens;
    private final int maxFileChars;

    public ReadFileTool(int contextWindow) {
        this.maxFileTokens = FileSizePolicy.maxFileTokens(contextWindow);
        this.maxFileChars = FileSizePolicy.maxFileChars(contextWindow);
    }

    public ToolDefinition read_file() {
        var pathProp = Map.of("type", "string", "description", "Absolute or relative file path");
        var properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProp);
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return ToolDefinition.of(
                "read_file",
                "Read the contents of a file at the given path. Returns the file content as a string.",
                schema);
    }

    public String execute(Map<String, Object> args) {
        @SuppressWarnings("unchecked")
        String pathStr = (String) args.getOrDefault("path", "");
        if (pathStr == null || pathStr.isBlank()) {
            return formatOutput(false, "Error: path is required");
        }

        Path path = Paths.get(pathStr);
        if (!Files.exists(path)) {
            return formatOutput(false, "Error: file not found: " + pathStr);
        }
        if (!Files.isRegularFile(path)) {
            return formatOutput(false, "Error: not a regular file: " + pathStr);
        }

        try {
            long sizeBytes = Files.size(path);
            int estimatedTokens = (int) Math.ceil(sizeBytes / 3.5);
            if (estimatedTokens > maxFileTokens) {
                return formatOutput(false, FileSizePolicy.tooLargeMessage(
                        "read_file", pathStr, estimatedTokens, maxFileTokens, maxFileChars));
            }

            String content = Files.readString(path);
            return formatOutput(true, content);
        } catch (IOException e) {
            throw new BorisException("Error reading file: " + pathStr, e);
        }
    }

    private static String formatOutput(boolean success, String message) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", success);
            node.put("message", message);
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new BorisException("Failed to format output", e);
        }
    }
}
