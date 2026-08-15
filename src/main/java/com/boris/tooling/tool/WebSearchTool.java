package com.boris.tooling.tool;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
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

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.tooling.ToolDefinition;

/**
 * Usa DuckDuckGo HTML (html.duckduckgo.com/html/) en vez de Google:
 * no requiere API key, no muestra páginas de consentimiento/captcha
 * agresivas, y su markup es mucho más estable (clases fijas
 * result__a / result__snippet en vez de hashes que cambian).
 *
 * Sigue siendo scraping de HTML, no una API oficial: puede romperse
 * si DuckDuckGo cambia su markup, y no está pensado para volumen alto.
 */
public class WebSearchTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Pattern UDDG_PARAM = Pattern.compile("[?&]uddg=([^&]+)");

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
                "Search DuckDuckGo for current information. Fetches the first result and returns a JSON with title, url, and summarized content.",
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
            HttpResponse<String> response = fetchDuckDuckGoResults(query);

            if (response.statusCode() != 200) {
                return formatError("HTTP " + response.statusCode());
            }

            List<Map<String, String>> results = parseDuckDuckGoHtml(response.body());

            if (results.isEmpty()) {
                return formatSuccess(query, new ArrayList<>());
            }

            Map<String, String> first = results.get(0);
            String rawContent = fetchAndExtractContent(first.get("url"));
            String summary = summarize(rawContent);

            first.put("content", summary != null ? summary : "");

            return formatSuccess(query, results);
        } catch (java.net.http.HttpTimeoutException e) {
            return formatError("search timed out after 10 seconds");
        } catch (IOException e) {
            return formatError("IO error: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return formatError("search interrupted");
        }
    }

    private HttpResponse<String> fetchDuckDuckGoResults(String query) throws IOException, InterruptedException {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://html.duckduckgo.com/html/?q=" + encoded + "&kl=mx-es";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .GET()
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Accept-Language", "es-MX,es;q=0.9")
                .build();

        return httpFetcher.send(request, Duration.ofSeconds(5));
    }

    private List<Map<String, String>> parseDuckDuckGoHtml(String html) {
        List<Map<String, String>> results = new ArrayList<>();
        Document doc = Jsoup.parse(html);

        Elements titleLinks = doc.select("a.result__a");

        for (Element link : titleLinks) {
            String title = link.text().trim();
            String url = cleanDuckDuckGoUrl(link.attr("href"));
            if (title.isEmpty() || url == null || !url.startsWith("http")) continue;

            // El snippet vive en un hermano/contenedor cercano con la clase result__snippet
            Element resultBlock = link.closest(".result, .web-result");
            String snippet = null;
            if (resultBlock != null) {
                Element snippetEl = resultBlock.selectFirst(".result__snippet");
                if (snippetEl != null) {
                    snippet = snippetEl.text().trim();
                }
            }

            Map<String, String> result = new LinkedHashMap<>();
            result.put("title", title);
            result.put("url", url);
            result.put("snippet", snippet != null ? snippet : "");
            results.add(result);

            if (results.size() >= 10) break;
        }

        return results;
    }

    /**
     * DuckDuckGo envuelve los links reales en un redirect propio:
     * //duckduckgo.com/l/?uddg=<url-encoded-real-url>&rut=...
     * Aquí lo desenvolvemos para regresar la URL real.
     */
    private String cleanDuckDuckGoUrl(String href) {
        if (href == null) return null;

        Matcher m = UDDG_PARAM.matcher(href);
        if (m.find()) {
            return URLDecoder.decode(m.group(1), StandardCharsets.UTF_8);
        }

        if (href.startsWith("//")) {
            href = "https:" + href;
        }
        return href;
    }

    private String fetchAndExtractContent(String url) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(5))
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
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

    /**
     * Resumen extractivo: se queda con las primeras oraciones completas
     * hasta un límite de caracteres. Para un resumen semántico real habría
     * que mandar rawContent a un modelo (p. ej. la API de Anthropic).
     */
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