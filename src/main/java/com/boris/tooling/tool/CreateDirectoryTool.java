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

public class CreateDirectoryTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ToolDefinition create_directory() {
        var pathProp = Map.of("type", "string", "description", "Absolute or relative directory path to create");
        var properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProp);
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return ToolDefinition.of(
                "create_directory",
                "Create a directory, including missing parent directories. Returns success status and message.",
                schema);
    }

    public static String execute(Map<String, Object> args) {
        String pathStr = (String) args.getOrDefault("path", "");
        if (pathStr == null || pathStr.isBlank()) {
            return formatOutput(false, "Error: path is required");
        }

        Path path = Paths.get(pathStr);
        if (Files.exists(path)) {
            if (Files.isDirectory(path)) {
                return formatOutput(true, "Directory already exists: " + pathStr);
            }
            return formatOutput(false, "Error: path exists and is a file, not a directory: " + pathStr);
        }

        try {
            Files.createDirectories(path);
            return formatOutput(true, "Directory created successfully: " + pathStr);
        } catch (IOException e) {
            throw new com.boris.exceptions.BorisException("Error creating directory: " + pathStr, e);
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
