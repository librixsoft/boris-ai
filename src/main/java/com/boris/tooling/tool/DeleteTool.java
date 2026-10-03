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

public class DeleteTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ToolDefinition delete_file() {
        var pathProp = Map.of("type", "string", "description", "Absolute or relative file path");
        var properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProp);
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return ToolDefinition.of(
                "delete_file",
                "Delete a file at the given path. Returns success status and message.",
                schema);
    }

    public static String execute(Map<String, Object> args) {
        @SuppressWarnings("unchecked")
        String pathStr = (String) args.getOrDefault("path", "");
        if (pathStr == null || pathStr.isBlank()) {
            return formatOutput(false, "Error: path is required");
        }

        Path path = Paths.get(pathStr);
        if (!Files.exists(path)) {
            return formatOutput(true, "File does not exist: " + pathStr);
        }
        if (Files.isDirectory(path)) {
            return formatOutput(false, "Error: is a directory, not a file: " + pathStr);
        }

        try {
            Files.delete(path);
            return formatOutput(true, "File deleted successfully: " + pathStr);
        } catch (IOException e) {
            throw new com.boris.exceptions.BorisException("Error deleting file: " + pathStr, e);
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
