package com.boris.tooling.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.tooling.ToolDefinition;

public class DeleteDirectoryTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ToolDefinition delete_directory() {
        var pathProp = new LinkedHashMap<String, Object>();
        pathProp.put("type", "string");
        pathProp.put("description", "Absolute or relative directory path to delete");
        var recursiveProp = new LinkedHashMap<String, Object>();
        recursiveProp.put("type", "boolean");
        recursiveProp.put("description", "If true, delete the directory with all its contents (default: false)");
        var properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProp);
        properties.put("recursive", recursiveProp);
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return ToolDefinition.of(
                "delete_directory",
                "Delete a directory. Fails if the directory is not empty unless recursive=true.",
                schema);
    }

    public static String execute(Map<String, Object> args) {
        String pathStr = (String) args.getOrDefault("path", "");
        boolean recursive = Boolean.TRUE.equals(args.get("recursive"));
        if (pathStr == null || pathStr.isBlank()) {
            return formatOutput(false, "Error: path is required");
        }

        Path path = Paths.get(pathStr);
        if (!Files.exists(path)) {
            return formatOutput(true, "Directory does not exist: " + pathStr);
        }
        if (!Files.isDirectory(path)) {
            return formatOutput(false, "Error: not a directory: " + pathStr);
        }

        try {
            if (recursive) {
                deleteRecursively(path);
            } else {
                if (hasEntries(path)) {
                    return formatOutput(false, "Error: directory is not empty: " + pathStr + ". Use recursive=true to delete with all contents");
                }
                Files.delete(path);
            }
            return formatOutput(true, "Directory deleted successfully: " + pathStr);
        } catch (IOException e) {
            throw new com.boris.exceptions.BorisException("Error deleting directory: " + pathStr, e);
        }
    }

    private static boolean hasEntries(Path path) throws IOException {
        try (var stream = Files.newDirectoryStream(path)) {
            return stream.iterator().hasNext();
        }
    }

    private static void deleteRecursively(Path path) throws IOException {
        try (var stream = Files.walk(path)) {
            stream.sorted(Comparator.reverseOrder()).forEach(p -> {
                try {
                    Files.delete(p);
                } catch (IOException e) {
                    throw new com.boris.exceptions.BorisException("Error deleting: " + p, e);
                }
            });
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
