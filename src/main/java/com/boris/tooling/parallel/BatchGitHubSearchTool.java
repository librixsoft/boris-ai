package com.boris.tooling.parallel;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.task.TaskAborter;
import com.boris.tooling.ToolProgressNotifier;

/**
 * Batch GitHub API search tool using PARALLEL queries with Virtual Threads.
 * Executes multiple GitHub API queries simultaneously.
 *
 * Features:
 * - Parallel execution of multiple queries
 * - Single query works exactly like old GitHubApiSearchTool
 * - Supports repos, code, readme, issues, files, file_content
 * - Rate limit aware
 * - Progress notifications to UI
 */
public class BatchGitHubSearchTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String GITHUB_API = "https://api.github.com";
    private static final int TIMEOUT_MS = 15000;
    private static final int PARALLEL_TIMEOUT_MS = 30000;
    private static final int MAX_CONTENT_LENGTH = 50000;

    public record SearchSpec(String searchType, String query, String path, String language, Integer maxResults) {}
    public record SearchResult(String searchType, String query, boolean success, ObjectNode data, String error) {}

    public String execute(List<SearchSpec> specs, TaskAborter aborter) {
        if (specs == null || specs.isEmpty()) {
            return formatError("queries list is empty");
        }

        List<SearchSpec> validSpecs = specs.stream()
            .filter(s -> s.query() != null && !s.query().isBlank())
            .toList();

        if (validSpecs.isEmpty()) {
            return formatError("no valid queries provided");
        }

        int totalQueries = validSpecs.size();

        if (totalQueries == 1) {
            SearchSpec spec = validSpecs.get(0);
            String type = spec.searchType() != null ? spec.searchType() : "repos";
            ToolProgressNotifier.notify("github_api_search", "Consultando GitHub: " + type + " - " + spec.query());
        } else {
            ToolProgressNotifier.notify("github_api_search", "Ejecutando " + totalQueries + " consultas GitHub en paralelo...");
        }

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<SearchResult>> futures = new ArrayList<>();

        try {
            for (SearchSpec spec : validSpecs) {
                futures.add(executor.submit(() -> executeSearch(spec, aborter)));
            }

            List<SearchResult> results = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;

            for (Future<SearchResult> future : futures) {
                try {
                    SearchResult result = future.get(PARALLEL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    results.add(result);
                    if (result.success()) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                } catch (Exception e) {
                    results.add(new SearchResult("unknown", "unknown", false, null, "Query timed out or failed: " + e.getMessage()));
                    failCount++;
                }
            }

            if (totalQueries == 1) {
                return formatSingleResult(results.get(0));
            } else {
                ToolProgressNotifier.notify("github_api_search", "Completado: " + successCount + " exitosas, " + failCount + " fallidas");
                return formatBatchResults(results, successCount, failCount);
            }
        } finally {
            executor.shutdown();
        }
    }

    public String execute(List<SearchSpec> specs) {
        return execute(specs, null);
    }

    private SearchResult executeSearch(SearchSpec spec, TaskAborter aborter) {
        if (aborter != null && aborter.isAborted()) {
            return new SearchResult(spec.searchType(), spec.query(), false, null, "Aborted");
        }

        String searchType = spec.searchType() != null ? spec.searchType() : "repos";
        String query = spec.query();
        String path = spec.path();
        String language = spec.language();
        int maxResults = spec.maxResults() != null ? Math.max(1, Math.min(30, spec.maxResults())) : 10;

        try {
            ObjectNode data = switch (searchType.toLowerCase()) {
                case "repos" -> searchRepositories(query, language, maxResults);
                case "code" -> searchCode(query, language, maxResults);
                case "readme" -> getReadme(query);
                case "issues" -> searchIssues(query, language, maxResults);
                case "files" -> listFiles(query, path);
                case "file_content" -> getFileContent(query, path);
                default -> throw new RuntimeException("Unknown search_type: " + searchType);
            };
            return new SearchResult(searchType, query, true, data, null);
        } catch (Exception e) {
            return new SearchResult(searchType, query, false, null, e.getMessage());
        }
    }

    private ObjectNode searchRepositories(String query, String language, int maxResults) throws Exception {
        StringBuilder urlBuilder = new StringBuilder(GITHUB_API + "/search/repositories?q=");
        urlBuilder.append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        if (language != null && !language.isBlank()) {
            urlBuilder.append("+language:").append(URLEncoder.encode(language, StandardCharsets.UTF_8));
        }
        urlBuilder.append("&per_page=").append(maxResults);
        urlBuilder.append("&sort=stars&order=desc");

        JsonNode response = makeRequest(urlBuilder.toString());

        ObjectNode result = MAPPER.createObjectNode();
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

        return result;
    }

    private ObjectNode searchCode(String query, String language, int maxResults) throws Exception {
        StringBuilder urlBuilder = new StringBuilder(GITHUB_API + "/search/code?q=");
        urlBuilder.append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        if (language != null && !language.isBlank()) {
            urlBuilder.append("+language:").append(URLEncoder.encode(language, StandardCharsets.UTF_8));
        }
        urlBuilder.append("&per_page=").append(maxResults);

        JsonNode response = makeRequest(urlBuilder.toString());

        ObjectNode result = MAPPER.createObjectNode();
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

            ArrayNode matches = code.putArray("matches");
            for (JsonNode match : item.path("text_matches")) {
                ObjectNode m = matches.addObject();
                m.put("fragment", match.path("fragment").asText());
            }
        }

        return result;
    }

    private ObjectNode getReadme(String repo) throws Exception {
        repo = normalizeRepo(repo);
        String url = GITHUB_API + "/repos/" + repo + "/readme";
        JsonNode response = makeRequest(url);

        ObjectNode result = MAPPER.createObjectNode();
        result.put("search_type", "readme");
        result.put("repo", repo);

        String content = response.path("content").asText("");
        if (!content.isEmpty()) {
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

        return result;
    }

    private ObjectNode searchIssues(String query, String language, int maxResults) throws Exception {
        StringBuilder urlBuilder = new StringBuilder(GITHUB_API + "/search/issues?q=");
        urlBuilder.append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        if (language != null && !language.isBlank()) {
            urlBuilder.append("+language:").append(URLEncoder.encode(language, StandardCharsets.UTF_8));
        }
        urlBuilder.append("&per_page=").append(maxResults);
        urlBuilder.append("&sort=reactions-+1&order=desc");

        JsonNode response = makeRequest(urlBuilder.toString());

        ObjectNode result = MAPPER.createObjectNode();
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

        return result;
    }

    private ObjectNode listFiles(String repo, String path) throws Exception {
        repo = normalizeRepo(repo);
        String url = GITHUB_API + "/repos/" + repo + "/contents";
        if (path != null && !path.isBlank()) {
            url += "/" + path;
        }

        JsonNode response = makeRequest(url);

        ObjectNode result = MAPPER.createObjectNode();
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

        return result;
    }

    private ObjectNode getFileContent(String repo, String path) throws Exception {
        repo = normalizeRepo(repo);
        if (path == null || path.isBlank()) {
            throw new RuntimeException("path is required for file_content search type");
        }

        String url = GITHUB_API + "/repos/" + repo + "/contents/" + path;
        JsonNode response = makeRequest(url);

        ObjectNode result = MAPPER.createObjectNode();
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
        return result;
    }

    private JsonNode makeRequest(String urlString) throws Exception {
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setRequestProperty("Accept", "application/vnd.github.text-match+json");
        conn.setRequestProperty("User-Agent", "Boris-AI-Agent");
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
                throw new RuntimeException("GitHub API rate limit exceeded. Try again later.");
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
        repo = repo.replaceFirst("^https?://github\\.com/", "");
        repo = repo.replaceFirst("^git@github\\.com:", "");
        repo = repo.replaceFirst("\\.git$", "");
        repo = repo.replaceFirst("/$", "");
        return repo;
    }

    private String extractRepoFromUrl(String repoUrl) {
        if (repoUrl == null) return "";
        return repoUrl.replaceFirst("^https://api\\.github\\.com/repos/", "");
    }

    private String formatSingleResult(SearchResult result) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", result.success());
            node.put("search_type", result.searchType());
            node.put("query", result.query());

            if (result.success() && result.data() != null) {
                node.setAll(result.data());
            }

            if (result.error() != null) {
                node.put("error", result.error());
            } else {
                node.putNull("error");
            }

            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(node);
        } catch (Exception e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }

    private String formatBatchResults(List<SearchResult> results, int successCount, int failCount) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("totalQueries", results.size());
            root.put("successfulQueries", successCount);
            root.put("failedQueries", failCount);

            ArrayNode queriesArray = root.putArray("results");
            for (SearchResult r : results) {
                ObjectNode item = queriesArray.addObject();
                item.put("search_type", r.searchType());
                item.put("query", r.query());
                item.put("success", r.success());

                if (r.success() && r.data() != null) {
                    item.set("data", r.data());
                } else {
                    item.put("error", r.error());
                }
            }

            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }

    private String formatError(String message) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", false);
            node.put("error", message);
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"" + message + "\"}";
        }
    }

    public void shutdown() {
        // No persistent executor to shut down
    }
}
