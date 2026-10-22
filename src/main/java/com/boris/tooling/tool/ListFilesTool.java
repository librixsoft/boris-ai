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
        var recursiveProp = Map.of("type", "boolean", "description", "If true, list files recursively (default: true). Use false only for a flat single-directory listing.");
        var maxDepthProp = Map.of("type", "integer", "description", "Maximum depth for recursive listing (default: 5)");
        var patternProp = Map.of("type", "string", "description", "Optional glob pattern matched against file names only (e.g., *.java, *.ts)");
        var filterProp = Map.of("type", "string", "description", "Preferred glob filter matched against the whole relative path (e.g., *Dashboard*, pom.xml, src/**/*Test.java). Enables a single recursive call instead of exploring directory by directory.");

        var properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProp);
        properties.put("filter", filterProp);
        properties.put("recursive", recursiveProp);
        properties.put("max_depth", maxDepthProp);
        properties.put("pattern", patternProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);

        return ToolDefinition.of(
                "list_files",
                "List files and directories in a given path. Lists recursively by default (depth 5) and returns a compact tree plus file names, types and sizes. To locate files by name in one call, pass a glob in 'filter' (e.g., filter: \"*Dashboard*\") instead of walking directories level by level.",
                schema);
    }

    private static final int DEFAULT_MAX_DEPTH = 5;
    private static final int LISTING_LIMIT = 500;
    private static final Set<String> DEFAULT_IGNORED_NAMES = Set.of(
            ".git", ".DS_Store", ".idea", ".gradle", "node_modules", "target", "build",
            "out", "dist", ".next", ".turbo", "venv", "__pycache__");

    public static String execute(Map<String, Object> args) {
        String pathStr = (String) args.getOrDefault("path", ".");
        Boolean recursive = (Boolean) args.getOrDefault("recursive", true);
        Integer maxDepth = args.get("max_depth") != null ? ((Number) args.get("max_depth")).intValue() : DEFAULT_MAX_DEPTH;
        String pattern = (String) args.get("pattern");
        String filter = (String) args.get("filter");

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

        boolean hasFilter = filter != null && !filter.isBlank();
        boolean walkRecursively = hasFilter || (recursive != null && recursive);

        try {
            ObjectNode result = MAPPER.createObjectNode();
            result.put("success", true);
            result.put("path", basePath.toAbsolutePath().toString());

            ArrayNode filesArray = result.putArray("entries");

            int depth = walkRecursively ? maxDepth : 1;

            Set<String> gitignoreRegexes = loadGitignorePatterns(basePath);

            List<Path> collected = new ArrayList<>();
            try (Stream<Path> stream = Files.walk(basePath, depth)) {
                stream.filter(p -> !p.equals(basePath))
                      .filter(p -> !isIgnored(basePath, p, gitignoreRegexes))
                      .filter(p -> !ProjectContext.isInsideAgentWorkspace(basePath.resolve(p).toAbsolutePath()))
                      .filter(p -> matchesPattern(p, pattern))
                      .filter(p -> !hasFilter || matchesFilter(basePath, p, filter))
                      .sorted()
                      .limit(LISTING_LIMIT)
                      .collect(Collectors.toList())
                      .forEach(collected::add);
            }

            for (Path p : collected) {
                String relative = basePath.relativize(p).toString();
                boolean isDir = Files.isDirectory(p);
                ObjectNode entry = filesArray.addObject();
                entry.put("name", relative);
                entry.put("type", isDir ? "directory" : "file");
                try {
                    if (Files.isRegularFile(p)) {
                        entry.put("size", Files.size(p));
                    }
                } catch (IOException ignored) {}
            }

            String listing = compactTree(collected, basePath);

            result.put("count", filesArray.size());
            result.put("listing", listing);
            if (filesArray.size() >= LISTING_LIMIT) {
                result.put("truncated", "Listing limited to " + LISTING_LIMIT + " entries. Use a subdirectory, a filter or a pattern to reduce it.");
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

    private static boolean matchesFilter(Path basePath, Path path, String filter) {
        String relative = toUnixString(basePath.relativize(path));
        String regex = globToPathRegex(filter);
        if (relative.matches(regex)) {
            return true;
        }
        for (int i = 0; i < path.getNameCount(); i++) {
            if (path.getName(i).toString().matches(regex)) {
                return true;
            }
        }
        return false;
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

    private static String compactTree(List<Path> entries, Path basePath) {
        Node root = new Node("", true);
        for (Path p : entries) {
            Path relative = basePath.relativize(p);
            Node current = root;
            for (int i = 0; i < relative.getNameCount(); i++) {
                String segment = relative.getName(i).toString();
                boolean isDir = i < relative.getNameCount() - 1 || Files.isDirectory(p);
                current = current.child(segment, isDir);
            }
        }
        List<String> lines = new ArrayList<>();
        for (Node child : root.children.values()) {
            lines.add(render(child));
        }
        return String.join("\n", lines) + (lines.isEmpty() ? "" : "\n");
    }

    private static String render(Node node) {
        if (!node.dir) {
            return node.name;
        }
        if (node.children.isEmpty()) {
            return node.name + "/";
        }
        if (node.children.size() == 1 && node.children.values().iterator().next().dir) {
            return node.name + "/" + render(node.children.values().iterator().next());
        }
        String childNames = node.children.values().stream()
                .map(ListFilesTool::render)
                .collect(Collectors.joining(", "));
        return node.name + "[" + childNames + "]";
    }

    private static final class Node {
        private final String name;
        private final boolean dir;
        private final Map<String, Node> children = new LinkedHashMap<>();

        private Node(String name, boolean dir) {
            this.name = name;
            this.dir = dir;
        }

        private Node child(String segment, boolean isDir) {
            Node existing = children.get(segment);
            if (existing != null) {
                return existing;
            }
            Node created = new Node(segment, isDir);
            children.put(segment, created);
            return created;
        }
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
