package com.boris.tooling.tool;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.tooling.ToolDefinition;
import com.boris.tooling.ToolProgressNotifier;

/**
 * Search GitHub using the REST API without cloning repositories.
 *
 * Use this tool when you need to:
 * - Find repositories by keywords or topics
 * - Search code across GitHub without cloning
 * - Find issues/PRs with solutions to problems
 * - Get README content quickly
 * - Explore repository structure via API
 *
 * This is faster than github_repo_search for initial exploration
 * since it doesn't require cloning the entire repository.
 */
public class GitHubApiSearchTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GITHUB_API = "https://api.github.com";
    private static final int TIMEOUT_MS = 15000;
    private static final int MAX_CONTENT_LENGTH = 50000;

    public static ToolDefinition github_api_search() {
        var searchTypeProp = Map.of(
            "type", "string",
            "description", "Search type: 'repos' (find repositories), 'code' (search code across GitHub), " +
                          "'readme' (get repo README), 'issues' (search issues/PRs), 'files' (list repo files), " +
                          "'file_content' (get specific file content). Default: repos"
        );
        var queryProp = Map.of(
            "type", "string",
            "description", "Search query. For 'repos': keywords/topics. For 'code': code pattern + optional qualifiers " +
                          "(e.g., 'HttpClient language:java'). For 'issues': problem description. For 'readme/files/file_content': owner/repo format"
        );
        var pathProp = Map.of(
            "type", "string",
            "description", "For 'file_content': path to the file within the repo. For 'files': subdirectory to list (optional)"
        );
        var languageProp = Map.of(
            "type", "string",
            "description", "Filter by programming language (e.g., 'java', 'typescript', 'python')"
        );
        var maxResultsProp = Map.of(
            "type", "integer",
            "description", "Maximum results to return (1-30). Default: 10"
        );

        var properties = new LinkedHashMap<String, Object>();
        properties.put("search_type", searchTypeProp);
        properties.put("query", queryProp);
        properties.put("path", pathProp);
        properties.put("language", languageProp);
        properties.put("max_results", maxResultsProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("query"));

        return ToolDefinition.of(
            "github_api_search",
            "Search GitHub via API without cloning. Find repos, search code, get README, search issues/PRs, or fetch file contents. " +
            "Faster than github_repo_search for exploration and documentation lookup.",
            schema
        );
    }

    public static String execute(Map<String, Object> args) {
        GitHubApiSearchTool tool = new GitHubApiSearchTool();
        return tool.search(args);
    }

    String search(Map<String, Object> args) {
        String searchType = (String) args.getOrDefault("search_type", "repos");
        String query = (String) args.get("query");
        String path = (String) args.getOrDefault("path", null);
        String language = (String) args.getOrDefault("language", null);
        Integer maxResults = args.get("max_results") != null ?
            ((Number) args.get("max_results")).intValue() : 10;

        if (query == null || query.isBlank()) {
            return formatError("query is required");
        }

        maxResults = Math.max(1, Math.min(30, maxResults));

        try {
            switch (searchType.toLowerCase()) {
                case "repos":
                    return searchRepositories(query, language, maxResults);
                case "code":
                    return searchCode(query, language, maxResults);
                case "readme":
                    return getReadme(query);
                case "issues":
                    return searchIssues(query, language, maxResults);
                case "files":
                    return listFiles(query, path);
                case "file_content":
                    return getFileContent(query, path);
                default:
                    return formatError("Unknown search_type: " + searchType + ". Use: repos, code, readme, issues, files, file_content");
            }
        } catch (Exception e) {
            return formatError("API request failed: " + e.getMessage());
        }
    }

    private String searchRepositories(String query, String language, int maxResults) throws Exception {
        ToolProgressNotifier.notify("github_api_search", "Buscando repositorios: " + query);

        StringBuilder urlBuilder = new StringBuilder(GITHUB_API + "/search/repositories?q=");
        urlBuilder.append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        if (language != null && !language.isBlank()) {
            urlBuilder.append("+language:").append(URLEncoder.encode(language, StandardCharsets.UTF_8));
        }
        urlBuilder.append("&per_page=").append(maxResults);
        urlBuilder.append("&sort=stars&order=desc");

        JsonNode response = makeRequest(urlBuilder.toString());

        ObjectNode result = MAPPER.createObjectNode();
        result.put("success", true);
        result.put("search_type", "repos");
        result.put("query", query);
        result.put("total_count", response.path("total_count").asInt(0));

        ArrayNode repos = result.putArray("results");
        for (JsonNode item : response.path("items")) {
            ObjectNode repo = repos.addObject();
            repo.put("name", item.path("full_name").asText());
            repo.put("description", item.path("description").asText(""));
            repo.put("url", item.path("html_url").asText());
            repo.put("stars", item.path("stargazers_count").asInt());
            repo.put("language", item.path("language").asText(""));
            repo.put("updated_at", item.path("updated_at").asText());

            ArrayNode topics = repo.putArray("topics");
            for (JsonNode topic : item.path("topics")) {
                topics.add(topic.asText());
            }
        }

        result.putNull("error");
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
    }

    private String searchCode(String query, String language, int maxResults) throws Exception {
        ToolProgressNotifier.notify("github_api_search", "Buscando código: " + query);

        StringBuilder urlBuilder = new StringBuilder(GITHUB_API + "/search/code?q=");
        urlBuilder.append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        if (language != null && !language.isBlank()) {
            urlBuilder.append("+language:").append(URLEncoder.encode(language, StandardCharsets.UTF_8));
        }
        urlBuilder.append("&per_page=").append(maxResults);

        JsonNode response = makeRequest(urlBuilder.toString());

        ObjectNode result = MAPPER.createObjectNode();
        result.put("success", true);
        result.put("search_type", "code");
        result.put("query", query);
        result.put("total_count", response.path("total_count").asInt(0));

        ArrayNode codeResults = result.putArray("results");
        for (JsonNode item : response.path("items")) {
            ObjectNode code = codeResults.addObject();
            code.put("file", item.path("name").asText());
            code.put("path", item.path("path").asText());
            code.put("repo", item.path("repository").path("full_name").asText());
            code.put("url", item.path("html_url").asText());

            // Include text matches if available
            ArrayNode matches = code.putArray("matches");
            for (JsonNode match : item.path("text_matches")) {
                ObjectNode m = matches.addObject();
                m.put("fragment", match.path("fragment").asText());
            }
        }

        result.putNull("error");
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
    }

    private String getReadme(String repo) throws Exception {
        repo = normalizeRepo(repo);
        ToolProgressNotifier.notify("github_api_search", "Obteniendo README de: " + repo);

        String url = GITHUB_API + "/repos/" + repo + "/readme";
        JsonNode response = makeRequest(url);

        ObjectNode result = MAPPER.createObjectNode();
        result.put("success", true);
        result.put("search_type", "readme");
        result.put("repo", repo);

        String content = response.path("content").asText("");
        if (!content.isEmpty()) {
            // GitHub returns base64 encoded content
            byte[] decoded = Base64.getMimeDecoder().decode(content);
            String readmeContent = new String(decoded, StandardCharsets.UTF_8);

            if (readmeContent.length() > MAX_CONTENT_LENGTH) {
                readmeContent = readmeContent.substring(0, MAX_CONTENT_LENGTH) + "\n\n[... truncated ...]";
            }
            result.put("content", readmeContent);
            result.put("path", response.path("path").asText());
        } else {
            result.put("content", "");
        }

        result.putNull("error");
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
    }

    private String searchIssues(String query, String language, int maxResults) throws Exception {
        ToolProgressNotifier.notify("github_api_search", "Buscando issues/PRs: " + query);

        StringBuilder urlBuilder = new StringBuilder(GITHUB_API + "/search/issues?q=");
        urlBuilder.append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        if (language != null && !language.isBlank()) {
            urlBuilder.append("+language:").append(URLEncoder.encode(language, StandardCharsets.UTF_8));
        }
        urlBuilder.append("&per_page=").append(maxResults);
        urlBuilder.append("&sort=reactions-+1&order=desc");

        JsonNode response = makeRequest(urlBuilder.toString());

        ObjectNode result = MAPPER.createObjectNode();
        result.put("success", true);
        result.put("search_type", "issues");
        result.put("query", query);
        result.put("total_count", response.path("total_count").asInt(0));

        ArrayNode issues = result.putArray("results");
        for (JsonNode item : response.path("items")) {
            ObjectNode issue = issues.addObject();
            issue.put("title", item.path("title").asText());
            issue.put("url", item.path("html_url").asText());
            issue.put("state", item.path("state").asText());
            issue.put("is_pr", item.has("pull_request"));
            issue.put("repo", extractRepoFromUrl(item.path("repository_url").asText()));
            issue.put("comments", item.path("comments").asInt());
            issue.put("reactions", item.path("reactions").path("total_count").asInt());

            String body = item.path("body").asText("");
            if (body.length() > 500) {
                body = body.substring(0, 500) + "...";
            }
            issue.put("body_preview", body);

            ArrayNode labels = issue.putArray("labels");
            for (JsonNode label : item.path("labels")) {
                labels.add(label.path("name").asText());
            }
        }

        result.putNull("error");
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
    }

    private String listFiles(String repo, String path) throws Exception {
        repo = normalizeRepo(repo);
        ToolProgressNotifier.notify("github_api_search", "Listando archivos de: " + repo);

        String url = GITHUB_API + "/repos/" + repo + "/contents";
        if (path != null && !path.isBlank()) {
            url += "/" + path;
        }

        JsonNode response = makeRequest(url);

        ObjectNode result = MAPPER.createObjectNode();
        result.put("success", true);
        result.put("search_type", "files");
        result.put("repo", repo);
        result.put("path", path != null ? path : "/");

        ArrayNode files = result.putArray("files");
        if (response.isArray()) {
            for (JsonNode item : response) {
                ObjectNode file = files.addObject();
                file.put("name", item.path("name").asText());
                file.put("type", item.path("type").asText());
                file.put("path", item.path("path").asText());
                file.put("size", item.path("size").asInt());
            }
        }

        result.putNull("error");
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
    }

    private String getFileContent(String repo, String path) throws Exception {
        repo = normalizeRepo(repo);
        if (path == null || path.isBlank()) {
            return formatError("path is required for file_content search type");
        }

        ToolProgressNotifier.notify("github_api_search", "Obteniendo: " + repo + "/" + path);

        String url = GITHUB_API + "/repos/" + repo + "/contents/" + path;
        JsonNode response = makeRequest(url);

        ObjectNode result = MAPPER.createObjectNode();
        result.put("success", true);
        result.put("search_type", "file_content");
        result.put("repo", repo);
        result.put("path", path);

        String content = response.path("content").asText("");
        if (!content.isEmpty()) {
            byte[] decoded = Base64.getMimeDecoder().decode(content);
            String fileContent = new String(decoded, StandardCharsets.UTF_8);

            if (fileContent.length() > MAX_CONTENT_LENGTH) {
                fileContent = fileContent.substring(0, MAX_CONTENT_LENGTH) + "\n\n[... truncated ...]";
            }
            result.put("content", fileContent);
        } else {
            result.put("content", "");
        }

        result.put("size", response.path("size").asInt());
        result.putNull("error");
        return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(result);
    }

    private JsonNode makeRequest(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/vnd.github.v3+json");
        conn.setRequestProperty("User-Agent", "Boris-AI-Agent");
        // Request text matches for code search
        conn.setRequestProperty("Accept", "application/vnd.github.text-match+json");
        conn.setConnectTimeout(TIMEOUT_MS);
        conn.setReadTimeout(TIMEOUT_MS);

        int responseCode = conn.getResponseCode();

        BufferedReader reader;
        if (responseCode >= 200 && responseCode < 300) {
            reader = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        } else {
            reader = new BufferedReader(new InputStreamReader(conn.getErrorStream()));
            StringBuilder error = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                error.append(line);
            }
            reader.close();

            if (responseCode == 403) {
                throw new RuntimeException("GitHub API rate limit exceeded. Try again later or use github_repo_search instead.");
            } else if (responseCode == 404) {
                throw new RuntimeException("Not found. Check repository name or path.");
            }
            throw new RuntimeException("HTTP " + responseCode + ": " + error);
        }

        StringBuilder response = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            response.append(line);
        }
        reader.close();

        return MAPPER.readTree(response.toString());
    }

    private String normalizeRepo(String repo) {
        if (repo == null) return "";
        repo = repo.trim();
        // Remove GitHub URL prefix if present
        repo = repo.replaceFirst("^https?://github\\.com/", "");
        repo = repo.replaceFirst("^git@github\\.com:", "");
        repo = repo.replaceFirst("\\.git$", "");
        // Remove trailing slash
        repo = repo.replaceFirst("/$", "");
        return repo;
    }

    private String extractRepoFromUrl(String repoUrl) {
        if (repoUrl == null) return "";
        return repoUrl.replaceFirst("^https://api\\.github\\.com/repos/", "");
    }

    private String formatError(String message) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", false);
            node.put("search_type", "");
            node.put("query", "");
            node.put("error", message);
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"" + message + "\"}";
        }
    }
}
