package com.boris.tooling.tool;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.tooling.ToolDefinition;

/**
 * DuckDuckGo key-free web search. Scrapes https://html.duckduckgo.com/html
 * and parses the HTML response. No API key required.
 */
public class WebSearchTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DDG_HTML_ENDPOINT = "https://html.duckduckgo.com/html";
    private static final int DEFAULT_TIMEOUT_SECONDS = 20;
    private static final String DEFAULT_SAFE_SEARCH = "moderate";
    private static final Map<String, String> DDG_SAFE_SEARCH_PARAM = new LinkedHashMap<>();

    static {
        DDG_SAFE_SEARCH_PARAM.put("strict", "1");
        DDG_SAFE_SEARCH_PARAM.put("moderate", "-1");
        DDG_SAFE_SEARCH_PARAM.put("off", "-2");
    }

    public static ToolDefinition web_search() {
        var queryProp = Map.of("type", "string", "description", "Search query string");
        var properties = new LinkedHashMap<String, Object>();
        properties.put("query", queryProp);
        var countProp = Map.of("type", "integer", "description", "Number of results to return (1-10)");
        properties.put("count", countProp);
        var regionProp = Map.of("type", "string", "description", "Optional DuckDuckGo region code such as us-en, uk-en, or de-de");
        properties.put("region", regionProp);
        var safeSearchProp = Map.of("type", "string", "description", "SafeSearch level: strict, moderate, or off");
        properties.put("safeSearch", safeSearchProp);
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return ToolDefinition.of(
                "web_search",
                "Search the web using DuckDuckGo. Returns titles, URLs, and snippets with no API key required.",
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
            int count = resolveCount(args);
            String region = (String) args.get("region");
            String safeSearch = resolveSafeSearch(args);
            List<Map<String, String>> results = searchViaDuckDuckGo(query, count, region, safeSearch);

            if (results.isEmpty()) {
                return formatError("no results found");
            }

            return formatSuccess(query, results);
        } catch (java.net.http.HttpTimeoutException e) {
            return formatError("search timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds");
        } catch (IOException e) {
            return formatError("IO error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return formatError("search interrupted");
        }
    }

    private List<Map<String, String>> searchViaDuckDuckGo(String query, int count, String region, String safeSearch) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder(DDG_HTML_ENDPOINT);
        sb.append("?q=").append(encoded);
        if (region != null && !region.isBlank()) {
            sb.append("&kl=").append(region);
        }
        sb.append("&kp=").append(DDG_SAFE_SEARCH_PARAM.getOrDefault(safeSearch, "-1"));

        java.net.http.HttpClient client = java.net.http.HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();
        java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
            .uri(java.net.URI.create(sb.toString()))
            .GET()
            .timeout(Duration.ofSeconds(DEFAULT_TIMEOUT_SECONDS))
            .header("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36")
            .build();

        java.net.http.HttpResponse<String> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("DuckDuckGo search error (" + response.statusCode() + ")");
        }

        String html = response.body();
        if (isBotChallenge(html)) {
            throw new IOException("DuckDuckGo returned a bot-detection challenge");
        }

        List<Map<String, String>> results = parseDuckDuckGoHtml(html);

        if (results.size() > count) {
            results = results.subList(0, count);
        }

        return results;
    }

    private int resolveCount(Map<String, Object> args) {
        Object countObj = args.get("count");
        if (countObj instanceof Integer c) {
            return Math.max(1, Math.min(10, c));
        }
        return 5;
    }

    private String resolveSafeSearch(Map<String, Object> args) {
        Object ssObj = args.get("safeSearch");
        if (ssObj instanceof String ss) {
            String normalized = ss.trim().toLowerCase();
            if (normalized.equals("strict") || normalized.equals("moderate") || normalized.equals("off")) {
                return normalized;
            }
        }
        return DEFAULT_SAFE_SEARCH;
    }

    private boolean isBotChallenge(String html) {
        if (html == null) return false;
        if (Pattern.compile("class=\"[^\"]*\\bresult__a\\b[^\"]*\"").matcher(html).find()) return false;
        return Pattern.compile("g-recaptcha", Pattern.CASE_INSENSITIVE).matcher(html).find() ||
               Pattern.compile("are you a human", Pattern.CASE_INSENSITIVE).matcher(html).find() ||
               Pattern.compile("id=\"challenge-form\"", Pattern.CASE_INSENSITIVE).matcher(html).find() ||
               Pattern.compile("name=\"challenge\"", Pattern.CASE_INSENSITIVE).matcher(html).find();
    }

    private List<Map<String, String>> parseDuckDuckGoHtml(String html) {
        List<Map<String, String>> results = new ArrayList<>();
        if (html == null) return results;

        Pattern resultPattern = Pattern.compile(
            "<a\\b(?=[^>]*\\bclass=\"[^\"]*\\bresult__a\\b[^\"]*\")([^>]*)>([\\s\\S]*?)</a>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        );
        Matcher resultMatcher = resultPattern.matcher(html);

        while (resultMatcher.find()) {
            String rawAttributes = resultMatcher.group(1);
            String rawTitle = resultMatcher.group(2);
            String rawUrl = extractHref(rawAttributes);

            int matchEnd = resultMatcher.end();
            String trailingHtml = html.substring(matchEnd);
            Pattern nextResultPattern = Pattern.compile(
                "<a\\b(?=[^>]*\\bclass=\"[^\"]*\\bresult__a\\b[^\"]*\")[^>]*>",
                Pattern.CASE_INSENSITIVE
            );
            Matcher nextResultMatcher = nextResultPattern.matcher(trailingHtml);
            int nextResultIndex = nextResultMatcher.find() ? nextResultMatcher.start() : -1;
            String scopedTrailingHtml = nextResultIndex >= 0 ? trailingHtml.substring(0, nextResultIndex) : trailingHtml;

            Pattern snippetPattern = Pattern.compile(
                "<a\\b(?=[^>]*\\bclass=\"[^\"]*\\bresult__snippet\\b[^\"]*\")[^>]*>([\\s\\S]*?)</a>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
            );
            Matcher snippetMatcher = snippetPattern.matcher(scopedTrailingHtml);
            String rawSnippet = snippetMatcher.find() ? snippetMatcher.group(1) : "";

            String title = decodeHtmlEntities(rawTitle);
            String url = decodeDuckDuckGoUrl(decodeHtmlEntities(rawUrl));
            String snippet = decodeHtmlEntities(stripHtml(rawSnippet));

            if (!title.isEmpty() && !url.isEmpty()) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("title", title);
                entry.put("url", url);
                entry.put("snippet", snippet);
                results.add(entry);
            }
        }

        return results;
    }

    private String extractHref(String attributes) {
        if (attributes == null) return "";
        Matcher m = Pattern.compile("\\bhref=\"([^\"]*)\"").matcher(attributes);
        return m.find() ? m.group(1) : "";
    }

    private String decodeHtmlEntities(String text) {
        if (text == null) return "";
        return text
            .replaceAll("&lt;", "<")
            .replaceAll("&gt;", ">")
            .replaceAll("&quot;", "\"")
            .replaceAll("&apos;", "'")
            .replaceAll("&#39;", "'")
            .replaceAll("&#x27;", "'")
            .replaceAll("&#x2f;", "/")
            .replaceAll("&nbsp;", " ")
            .replaceAll("&ndash;", "-")
            .replaceAll("&mdash;", "--")
            .replaceAll("&hellip;", "...")
            .replaceAll("&amp;", "&");
    }

    private String stripHtml(String html) {
        if (html == null) return "";
        return html.replaceAll("<[^>]+>", " ").replaceAll("\\s+", " ").trim();
    }

    private String decodeDuckDuckGoUrl(String rawUrl) {
        if (rawUrl == null) return "";
        try {
            String normalized = rawUrl.startsWith("//") ? "https:" + rawUrl : rawUrl;
            Matcher m = Pattern.compile("uddg=([^&]+)").matcher(normalized);
            if (m.find()) {
                return java.net.URLDecoder.decode(m.group(1), StandardCharsets.UTF_8.name());
            }
        } catch (Exception ignored) {}
        return rawUrl;
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
                obj.put("snippet", r.getOrDefault("snippet", ""));
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
}
