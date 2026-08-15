package com.boris.tooling.tool;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.tooling.ToolDefinition;

public class WebSearchTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern GOOGLE_TITLE = Pattern.compile("<h3[^>]*>(.*?)</h3>", Pattern.DOTALL);
    private static final Pattern GOOGLE_LINK = Pattern.compile("<a[^>]*href\\s*=\\s*(?:\"([^\"]+)\"|'([^']+)'|([^\\s>]+))", Pattern.DOTALL);
    private static final Pattern GOOGLE_SNIPPET = Pattern.compile("<span[^>]*>(.*?)</span>", Pattern.DOTALL);
    private static final Pattern GOOGLE_RESULT_DIV = Pattern.compile("<div[^>]*class\\s*=\\s*\"([^\"]*G[^\"]*)\"[^>]*>(.*?)</div>", Pattern.DOTALL);

    private final HttpFetcher httpFetcher;

    public WebSearchTool() {
        this.httpFetcher = createDefaultFetcher();
    }

    public WebSearchTool(HttpFetcher httpFetcher) {
        this.httpFetcher = httpFetcher;
    }

    public static ToolDefinition web_search() {
        var queryProp = Map.of("type", "string", "description", "Search query string");
        var properties = new LinkedHashMap<String, Object>();
        properties.put("query", queryProp);
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return ToolDefinition.of(
                "web_search",
                "Search Google for current information. Returns structured JSON results with title, URL, and snippet.",
                schema);
    }

    public static String execute(Map<String, Object> args) {
        WebSearchTool tool = new WebSearchTool();
        return tool.search(args);
    }

    String search(Map<String, Object> args) {
        @SuppressWarnings("unchecked")
        String query = (String) args.get("query");

        if (query == null || query.isBlank()) {
            return formatError("Error: query is required");
        }

        try {
            HttpResponse<String> response = fetchGoogleResults(query);

            if (response.statusCode() != 200) {
                return formatError("Error: HTTP error: " + response.statusCode());
            }

            List<Map<String, String>> results = parseGoogleHtml(response.body());

            return formatSuccess(query, results);
        } catch (java.net.http.HttpTimeoutException e) {
            return formatError("Error: search timed out after 5 seconds");
        } catch (IOException e) {
            return formatError("Error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return formatError("Error: search interrupted");
        }
    }

    private HttpResponse<String> fetchGoogleResults(String query) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://www.google.com/search?q=" + encoded + "&num=10&hl=es";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "BorisCLI/1.0 (Linux; x64)")
                .header("Accept-Language", "es-MX,es;q=0.9")
                .build();

        return httpFetcher.send(request, Duration.ofSeconds(5));
    }

    private List<Map<String, String>> parseGoogleHtml(String html) {
        List<Map<String, String>> results = new ArrayList<>();

        Matcher divMatcher = GOOGLE_RESULT_DIV.matcher(html);
        while (divMatcher.find()) {
            String divContent = divMatcher.group(2);
            if (divContent == null) continue;

            String title = extractTitle(divContent);
            String url = extractUrl(divContent);
            String snippet = extractSnippet(divContent);

            if (title != null && url != null) {
                Map<String, String> result = new LinkedHashMap<>();
                result.put("title", title);
                result.put("url", url);
                result.put("snippet", snippet != null ? snippet : "");
                results.add(result);

                if (results.size() >= 10) break;
            }
        }

        return results;
    }

    private String extractTitle(String html) {
        Matcher m = GOOGLE_TITLE.matcher(html);
        if (m.find()) {
            return m.group(1).replaceAll("<[^>]+>", "").trim();
        }
        return null;
    }

    private String extractUrl(String html) {
        Matcher m = GOOGLE_LINK.matcher(html);
        if (m.find()) {
            String url = m.group(1) != null ? m.group(1) : (m.group(2) != null ? m.group(2) : m.group(3));
            if (url != null && url.startsWith("http")) {
                return cleanGoogleUrl(url);
            }
        }
        return null;
    }

    private String extractSnippet(String html) {
        Matcher m = GOOGLE_SNIPPET.matcher(html);
        while (m.find()) {
            String text = m.group(1).replaceAll("<[^>]+>", "").trim();
            if (text != null && text.length() > 50) {
                return text;
            }
        }
        return null;
    }

    private String cleanGoogleUrl(String url) {
        return url.replaceAll("[?&](utm_[^&=]+|sa_|ved)=([^&]*)", "");
    }

    private String formatSuccess(String query, List<Map<String, String>> results) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", true);
            node.put("query", query);

            ArrayNode array = node.putArray("results");
            for (Map<String, String> r : results) {
                ObjectNode obj = array.addObject();
                obj.put("title", r.get("title"));
                obj.put("url", r.get("url"));
                obj.put("snippet", r.get("snippet"));
            }
            node.putNull("error");

            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }

    private String formatError(String message) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", false);
            node.put("query", "");
            node.putArray("results");
            node.put("error", message);
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }

    private HttpFetcher createDefaultFetcher() {
        return (request, timeout) -> {
            java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            return client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());
        };
    }
}
