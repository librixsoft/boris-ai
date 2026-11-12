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
import com.microsoft.playwright.options.LoadState;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.util.concurrent.atomic.AtomicInteger;
import com.boris.exceptions.ChromeNotFoundException;
import com.boris.exceptions.WebSearchBlockedException;
import com.boris.exceptions.WebSearchDuplicateQueryException;
import com.boris.exceptions.WebSearchLimitException;
import com.boris.exceptions.WebSearchFallbackException;
import com.boris.tooling.ToolProgressNotifier;

import java.util.HashSet;
import java.util.Set;

/**
 * Web search using Playwright with Bing.
 * Simple, single-engine search - fast and efficient.
 *
 * Features:
 * - No API key required
 * - Single engine (Bing) - no overhead
 * - Progress notifications to UI
 * - Returns titles, URLs, and snippets
 */
public class WebSearchTool {

    private static final String BING_SEARCH_URL = "https://www.bing.com/search";
    private static final String BING_MARKET = "es-MX";
    private static final String BING_LANG = "es";
    private static final String BING_REDIRECT_PREFIX = "https://www.bing.com/ck/a";
    private static final com.fasterxml.jackson.databind.ObjectMapper MAPPER = new com.fasterxml.jackson.databind.ObjectMapper();
    private static final Properties PROPERTIES = loadProperties();
    private static final boolean HEADLESS = Boolean.parseBoolean(PROPERTIES.getProperty("web.search.headless", "true"));
    private static final String REGION = PROPERTIES.getProperty("web.search.region", "mx-es");
    private static final int DEFAULT_COUNT = Integer.parseInt(PROPERTIES.getProperty("web.search.default.count", "5"));
    private static final int MAX_COUNT = Integer.parseInt(PROPERTIES.getProperty("web.search.max.count", "10"));
    private static final int TIMEOUT_MS = Integer.parseInt(PROPERTIES.getProperty("web.search.timeout.ms", "20000"));
    private static final int WAIT_TIMEOUT_MS = Integer.parseInt(PROPERTIES.getProperty("web.search.wait.timeout.ms", "3000"));
    private static final int MAX_CALLS = Integer.parseInt(PROPERTIES.getProperty("web.search.max.calls", "1"));
    private static final AtomicInteger CALL_COUNT = new AtomicInteger(0);

    // Estado por turno: queries ya ejecutadas (normalizadas). Se resetea con resetCallCount().
    private static final Set<String> EXECUTED_QUERIES = new HashSet<>();

    private static Properties loadProperties() {
        Properties props = new Properties();
        try (InputStream input = WebSearchTool.class.getClassLoader().getResourceAsStream("application.properties")) {
            if (input != null) {
                props.load(input);
            }
        } catch (IOException e) {
            throw new com.boris.exceptions.BorisException("No se pudo cargar application.properties", e);
        }
        return props;
    }

    public static void resetCallCount() {
        CALL_COUNT.set(0);
        EXECUTED_QUERIES.clear();
    }

    public static int getCallCount() {
        return CALL_COUNT.get();
    }

    public static int getMaxCalls() {
        return MAX_CALLS;
    }

    public static int incrementCallCount() {
        return CALL_COUNT.incrementAndGet();
    }

    public static String execute(Map<String, Object> args) {
        String query = (String) args.get("query");

        if (query == null || query.isBlank()) {
            return formatError("query is required");
        }

        // Dedup: query idéntica (normalizada) en el mismo turno → excepción controlada
        String normalized = normalizeQuery(query);
        if (EXECUTED_QUERIES.contains(normalized)) {
            throw new WebSearchDuplicateQueryException(
                "Query ya ejecutada en este turno: '" + query + "'. Reformulá con más precisión o terminá tu turno.");
        }

        int currentCount = CALL_COUNT.incrementAndGet();
        if (currentCount > MAX_CALLS) {
            throw new WebSearchLimitException(
                "Límite de búsquedas web alcanzado (máximo " + MAX_CALLS + " por turno).");
        }

        // Registrar la query ejecutada (después de validar límite, antes de la búsqueda real)
        EXECUTED_QUERIES.add(normalized);

        int count = resolveCount(args);
        ToolProgressNotifier.notify("web_search", "Buscando: " + query);

        try (Playwright playwright = Playwright.create()) {
            Browser browser = BrowserLauncher.launchChrome(playwright, HEADLESS);

            BrowserContext context = browser.newContext(new Browser.NewContextOptions()
                .setLocale(REGION));

            try {
                Page page = context.newPage();

                String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
                String url = BING_SEARCH_URL + "?q=" + encoded + "&setmkt=" + BING_MARKET + "&setlang=" + BING_LANG;

                page.navigate(url, new Page.NavigateOptions().setTimeout(TIMEOUT_MS));
                page.waitForLoadState(LoadState.DOMCONTENTLOADED);

                // Detectar bloqueo anti-bot en la URL
                String currentUrl = page.url();
                if (currentUrl.contains("/challenge") || currentUrl.contains("captcha")) {
                    throw new WebSearchBlockedException(
                        "Bing bloqueó la solicitud (anti-bot): URL de desafío/captcha detectada.");
                }

                // Detectar bloqueo anti-bot en el contenido de la página
                String pageContent = page.content().toLowerCase();
                if (pageContent.contains("challenge-form") || pageContent.contains("recaptcha") ||
                    pageContent.contains("not a robot") || pageContent.contains("verify you are not a robot")) {
                    throw new WebSearchBlockedException(
                        "Bing bloqueó la solicitud (anti-bot): contenido de desafío detectado.");
                }

                // Esperar resultados reales con timeout extendido
                page.waitForSelector("li.b_algo", new Page.WaitForSelectorOptions().setTimeout(WAIT_TIMEOUT_MS));

                String html = page.content();
                List<Map<String, String>> results = parseBingHtml(html);

                if (results.isEmpty()) {
                    ToolProgressNotifier.notify("web_search", "No se encontraron resultados");
                    throw new WebSearchFallbackException(
                        "Búsqueda web sin resultados para: '" + query + "'. "
                        + "Usá contenido previamente fetchado o proporcioná la URL directamente.");
                }

                if (results.size() > count) {
                    results = results.subList(0, count);
                }

                ToolProgressNotifier.notify("web_search", "Encontrados " + results.size() + " resultados");
                return formatSuccess(query, results, "Bing");
            } finally {
                browser.close();
            }
        } catch (WebSearchLimitException e) {
            ToolProgressNotifier.notify("web_search", "Límite alcanzado (máx " + MAX_CALLS + " llamada por consulta)");
            return formatError(e.getMessage());
        } catch (WebSearchBlockedException e) {
            ToolProgressNotifier.notify("web_search", "Búsqueda bloqueada: " + e.getMessage());
            return formatError(e.getMessage());
        } catch (ChromeNotFoundException e) {
            ToolProgressNotifier.notify("web_search", "Error: " + e.getMessage());
            return formatError(e.getMessage());
        } catch (Exception e) {
            ToolProgressNotifier.notify("web_search", "Error: " + e.getMessage());
            throw new WebSearchFallbackException(
                "Búsqueda web falló para: '" + query + "'. "
                + "Usá contenido previamente fetchado o proporcioná la URL directamente.", e);
        }
    }

