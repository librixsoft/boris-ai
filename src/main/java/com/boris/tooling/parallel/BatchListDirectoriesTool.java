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
    private static final java.util.Set<String> DEFAULT_IGNORED_NAMES = java.util.Set.of(
            ".git", ".DS_Store", ".idea", ".gradle", "node_modules", "target", "build",
            "out", "dist", ".next", ".turbo", "venv", "__pycache__");

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
        if (pathStr.startsWith("~/") || pathStr.equals("~")) {
            resolvedPath = pathStr.replaceFirst("^~", System.getProperty("user.home"));
        } else if (path.isAbsolute()) {
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
        if (ProjectContext.isInsideAgentWorkspace(dirPath.toAbsolutePath())) {
            return formatError("Access denied: .boris is agent internal workspace. Use query_framework_knowledge(framework, class_name) for cached class summaries.");
        }

        try (var stream = Files.list(dirPath)) {
            StringBuilder sb = new StringBuilder();
            stream.sorted().forEach(entry -> {
                try {
                    if (DEFAULT_IGNORED_NAMES.contains(entry.getFileName().toString())) {
                        return;
                    }
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
