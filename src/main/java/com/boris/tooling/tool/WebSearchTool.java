package com.boris.tooling.tool;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.*;
import com.boris.tooling.ToolDefinition;

/**
 * Web search using Playwright and DuckDuckGo HTML interface.
 * Uses Playwright to navigate to DuckDuckGo and extract search results.
 * 
 * Features:
 * - No API key required
 * - Headless browser automation
 * - Returns titles, URLs, and snippets
 */
public class WebSearchTool {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
    private static final String DUCKDUCKGO_URL = "https://html.duckduckgo.com/html/";
    private static final int DEFAULT_COUNT = 5;
    private static final int MAX_COUNT = 10;
    private static final int TIMEOUT_MS = 15000;

    public static ToolDefinition web_search() {
        var queryProp = Map.of("type", "string", "description", "Search query string");
        var properties = new LinkedHashMap<String, Object>();
        properties.put("query", queryProp);
        var countProp = Map.of("type", "integer", "description", "Number of results to return (1-10, default 5)");
        properties.put("count", countProp);
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return ToolDefinition.of(
                "web_search",
                "Search the web using DuckDuckGo via Playwright. Returns titles, URLs, and snippets with no API key required.",
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

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(false)  // Try non-headless first for debugging
                .setArgs(java.util.List.of("--no-sandbox")));
            Page page = browser.newPage();
            
            try {
                int count = resolveCount(args);
                List<Map<String, String>> results = searchViaDuckDuckGo(page, query, count);

                if (results.isEmpty()) {
                    return formatError("no results found");
                }

                return formatSuccess(query, results);
            } finally {
                browser.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
            return formatError("search error: " + e.getMessage() + " (Type: " + e.getClass().getSimpleName() + ")");
        }
    }

    private List<Map<String, String>> searchViaDuckDuckGo(Page page, String query, int count) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = DUCKDUCKGO_URL + "?q=" + encoded;
        
        page.navigate(url, new Page.NavigateOptions().setTimeout(TIMEOUT_MS));
        
        // Wait a bit for dynamic content
        page.waitForTimeout(3000);
        
        Object resultObj = page.evaluate("""
            () => {
                const items = document.querySelectorAll('a.result__a');
                return Array.from(items).map(item => {
                    const title = item.textContent;
                    const url = item.href;
                    const parent = item.closest('.result');
                    const snippetEl = parent ? parent.querySelector('.result__snippet') : null;
                    const snippet = snippetEl ? snippetEl.textContent : '';
                    return { title, url, snippet };
                });
            }
        """);
        
        @SuppressWarnings("unchecked")
        List<Map<String, String>> allResults = (List<Map<String, String>>) resultObj;
        
        // Limit results to requested count
        List<Map<String, String>> results = new ArrayList<>();
        for (int i = 0; i < Math.min(count, allResults.size()); i++) {
            Map<String, String> result = allResults.get(i);
            if (result.get("title") != null && !result.get("title").isEmpty() &&
                result.get("url") != null && !result.get("url").isEmpty()) {
                results.add(result);
            }
        }
        
        return results;
    }

    private int resolveCount(Map<String, Object> args) {
        Object countObj = args.get("count");
        if (countObj instanceof Integer c) {
            return Math.max(1, Math.min(MAX_COUNT, c));
        }
        return DEFAULT_COUNT;
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
        } catch (Exception e) {
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
        } catch (Exception e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }
}
