package com.boris.tooling.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

        var properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);

        return ToolDefinition.of(
                "list_files",
                "List files and directories in a given path. Returns a flat, readable listing: directories first with prefix 'd ', files with prefix 'f ' and readable size. Use a subdirectory path to narrow the scope.",
                schema);
    }

    private static final int LISTING_LIMIT = 500;
    private static final Set<String> DEFAULT_IGNORED_NAMES = Set.of(
            ".git", ".DS_Store", ".idea", ".gradle", "node_modules", "target", "build",
            "out", "dist", ".next", ".turbo", "venv", "__pycache__");

    public static String execute(Map<String, Object> args) {
        String pathStr = (String) args.getOrDefault("path", ".");

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

        Path absoluteBase = basePath.toAbsolutePath().normalize();

        if (ProjectContext.isInsideAgentWorkspace(absoluteBase)) {
            return formatOutput(false,
                "Access to .boris (agent internal workspace: index, dashboard) is denied.", null);
        }

        try {
            ObjectNode result = MAPPER.createObjectNode();
            result.put("success", true);
            result.put("path", basePath.toAbsolutePath().toString());

            ArrayNode filesArray = result.putArray("entries");

            Set<String> gitignoreRegexes = loadGitignorePatterns(basePath);

            List<Path> collected = new ArrayList<>();
            try (Stream<Path> stream = Files.walk(basePath, 1)) {
                stream.filter(p -> !p.equals(basePath))
                      .filter(p -> !isIgnored(basePath, p, gitignoreRegexes))
                      .filter(p -> !ProjectContext.isInsideAgentWorkspace(basePath.resolve(p).toAbsolutePath()))
                      .sorted()
                      .limit(LISTING_LIMIT)
                      .collect(Collectors.toList())
                      .forEach(collected::add);
            }

            // Separar directorios y archivos para mostrar directorios primero
            List<Path> dirs = new ArrayList<>();
            List<Path> files = new ArrayList<>();
            for (Path p : collected) {
                if (Files.isDirectory(p)) {
                    dirs.add(p);
                } else {
                    files.add(p);
                }
            }

            int count = 0;
            StringBuilder listing = new StringBuilder();

            for (Path p : dirs) {
                String relative = basePath.relativize(p).toString();
                listing.append("d ").append(relative).append("/\n");
                count++;
            }
            for (Path p : files) {
                String relative = basePath.relativize(p).toString();
                long size = 0;
                try {
                    size = Files.size(p);
                } catch (IOException ignored) {}
                listing.append("f ").append(relative).append(" (").append(readableSize(size)).append(")\n");
                count++;
            }

            result.put("count", count);
            result.put("listing", listing.toString());
            if (count >= LISTING_LIMIT) {
                result.put("truncated", "Listing limited to " + LISTING_LIMIT + " entries. Use a subdirectory path to narrow the scope.");
                listing.append("... ").append(count - LISTING_LIMIT).append(" entradas más en este directorio\n");
            }

            return MAPPER.writeValueAsString(result);

        } catch (IOException e) {
            return formatOutput(false, "Error listing directory: " + e.getMessage(), null);
        }
    }

    private static String readableSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return String.format("%.1f KB", bytes / 1024.0);
        } else {
            return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
        }
    }

    private static boolean isIgnored(Path basePath, Path path, Set<String> gitignoreRegexes) {
        Path relative = basePath.relativize(path);
        for (int i = 0; i < relative.getNameCount(); i++) {
            if (DEFAULT_IGNORED_NAMES.contains(relative.getName(i).toString())) {
                return true;
            }
        }
        for (String regex : gitignoreRegexes) {
            for (int i = 0; i < relative.getNameCount(); i++) {
                if (relative.getName(i).toString().matches(regex)) {
                    return true;
                }
            }
            if (toUnixString(relative).matches(regex)) {
                return true;
            }
        }
        return false;
    }

    private static Set<String> loadGitignorePatterns(Path basePath) {
        Set<String> regexes = new java.util.HashSet<>(loadGitignoreFile(basePath));
        Path projectRoot = ProjectContext.projectRootOrCurrent();
        if (projectRoot != null && !projectRoot.equals(basePath.toAbsolutePath().normalize())) {
            regexes.addAll(loadGitignoreFile(projectRoot));
        }
        return regexes;
    }

    private static Set<String> loadGitignoreFile(Path dir) {
        Path gitignore = dir.resolve(".gitignore");
        if (!Files.isRegularFile(gitignore)) {
            return Set.of();
        }
        try {
            List<String> lines = Files.readAllLines(gitignore);
            Set<String> regexes = new java.util.HashSet<>();
            for (String raw : lines) {
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#") || line.startsWith("!")) {
                    continue;
                }
                String pattern = line.endsWith("/") ? line.substring(0, line.length() - 1) : line;
                regexes.add(globToPathRegex(pattern));
            }
            return regexes;
        } catch (IOException e) {
            return Set.of();
        }
    }

    private static String globToPathRegex(String glob) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                if (i + 1 < glob.length() && glob.charAt(i + 1) == '*') {
                    sb.append(".*");
                    i++;
                } else {
                    sb.append("[^/]*");
                }
            } else if (c == '?') {
                sb.append("[^/]");
            } else {
                sb.append(String.valueOf(c).replace("[", "\\[").replace("]", "\\]"));
            }
        }
        return sb.toString();
    }

    private static String toUnixString(Path path) {
        return path.toString().replace('\\', '/');
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
