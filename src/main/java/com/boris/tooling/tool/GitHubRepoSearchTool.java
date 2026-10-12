package com.boris.tooling.tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.tooling.ToolDefinition;
import com.boris.tooling.ToolProgressNotifier;

/**
 * Search code in GitHub repositories by cloning them temporarily.
 *
 * Use this tool when you need to:
 * - Find how something is implemented in a GitHub repo
 * - Search for code patterns, components, or examples
 * - Explore a repo's structure
 *
 * DO NOT use this for:
 * - Searching in Maven dependencies (use the source repo instead)
 * - Local project code (use read_file/list_files instead)
 */
public class GitHubRepoSearchTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int CLONE_TIMEOUT_SECONDS = 60;
    private static final int SEARCH_TIMEOUT_SECONDS = 30;
    private static final int MAX_FILE_CONTENT_LENGTH = 5000;
    private static final int DEFAULT_MAX_RESULTS = 10;

    public static ToolDefinition github_repo_search() {
        var repoProp = Map.of(
            "type", "string",
            "description", "GitHub repository URL or owner/repo format (e.g., 'angular/angular' or 'https://github.com/angular/angular')"
        );
        var queryProp = Map.of(
            "type", "string",
            "description", "Search query: text to find in file content or filename pattern"
        );
        var searchTypeProp = Map.of(
            "type", "string",
            "description", "Search type: 'content' (grep in files), 'filename' (find by name), or 'structure' (list directory tree). Default: content"
        );
        var filePatternProp = Map.of(
            "type", "string",
            "description", "File extension filter (e.g., '*.java', '*.ts', '*.py'). Default: all files"
        );
        var pathProp = Map.of(
            "type", "string",
            "description", "Subdirectory to search in (e.g., 'src/main', 'packages/core'). Default: entire repo"
        );
        var branchProp = Map.of(
            "type", "string",
            "description", "Branch to clone. Default: default branch (usually main/master)"
        );
        var maxResultsProp = Map.of(
            "type", "integer",
            "description", "Maximum results to return (1-50). Default: 10"
        );
        var showContentProp = Map.of(
            "type", "boolean",
            "description", "Include file content in results. Default: true for content search, false for filename/structure"
        );

        var properties = new LinkedHashMap<String, Object>();
        properties.put("repo", repoProp);
        properties.put("query", queryProp);
        properties.put("search_type", searchTypeProp);
        properties.put("file_pattern", filePatternProp);
        properties.put("path", pathProp);
        properties.put("branch", branchProp);
        properties.put("max_results", maxResultsProp);
        properties.put("show_content", showContentProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("repo"));

        return ToolDefinition.of(
            "github_repo_search",
            "Search code in a GitHub repository. Clones the repo temporarily and searches for files or content. " +
            "Use this instead of searching in Maven JARs - always search in the source repository.",
            schema
        );
    }

    public static String execute(Map<String, Object> args) {
        GitHubRepoSearchTool tool = new GitHubRepoSearchTool();
        return tool.search(args);
    }

    String search(Map<String, Object> args) {
        String repo = (String) args.get("repo");
        String query = (String) args.getOrDefault("query", "");
        String searchType = (String) args.getOrDefault("search_type", "content");
        String filePattern = (String) args.getOrDefault("file_pattern", null);
        String subPath = (String) args.getOrDefault("path", null);
        String branch = (String) args.getOrDefault("branch", null);
        Integer maxResults = args.get("max_results") != null ?
            ((Number) args.get("max_results")).intValue() : DEFAULT_MAX_RESULTS;
        Boolean showContent = (Boolean) args.getOrDefault("show_content", null);

        if (repo == null || repo.isBlank()) {
            return formatError("repo is required. Provide GitHub URL or owner/repo format.");
        }

        String repoUrl = normalizeRepoUrl(repo);
        maxResults = Math.max(1, Math.min(50, maxResults));

        if (showContent == null) {
            showContent = "content".equalsIgnoreCase(searchType);
        }

        Path tempDir = null;
        try {
            tempDir = Files.createTempDirectory("github-search-" + UUID.randomUUID().toString().substring(0, 8));
            ToolProgressNotifier.notify("github_repo_search", "Clonando " + repoUrl + "...");

            if (!cloneRepo(repoUrl, tempDir, branch)) {
                return formatError("Failed to clone repository: " + repoUrl);
            }

            Path searchPath = tempDir;
            if (subPath != null && !subPath.isBlank()) {
                searchPath = tempDir.resolve(subPath);
                if (!Files.exists(searchPath)) {
                    return formatError("Path not found in repository: " + subPath);
                }
            }

            ToolProgressNotifier.notify("github_repo_search", "Buscando en el repositorio...");

            List<SearchResult> results;
            switch (searchType.toLowerCase()) {
                case "filename":
                    results = searchByFilename(searchPath, query, filePattern, maxResults);
                    break;
                case "structure":
                    results = getRepoStructure(searchPath, filePattern, maxResults);
                    break;
                case "content":
                default:
                    if (query == null || query.isBlank()) {
                        return formatError("query is required for content search");
                    }
                    results = searchByContent(searchPath, query, filePattern, maxResults);
                    break;
            }

            if (showContent && !results.isEmpty()) {
                loadFileContents(tempDir, results);
            }

            ToolProgressNotifier.notify("github_repo_search",
                "Encontrados " + results.size() + " resultados");

            return formatSuccess(repoUrl, searchType, query, results);
        } catch (Exception e) {
            return formatError("Search failed: " + e.getMessage());
        } finally {
            if (tempDir != null) {
                cleanupTempDir(tempDir);
            }
        }
    }

    private String normalizeRepoUrl(String repo) {
        repo = repo.trim();
        if (repo.startsWith("https://github.com/") || repo.startsWith("git@github.com:")) {
            return repo.endsWith(".git") ? repo : repo + ".git";
        }
        if (repo.matches("^[\\w.-]+/[\\w.-]+$")) {
            return "https://github.com/" + repo + ".git";
        }
        return repo.endsWith(".git") ? repo : repo + ".git";
    }

    private boolean cloneRepo(String repoUrl, Path targetDir, String branch) {
        try {
            List<String> command = new ArrayList<>();
            command.add("git");
            command.add("clone");
            command.add("--depth");
            command.add("1");
            command.add("--single-branch");
            if (branch != null && !branch.isBlank()) {
                command.add("--branch");
                command.add(branch);
            }
            command.add(repoUrl);
            command.add(targetDir.toString());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            boolean completed = process.waitFor(CLONE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private List<SearchResult> searchByContent(Path searchPath, String query, String filePattern, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        try {
            List<String> command = new ArrayList<>();
            command.add("grep");
            command.add("-r");
            command.add("-n");
            command.add("-l");
            command.add("--include=" + (filePattern != null ? filePattern : "*"));
            command.add("-m");
            command.add(String.valueOf(maxResults * 2));
            command.add(query);
            command.add(searchPath.toString());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null && results.size() < maxResults) {
                Path filePath = Path.of(line.trim());
                if (Files.exists(filePath) && Files.isRegularFile(filePath)) {
                    String relativePath = searchPath.relativize(filePath).toString();
                    results.add(new SearchResult(relativePath, filePath, null, null));
                }
            }
            process.waitFor(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (results.isEmpty()) {
                results = searchByContentWithContext(searchPath, query, filePattern, maxResults);
            }
        } catch (Exception e) {
            // Fallback silently
        }
        return results;
    }

    private List<SearchResult> searchByContentWithContext(Path searchPath, String query, String filePattern, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        try {
            List<String> command = new ArrayList<>();
            command.add("grep");
            command.add("-r");
            command.add("-n");
            command.add("-B");
            command.add("2");
            command.add("-A");
            command.add("5");
            command.add("--include=" + (filePattern != null ? filePattern : "*"));
            command.add(query);
            command.add(searchPath.toString());

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            StringBuilder currentMatch = new StringBuilder();
            String currentFile = null;
            int lineNumber = 0;

            String line;
            while ((line = reader.readLine()) != null && results.size() < maxResults) {
                if (line.contains(":") && !line.startsWith("--")) {
                    int colonIdx = line.indexOf(':');
                    int secondColon = line.indexOf(':', colonIdx + 1);
                    if (secondColon > colonIdx) {
                        String filePart = line.substring(0, colonIdx);
                        String linePart = line.substring(colonIdx + 1, secondColon);

                        if (!filePart.equals(currentFile) && currentFile != null) {
                            Path filePath = Path.of(currentFile);
                            String relativePath = searchPath.relativize(filePath).toString();
                            results.add(new SearchResult(relativePath, filePath, lineNumber, currentMatch.toString().trim()));
                            currentMatch = new StringBuilder();
                        }

                        currentFile = filePart;
                        try {
                            lineNumber = Integer.parseInt(linePart);
                        } catch (NumberFormatException e) {
                            lineNumber = 0;
                        }
                        currentMatch.append(line.substring(secondColon + 1)).append("\n");
                    }
                } else if (line.equals("--")) {
                    if (currentFile != null && currentMatch.length() > 0) {
                        Path filePath = Path.of(currentFile);
                        String relativePath = searchPath.relativize(filePath).toString();
                        results.add(new SearchResult(relativePath, filePath, lineNumber, currentMatch.toString().trim()));
                        currentMatch = new StringBuilder();
                        currentFile = null;
                    }
                } else {
                    currentMatch.append(line).append("\n");
                }
            }

            if (currentFile != null && currentMatch.length() > 0) {
                Path filePath = Path.of(currentFile);
                String relativePath = searchPath.relativize(filePath).toString();
                results.add(new SearchResult(relativePath, filePath, lineNumber, currentMatch.toString().trim()));
            }

            process.waitFor(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Fallback silently
        }
        return results;
    }

    private List<SearchResult> searchByFilename(Path searchPath, String query, String filePattern, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        try {
            String pattern = query;
            if (pattern == null || pattern.isBlank()) {
                pattern = filePattern != null ? filePattern : "*";
            } else if (!pattern.contains("*")) {
                pattern = "*" + pattern + "*";
            }

            List<String> command = List.of("find", searchPath.toString(), "-type", "f", "-name", pattern);
            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null && results.size() < maxResults) {
                Path filePath = Path.of(line.trim());
                if (Files.exists(filePath) && !filePath.toString().contains("/.git/")) {
                    String relativePath = searchPath.relativize(filePath).toString();
                    results.add(new SearchResult(relativePath, filePath, null, null));
                }
            }
            process.waitFor(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Fallback silently
        }
        return results;
    }

    private List<SearchResult> getRepoStructure(Path searchPath, String filePattern, int maxResults) {
        List<SearchResult> results = new ArrayList<>();
        try {
            List<String> command = new ArrayList<>();
            command.add("find");
            command.add(searchPath.toString());
            command.add("-type");
            command.add("f");
            if (filePattern != null && !filePattern.isBlank()) {
                command.add("-name");
                command.add(filePattern);
            }

            ProcessBuilder pb = new ProcessBuilder(command);
            pb.redirectErrorStream(true);
            Process process = pb.start();

            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            String line;
            while ((line = reader.readLine()) != null && results.size() < maxResults) {
                Path filePath = Path.of(line.trim());
                if (Files.exists(filePath) && !filePath.toString().contains("/.git/")) {
                    String relativePath = searchPath.relativize(filePath).toString();
                    results.add(new SearchResult(relativePath, filePath, null, null));
                }
            }
            process.waitFor(SEARCH_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            // Fallback silently
        }
        return results;
    }

    private void loadFileContents(Path repoRoot, List<SearchResult> results) {
        for (SearchResult result : results) {
            try {
                if (result.fullPath != null && Files.exists(result.fullPath)) {
                    String content = Files.readString(result.fullPath);
                    if (content.length() > MAX_FILE_CONTENT_LENGTH) {
                        content = content.substring(0, MAX_FILE_CONTENT_LENGTH) + "\n... [truncated]";
                    }
                    result.content = content;
                }
            } catch (Exception e) {
                // Skip file if can't read
            }
        }
    }

    private void cleanupTempDir(Path tempDir) {
        try {
            Files.walk(tempDir)
                .sorted(Comparator.reverseOrder())
                .map(Path::toFile)
                .forEach(File::delete);
        } catch (Exception e) {
            // Best effort cleanup
        }
    }

    private String formatSuccess(String repo, String searchType, String query, List<SearchResult> results) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", true);
            node.put("repo", repo);
            node.put("search_type", searchType);
            node.put("query", query != null ? query : "");
            node.put("result_count", results.size());

            ArrayNode resultsArray = node.putArray("results");
            for (SearchResult result : results) {
                ObjectNode resultNode = resultsArray.addObject();
                resultNode.put("file", result.relativePath);
                if (result.lineNumber != null) {
                    resultNode.put("line", result.lineNumber);
                }
                if (result.matchContext != null && !result.matchContext.isBlank()) {
                    resultNode.put("match_context", result.matchContext);
                }
                if (result.content != null && !result.content.isBlank()) {
                    resultNode.put("content", result.content);
                }
            }

            node.putNull("error");
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }

    private String formatError(String message) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", false);
            node.put("repo", "");
            node.put("search_type", "");
            node.put("query", "");
            node.put("result_count", 0);
            node.putArray("results");
            node.put("error", message);
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }

    private static class SearchResult {
        String relativePath;
        Path fullPath;
        Integer lineNumber;
        String matchContext;
        String content;

        SearchResult(String relativePath, Path fullPath, Integer lineNumber, String matchContext) {
            this.relativePath = relativePath;
            this.fullPath = fullPath;
            this.lineNumber = lineNumber;
            this.matchContext = matchContext;
        }
    }
}
