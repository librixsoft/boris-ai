package com.boris.tooling.parallel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.context.ProjectContext;

/**
 * Directory listing tool - simple, one directory at a time.
 */
public class BatchListDirectoriesTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public String execute(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) {
            pathStr = ".";
        }

        // Resolve relative path
        String projectPath = ProjectContext.getProjectPath();
        if (projectPath == null || projectPath.isBlank()) {
            projectPath = System.getProperty("user.dir");
        }

        Path path = Paths.get(pathStr);
        String resolvedPath;
        if (path.isAbsolute()) {
            resolvedPath = pathStr;
        } else {
            resolvedPath = Paths.get(projectPath, pathStr).toString();
        }

        Path dirPath = Paths.get(resolvedPath);
        if (!Files.exists(dirPath)) {
            return formatError("Directory not found: " + pathStr);
        }
        if (!Files.isDirectory(dirPath)) {
            return formatError("Not a directory: " + pathStr);
        }

        try (var stream = Files.list(dirPath)) {
            StringBuilder sb = new StringBuilder();
            stream.sorted().forEach(entry -> {
                try {
                    String type = Files.isDirectory(entry) ? "D" : "F";
                    long size = Files.size(entry);
                    String name = entry.getFileName().toString();
                    sb.append(type).append(" ")
                      .append(String.format("%10d", size)).append("  ")
                      .append(name).append("\n");
                } catch (IOException ignored) {}
            });
            return formatSuccess(sb.toString().trim());
        } catch (IOException e) {
            return formatError("Error listing directory: " + e.getMessage());
        }
    }

    private String formatSuccess(String content) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", true);
            node.put("message", content);
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            return "{\"success\":true,\"message\":\"" + content.replace("\"", "\\\"") + "\"}";
        }
    }

    private String formatError(String message) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", false);
            node.put("message", message);
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"success\":false,\"message\":\"" + message + "\"}";
        }
    }
}
