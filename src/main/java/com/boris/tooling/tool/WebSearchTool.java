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

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.*;
import java.util.concurrent.atomic.AtomicInteger;
import com.boris.tooling.ToolDefinition;
import com.boris.tooling.ToolProgressNotifier;

/**
 * Web search using Playwright with DuckDuckGo.
 * Simple, single-engine search - fast and efficient.
 *
 * Features:
 * - No API key required
 * - Single engine (DuckDuckGo) - no overhead
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
    private static final int MAX_CALLS = Integer.parseInt(PROPERTIES.getProperty("web.search.max.calls", "1"));
    private static final AtomicInteger CALL_COUNT = new AtomicInteger(0);

    private static Properties loadProperties() {
        Properties props = new Properties();
        for (String resource : List.of("application.properties", "test.properties")) {
            try (InputStream input = WebSearchTool.class.getClassLoader().getResourceAsStream(resource)) {
                if (input != null) {
                    props.load(input);
                }
            } catch (IOException ignored) {
            }
        }
        return props;
    }

    public static void resetCallCount() {
        CALL_COUNT.set(0);
    }

    public static int getCallCount() {
        return CALL_COUNT.get();
    }

    public static int incrementCallCount() {
        return CALL_COUNT.incrementAndGet();
    }

    public static int getMaxCalls() {
        return MAX_CALLS;
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
                "Search the web using DuckDuckGo via Playwright. Returns titles, URLs, and snippets.",
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

        int currentCount = CALL_COUNT.incrementAndGet();
        if (currentCount > MAX_CALLS) {
            String msg = "Límite de búsquedas web alcanzado (máximo " + MAX_CALLS + " llamada por consulta). "
                    + "No intentes más búsquedas web con queries diferentes. "
                    + "Utiliza las herramientas locales del proyecto como github_repo, list_files, batch_read_files o responde con la información disponible.";
            ToolProgressNotifier.notify("web_search", "Límite alcanzado (máx " + MAX_CALLS + " llamada por consulta)");
            return formatError(msg);
        }

        int count = resolveCount(args);
        ToolProgressNotifier.notify("web_search", "Buscando: " + query);

        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setHeadless(HEADLESS)
                .setArgs(java.util.List.of("--no-sandbox", "--disable-blink-features=AutomationControlled")));

            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                .setLocale("es-ES"));

            try {
                Page page = context.newPage();

                // Usar solo DuckDuckGo (más simple, sin bloqueos)
                List<Map<String, String>> results = searchViaDuckDuckGo(page, query, count);

                if (results == null || results.isEmpty()) {
                    ToolProgressNotifier.notify("web_search", "No se encontraron resultados");
                    return formatError("No se encontraron resultados para: " + query);
                }

                if (results.size() > count) {
                    results = results.subList(0, count);
                }

                ToolProgressNotifier.notify("web_search", "Encontrados " + results.size() + " resultados");
                return formatSuccess(query, results, "DuckDuckGo");
            } finally {
                browser.close();
            }
        } catch (Exception e) {
            ToolProgressNotifier.notify("web_search", "Error: " + e.getMessage());
            return formatError("Error en búsqueda: " + e.getMessage());
        }
    }


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
