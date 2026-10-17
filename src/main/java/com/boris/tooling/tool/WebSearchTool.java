package com.boris.tooling.tool;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.*;
import com.boris.tooling.ToolDefinition;
import com.boris.tooling.ToolProgressNotifier;

/**
 * Web search using Playwright with PARALLEL search across multiple engines.
 * Searches Google, Bing, and DuckDuckGo simultaneously using Virtual Threads.
 *
 * Features:
 * - No API key required
 * - Parallel search across 3 engines (fastest response wins)
 * - Merges and deduplicates results from all engines
 * - Progress notifications to UI
 * - Returns titles, URLs, and snippets
 */
public class WebSearchTool {

    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
    private static final Properties PROPERTIES = loadProperties();
    private static final boolean HEADLESS = Boolean.parseBoolean(PROPERTIES.getProperty("web.search.headless", "true"));
    private static final int DEFAULT_COUNT = Integer.parseInt(PROPERTIES.getProperty("web.search.default.count", "5"));
    private static final int MAX_COUNT = Integer.parseInt(PROPERTIES.getProperty("web.search.max.count", "10"));
    private static final int TIMEOUT_MS = Integer.parseInt(PROPERTIES.getProperty("web.search.timeout.ms", "20000"));
    private static final int WAIT_TIMEOUT_MS = Integer.parseInt(PROPERTIES.getProperty("web.search.wait.timeout.ms", "3000"));
    private static final int PARALLEL_TIMEOUT_MS = 25000;

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream input = WebSearchTool.class.getClassLoader().getResourceAsStream("test.properties")) {
            if (input != null) {
                props.load(input);
            }
        } catch (IOException e) {
            // Use defaults
        }
        return props;
    }

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
                "Search the web using Google/Bing/DuckDuckGo in PARALLEL via Playwright. Returns titles, URLs, and snippets.",
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

        int count = resolveCount(args);
        ToolProgressNotifier.notify("web_search", "Buscando en paralelo (Google + Bing + DuckDuckGo): " + query);

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<EngineResult>> futures = new ArrayList<>();

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(HEADLESS)
                .setArgs(java.util.List.of("--no-sandbox", "--disable-blink-features=AutomationControlled")));

            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setLocale("es-ES"));

            try {
                Page googlePage = context.newPage();
                Page bingPage = context.newPage();
                Page duckPage = context.newPage();

                futures.add(executor.submit(() -> {
                    try {
                        List<Map<String, String>> results = searchViaGoogle(googlePage, query, count);
                        return new EngineResult("Google", results);
                    } catch (Exception e) {
                        return new EngineResult("Google", null);
                    }
                }));

                futures.add(executor.submit(() -> {
                    try {
                        List<Map<String, String>> results = searchViaBing(bingPage, query, count);
                        return new EngineResult("Bing", results);
                    } catch (Exception e) {
                        return new EngineResult("Bing", null);
                    }
                }));

                futures.add(executor.submit(() -> {
                    try {
                        List<Map<String, String>> results = searchViaDuckDuckGo(duckPage, query, count);
                        return new EngineResult("DuckDuckGo", results);
                    } catch (Exception e) {
                        return new EngineResult("DuckDuckGo", null);
                    }
                }));

                List<Map<String, String>> allResults = new ArrayList<>();
                java.util.Set<String> seenUrls = new java.util.HashSet<>();
                List<String> successfulEngines = new ArrayList<>();

                for (Future<EngineResult> future : futures) {
                    try {
                        EngineResult result = future.get(PARALLEL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                        if (result.results != null && !result.results.isEmpty()) {
                            successfulEngines.add(result.engine);
                            for (Map<String, String> item : result.results) {
                                String url = item.get("url");
                                if (url != null && !seenUrls.contains(url)) {
                                    seenUrls.add(url);
                                    allResults.add(item);
                                }
                            }
                        }
                    } catch (Exception e) {
                        // Engine failed or timed out, continue with others
                    }
                }

                if (allResults.isEmpty()) {
                    ToolProgressNotifier.notify("web_search", "No se encontraron resultados en ningún buscador");
                    return formatError("No se encontraron resultados para: " + query);
                }

                if (allResults.size() > count) {
                    allResults = allResults.subList(0, count);
                }

                String enginesUsed = String.join(" + ", successfulEngines);
                ToolProgressNotifier.notify("web_search", "Encontrados " + allResults.size() + " resultados (" + enginesUsed + ")");
                return formatSuccess(query, allResults, enginesUsed);
            } finally {
                browser.close();
            }
        } catch (Exception e) {
            ToolProgressNotifier.notify("web_search", "Error: " + e.getMessage());
            return formatError("Error en búsqueda: " + e.getMessage());
        } finally {
            executor.shutdown();
        }
    }

    private record EngineResult(String engine, List<Map<String, String>> results) {}

    private List<Map<String, String>> searchViaGoogle(Page page, String query, int count) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://www.google.com/search?q=" + encoded + "&num=" + Math.min(count + 5, 20);

            page.navigate(url, new Page.NavigateOptions().setTimeout(TIMEOUT_MS));
            page.waitForTimeout(WAIT_TIMEOUT_MS);

            try {
                Locator consentButton = page.locator("button:has-text('Accept'), button:has-text('Aceptar'), button:has-text('I agree')");
                if (consentButton.count() > 0) {
                    consentButton.first().click();
                    page.waitForTimeout(1000);
                }
            } catch (Exception ignored) {}

            Object resultObj = page.evaluate("""
                () => {
                    const results = [];

                    document.querySelectorAll('div.g, div[data-sokoban-container]').forEach(item => {
                        const linkEl = item.querySelector('a[href^="http"]');
                        const titleEl = item.querySelector('h3');
                        const snippetEl = item.querySelector('[data-sncf], .VwiC3b, span.st');

                        if (linkEl && titleEl) {
                            const url = linkEl.href;
                            if (!url.includes('google.com/search') && !url.includes('accounts.google')) {
                                results.push({
                                    title: titleEl.textContent || '',
                                    url: url,
                                    snippet: snippetEl ? snippetEl.textContent : ''
                                });
                            }
                        }
                    });

                    return results;
                }
            """);

            return parseResults(resultObj, count);
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, String>> searchViaBing(Page page, String query, int count) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://www.bing.com/search?q=" + encoded + "&count=" + count;

            page.navigate(url, new Page.NavigateOptions().setTimeout(TIMEOUT_MS));
            page.waitForTimeout(WAIT_TIMEOUT_MS);

            Object resultObj = page.evaluate("""
                () => {
                    const results = [];
                    document.querySelectorAll('li.b_algo').forEach(item => {
                        const link = item.querySelector('a');
                        const snippetEl = item.querySelector('.b_caption p, .b_caption');

                        if (link && link.href) {
                            results.push({
                                title: link.textContent || '',
                                url: link.href,
                                snippet: snippetEl ? snippetEl.textContent : ''
                            });
                        }
                    });
                    return results;
                }
            """);

            return parseResults(resultObj, count);
        } catch (Exception e) {
            return null;
        }
    }

    private List<Map<String, String>> searchViaDuckDuckGo(Page page, String query, int count) {
        try {
            String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
            String url = "https://html.duckduckgo.com/html/?q=" + encoded;

            page.navigate(url, new Page.NavigateOptions().setTimeout(TIMEOUT_MS));
            page.waitForTimeout(WAIT_TIMEOUT_MS);

            Object resultObj = page.evaluate("""
                () => {
                    const results = [];
                    document.querySelectorAll('.result, .web-result').forEach(item => {
                        const linkEl = item.querySelector('a.result__a, a.result__url');
                        const snippetEl = item.querySelector('.result__snippet, .result__body');

                        if (linkEl && linkEl.href) {
                            let url = linkEl.href;
                            if (url.includes('duckduckgo.com/l/')) {
                                const match = url.match(/uddg=([^&]+)/);
                                if (match) {
                                    url = decodeURIComponent(match[1]);
                                }
                            }
                            results.push({
                                title: linkEl.textContent || '',
                                url: url,
                                snippet: snippetEl ? snippetEl.textContent : ''
                            });
                        }
                    });
                    return results;
                }
            """);

            return parseResults(resultObj, count);
        } catch (Exception e) {
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseResults(Object resultObj, int count) {
        if (resultObj == null) return null;

        List<Map<String, String>> allResults = (List<Map<String, String>>) resultObj;
        List<Map<String, String>> results = new ArrayList<>();

        for (int i = 0; i < Math.min(count, allResults.size()); i++) {
            Map<String, String> result = allResults.get(i);
            String title = result.get("title");
            String url = result.get("url");

            if (title != null && !title.isEmpty() && url != null && !url.isEmpty()) {
                results.add(result);
            }
        }

        return results.isEmpty() ? null : results;
    }

    private int resolveCount(Map<String, Object> args) {
        Object countObj = args.get("count");
        if (countObj instanceof Integer c) {
            return Math.max(1, Math.min(MAX_COUNT, c));
        }
        if (countObj instanceof Number n) {
            return Math.max(1, Math.min(MAX_COUNT, n.intValue()));
        }
        return DEFAULT_COUNT;
    }

    private String formatSuccess(String query, List<Map<String, String>> results, String enginesUsed) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", true);
            node.put("query", query);
            node.put("engines", enginesUsed);
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
