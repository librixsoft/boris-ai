package com.boris.tooling.tool;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.tooling.ToolDefinition;

/**
 * Usa SearXNG como metamotor de búsqueda: agrega resultados de Google,
 * Bing, DuckDuckGo, Wikipedia, etc. en un solo endpoint JSON.
 * No requiere API key ni pago.
 *
 * Usa una lista de instancias públicas con fallback: si una devuelve
 * error, intenta la siguiente. Para producción se recomienda
 * self-hostear una instancia de SearXNG.
 */
public class WebSearchTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final HttpFetcher httpFetcher;

    private static final List<String> SEARXNG_INSTANCES = List.of(
        "https://searx.be",
        "https://searx.tiekoetter.com",
        "https://search.sapti.me",
        "https://searxng.lexie.dev",
        "https://search.ononoki.org"
    );

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
                "Search via SearXNG (aggregates Google, Bing, DuckDuckGo, Wikipedia, etc.) for current information. Returns a JSON with title, url, engine, and summarized content.",
                schema);
    }

    public static String execute(Map<String, Object> args) {
        WebSearchTool tool = new WebSearchTool();
        return tool.search(args);
    }

    String search(Map<String, Object> args) {
        String query = (String) args.get("query");

        if (query == null || query.isBlank()) {
            return formatError("query is required");
        }

        try {
            List<Map<String, String>> results = searchViaSearXNG(query);

            if (results.isEmpty()) {
                return formatError("no results found via any SearXNG instance");
            }

            Map<String, String> first = results.get(0);
            String rawContent = fetchAndExtractContent(first.get("url"));
            String summary = summarize(rawContent);
            first.put("content", summary != null ? summary : "");

            return formatSuccess(query, results);
        } catch (java.net.http.HttpTimeoutException e) {
            return formatError("search timed out after 15 seconds");
        } catch (IOException e) {
            return formatError("IO error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return formatError("search interrupted");
        }
    }

    private List<Map<String, String>> searchViaSearXNG(String query) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        List<Map<String, String>> results = new ArrayList<>();

        for (String instance : SEARXNG_INSTANCES) {
            try {
                String url = instance + "/search?q=" + encoded + "&format=json&categories=general&language=en";
                HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                    .header("Accept", "application/json")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .build();

                HttpResponse<String> response = httpFetcher.send(request, Duration.ofSeconds(10));

                if (response.statusCode() == 200 && response.body() != null && !response.body().isEmpty()) {
                    results = parseSearXNGJson(response.body());
                    if (!results.isEmpty()) {
                        break;
                    }
                }
            } catch (Exception e) {
                // Try next instance
            }
        }

        return results;
    }

    private List<Map<String, String>> parseSearXNGJson(String jsonBody) {
        List<Map<String, String>> results = new ArrayList<>();
        try {
            JsonNode root = MAPPER.readTree(jsonBody);
            JsonNode resultsNode = root.get("results");
            if (resultsNode == null || !resultsNode.isArray()) return results;

            for (JsonNode result : resultsNode) {
                String title = result.has("title") ? result.get("title").asText() : "";
                String url = result.has("url") ? result.get("url").asText() : "";
                String snippet = result.has("content") ? result.get("content").asText() : "";

                if (title.isEmpty() || url.isEmpty()) continue;

                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("title", title);
                entry.put("url", url);
                entry.put("snippet", snippet);
                entry.put("engine", result.has("engine") ? result.get("engine").asText() : "");
                results.add(entry);

                if (results.size() >= 10) break;
            }
        } catch (IOException ignored) {}
        return results;
    }

    private String fetchAndExtractContent(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml")
                    .build();

            HttpResponse<String> response = httpFetcher.send(request, Duration.ofSeconds(5));

            if (response.statusCode() != 200) {
                return null;
            }

            Document doc = Jsoup.parse(response.body());
            doc.select("script, style, nav, footer, header, noscript").remove();
            String text = doc.body() != null ? doc.body().text() : doc.text();

            if (text.length() > 5000) {
                text = text.substring(0, 5000);
            }

            return text;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return null;
        }
    }

    private String summarize(String rawContent) {
        if (rawContent == null || rawContent.isBlank()) return "";

        String[] sentences = rawContent.split("(?<=[.!?])\\s+");
        StringBuilder sb = new StringBuilder();
        for (String s : sentences) {
            if (sb.length() + s.length() > 800) break;
            sb.append(s).append(" ");
        }
        String result = sb.toString().trim();
        return result.isEmpty() ? rawContent.substring(0, Math.min(800, rawContent.length())) : result;
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
                obj.put("content", r.getOrDefault("content", ""));
                obj.put("engine", r.getOrDefault("engine", ""));
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
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString());
        };
    }
}