    /**
     * Parses a Bing SERP HTML string into a list of results.
     * Package-visible for unit testing without network.
     *
     * @param html the raw SERP HTML
     * @return list of results with title, url, snippet; empty list if no results
     * @throws WebSearchBlockedException if the page is blocked by Bing anti-bot
     */
    static List<Map<String, String>> parseBingHtml(String html) {
        if (html == null || html.isBlank()) {
            return new ArrayList<>();
        }

        // Detectar bloqueo en el HTML
        String lowerHtml = html.toLowerCase();
        if (lowerHtml.contains("challenge-form") || lowerHtml.contains("recaptcha") ||
            lowerHtml.contains("not a robot") || lowerHtml.contains("verify you are not a robot")) {
            throw new WebSearchBlockedException("Bing bloqueó la solicitud (anti-bot).");
        }

        Document doc = Jsoup.parse(html);
        Elements items = doc.select("li.b_algo");

        List<Map<String, String>> results = new ArrayList<>();
        for (Element item : items) {
            Element titleEl = item.select("h2").first();
            Element linkEl = item.select("h2 a").first();
            Element snippetEl = item.select(".b_caption p").first();

            if (titleEl == null || linkEl == null) {
                continue;
            }

            String title = titleEl.text();
            String url = resolveBingRedirectUrl(linkEl.attr("href"));
            String snippet = snippetEl != null ? snippetEl.text() : "";

            if (title.isBlank() || url.isBlank()) {
                continue;
            }

            Map<String, String> result = new LinkedHashMap<>();
            result.put("title", title);
            result.put("url", url);
            result.put("snippet", snippet);
            results.add(result);
        }

        return results;
    }

    /**
     * Resolves Bing redirect links (bing.com/ck/a?...&u=a1BASE64URL) to the real target URL.
     *
     * @param href the raw href from the SERP
     * @return the real target URL, or empty string if the redirect cannot be resolved
     */
    static String resolveBingRedirectUrl(String href) {
        if (href == null || href.isBlank() || !href.startsWith(BING_REDIRECT_PREFIX)) {
            return href;
        }
        int encodedIndex = href.indexOf("u=a1");
        if (encodedIndex < 0) {
            return "";
        }
        String encodedUrl = href.substring(encodedIndex + "u=a1".length());
        int ampersandIndex = encodedUrl.indexOf('&');
        if (ampersandIndex >= 0) {
            encodedUrl = encodedUrl.substring(0, ampersandIndex);
        }
        try {
            byte[] decoded = java.util.Base64.getUrlDecoder().decode(encodedUrl);
            return new String(decoded, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            throw new WebSearchBlockedException("Bing devolvió un redirect con URL codificada inválida.");
        }
    }

    private static int resolveCount(Map<String, Object> args) {
        Object countObj = args.get("count");
        if (countObj instanceof Integer c) {
            return Math.max(1, Math.min(MAX_COUNT, c));
        }
        if (countObj instanceof Number n) {
            return Math.max(1, Math.min(MAX_COUNT, n.intValue()));
        }
        return DEFAULT_COUNT;
    }

    /**
     * Normaliza una query para detección de duplicados: minúsculas, trim,
     * colapso de espacios internos a un solo espacio.
     */
    private static String normalizeQuery(String query) {
        return query.toLowerCase().trim().replaceAll("\\s+", " ");
    }

    private static String formatSuccess(String query, List<Map<String, String>> results, String enginesUsed) {
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

    private static String formatError(String message) {
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
