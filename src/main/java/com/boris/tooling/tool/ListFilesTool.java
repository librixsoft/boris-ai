package com.boris.tooling.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.tooling.ToolDefinition;

public class ListFilesTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ToolDefinition list_files() {
        var pathProp = Map.of("type", "string", "description", "Directory path to list");
        var properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProp);
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return ToolDefinition.of(
                "list_files",
                "List files and directories in the given directory. Returns a formatted listing.",
                schema);
    }

    public static String execute(Map<String, Object> args) {
        @SuppressWarnings("unchecked")
        String pathStr = (String) args.getOrDefault("path", ".");
        if (pathStr == null || pathStr.isBlank()) {
            return formatOutput(false, "Error: path is required");
        }

        Path dirPath = Paths.get(pathStr);
        if (!Files.exists(dirPath)) {
            return formatOutput(false, "Directory not found: " + pathStr);
        }
        if (!Files.isDirectory(dirPath)) {
            return formatOutput(false, "Not a directory: " + pathStr);
        }

        try (var stream = Files.list(dirPath)) {
            List<Path> entries = stream.sorted().toList();
            StringBuilder sb = new StringBuilder();
            for (Path entry : entries) {
                String type = Files.isDirectory(entry) ? "D" : "F";
                long size = Files.size(entry);
                sb.append(type).append(" ").append(String.format("%10d", size)).append("  ")
                        .append(entry.getFileName()).append("\n");
            }
            return formatOutput(true, sb.toString().trim());
        } catch (IOException e) {
            throw new com.boris.exceptions.BorisException("Error listing directory: " + pathStr, e);
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
