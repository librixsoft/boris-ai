package com.boris.tooling.tool;

import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.tooling.ToolDefinition;

public class FileTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ToolDefinition read_file() {
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

    public static String read_file(Map<String, Object> args) {
        @SuppressWarnings("unchecked")
        String pathStr = (String) args.getOrDefault("path", "");
        if (pathStr == null || pathStr.isBlank()) {
            return "Error: path is required";
        }

        Path path = Paths.get(pathStr);
        if (!Files.exists(path)) {
            return "Error: file not found: " + pathStr;
        }
        if (!Files.isRegularFile(path)) {
            return "Error: not a regular file: " + pathStr;
        }

        try {
            String content = Files.readString(path);
            return formatOutput(true, content);
        } catch (IOException e) {
            throw new com.boris.exceptions.BorisException("Error reading file: " + pathStr, e);
        }
    }

    public static ToolDefinition write_file() {
        var pathProp = new LinkedHashMap<String, Object>();
        pathProp.put("type", "string");
        pathProp.put("description", "Absolute or relative file path");
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

    public static String write_file(Map<String, Object> args) {
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

    public static String delete_file(Map<String, Object> args) {
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

    public static String list_files(Map<String, Object> args) {
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

    private static String escapeJson(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }
}
