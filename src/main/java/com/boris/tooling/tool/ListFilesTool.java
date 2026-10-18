package com.boris.tooling.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.tooling.ToolDefinition;
import com.boris.context.ProjectContext;

public class ListFilesTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ToolDefinition list_files() {
        var pathProp = Map.of("type", "string", "description", "Directory path to list (absolute or relative)");
        var recursiveProp = Map.of("type", "boolean", "description", "If true, list files recursively (default: false)");
        var maxDepthProp = Map.of("type", "integer", "description", "Maximum depth for recursive listing (default: 3)");
        var patternProp = Map.of("type", "string", "description", "Optional glob pattern to filter files (e.g., *.java, *.ts)");

        var properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProp);
        properties.put("recursive", recursiveProp);
        properties.put("max_depth", maxDepthProp);
        properties.put("pattern", patternProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);

        return ToolDefinition.of(
                "list_files",
                "List files and directories in a given path. Returns file names, types (file/directory), and sizes.",
                schema);
    }

    public static String execute(Map<String, Object> args) {
        String pathStr = (String) args.getOrDefault("path", ".");
        Boolean recursive = (Boolean) args.getOrDefault("recursive", false);
        Integer maxDepth = args.get("max_depth") != null ? ((Number) args.get("max_depth")).intValue() : 3;
        String pattern = (String) args.get("pattern");

        if (pathStr == null || pathStr.isBlank()) {
            pathStr = ".";
        }

        pathStr = ProjectContext.resolveRelativePath(pathStr);
        Path basePath = Paths.get(pathStr);

        String projectPath = ProjectContext.getProjectPath();
        if (!Files.exists(basePath)) {
            return formatOutput(false,
                    "Directory not found: " + pathStr + ". The current project path is: " + projectPath
                    + ". Call list_files with the exact project path or a relative path inside it. Do not invent sibling directories.",
                    null);
        }

        if (!Files.isDirectory(basePath)) {
            return formatOutput(false, "Path is not a directory: " + pathStr, null);
        }

        try {
            ObjectNode result = MAPPER.createObjectNode();
            result.put("success", true);
            result.put("path", basePath.toAbsolutePath().toString());

            ArrayNode filesArray = result.putArray("entries");

            int depth = (recursive != null && recursive) ? maxDepth : 1;

            int limit = 500;
            StringBuilder listing = new StringBuilder();

            try (Stream<Path> stream = Files.walk(basePath, depth)) {
                stream.filter(p -> !p.equals(basePath))
                      .filter(p -> matchesPattern(p, pattern))
                      .sorted()
                      .limit(limit)
                      .forEach(p -> {
                          String relative = basePath.relativize(p).toString();
                          boolean isDir = Files.isDirectory(p);
                          listing.append(isDir ? "[DIR]  " : "[FILE] ").append(relative).append("\n");
                          ObjectNode entry = filesArray.addObject();
                          entry.put("name", relative);
                          entry.put("type", isDir ? "directory" : "file");
                          try {
                              if (Files.isRegularFile(p)) {
                                  entry.put("size", Files.size(p));
                              }
                          } catch (IOException ignored) {}
                      });
            }

            result.put("count", filesArray.size());
            result.put("listing", listing.toString());
            if (filesArray.size() >= limit) {
                result.put("truncated", "Listing limited to " + limit + " entries. Use a subdirectory or a pattern to reduce it.");
            }
            return MAPPER.writeValueAsString(result);

        } catch (IOException e) {
            return formatOutput(false, "Error listing directory: " + e.getMessage(), null);
        }
    }

    private static boolean matchesPattern(Path path, String pattern) {
        if (pattern == null || pattern.isBlank()) {
            return true;
        }
        String fileName = path.getFileName().toString();
        String regex = pattern.replace(".", "\\.").replace("*", ".*").replace("?", ".");
        return fileName.matches(regex);
    }

    private static String formatOutput(boolean success, String message, Object data) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", success);
            node.put("message", message);
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            return "{\"success\":false,\"message\":\"Failed to format output\"}";
        }
    }
}
