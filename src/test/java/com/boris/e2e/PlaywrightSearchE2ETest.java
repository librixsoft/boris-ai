package com.boris.e2e;

import com.boris.context.ProjectContext;
import com.boris.context.SearchSessionContext;
import com.boris.context.SessionDirectoryResolver;
import com.boris.exceptions.BorisException;
import com.boris.exceptions.PlanModeException;
import com.boris.exceptions.WebSearchDuplicateQueryException;
import com.boris.exceptions.WebSearchLimitException;
import com.boris.tooling.tool.SearchContextWriter;
import com.boris.tooling.tool.WebSearchTool;
import com.boris.tooling.tool.WriteTool;
import com.boris.cli.ui.PlanMode;
import com.boris.cli.ui.PlanModeManager;
import com.microsoft.playwright.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end test for Playwright-based web search.
 * EXCLUSIVAMENTE Bing. Prohibido probar otros buscadores.
 * This test is disabled by default and can be enabled with -DenableE2ETests=true.
 */
@EnabledIf("isE2ETestEnabled")
public class PlaywrightSearchE2ETest {

    private static final String BING_SEARCH_URL = "https://www.bing.com/search";
    private static final String BING_MARKET = "es-MX";
    private static final String BING_LANG = "es";
    private static final String BING_REDIRECT_PREFIX = "https://www.bing.com/ck/a";

    @TempDir
    Path tempDir;

    private static final String SESSION_ID = "e2e-test-session";

    @BeforeEach
    void setUp() {
        WebSearchTool.resetCallCount();
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.setSessionId(SESSION_ID);
    }

    @AfterEach
    void tearDown() {
        ProjectContext.clear();
        SearchSessionContext.clear();
    }

    @Test
    void playwrightBingSearch_retrievesResults() {
        try (Playwright playwright = Playwright.create()) {
            Browser browser = playwright.chromium().launch(new BrowserType.LaunchOptions()
                .setChannel("chrome")
                .setHeadless(true)
                .setArgs(java.util.List.of("--no-sandbox")));
            Page page = browser.newPage();

            try {
                System.out.println("Navigating to Bing...");
                String encodedQuery = java.net.URLEncoder.encode("java 21 programming", java.nio.charset.StandardCharsets.UTF_8);
                page.navigate(BING_SEARCH_URL + "?q=" + encodedQuery + "&setmkt=" + BING_MARKET + "&setlang=" + BING_LANG,
                    new Page.NavigateOptions().setTimeout(15000));

                System.out.println("Page URL after navigation: " + page.url());
                System.out.println("Page title: " + page.title());

                System.out.println("Waiting for Bing results...");
                page.waitForSelector("li.b_algo", new Page.WaitForSelectorOptions().setTimeout(30000));

                System.out.println("Extracting results...");
                Object resultObj = page.evaluate("""
                    () => {
                        const results = [];
                        const items = document.querySelectorAll('li.b_algo');
                        for (const item of items) {
                            const titleEl = item.querySelector('h2');
                            const linkEl = item.querySelector('h2 a');
                            const snippetEl = item.querySelector('.b_caption p');
                            if (titleEl && linkEl) {
                                let url = linkEl.href;
                                if (url.startsWith('https://www.bing.com/ck/a')) {
                                    const m = url.match(/[?&]u=a1([A-Za-z0-9\\-_]+)/);
                                    if (m) {
                                        url = atob(m[1].replace(/-/g, '+').replace(/_/g, '/'));
                                    }
                                }
                                results.push({
                                    title: titleEl.textContent || '',
                                    url: url,
                                    snippet: snippetEl ? snippetEl.textContent : ''
                                });
                            }
                        }
                        return results;
                    }
                """);

                @SuppressWarnings("unchecked")
                List<Map<String, String>> results = (List<Map<String, String>>) resultObj;

                System.out.println("Results found: " + results.size());

                assertFalse(results.isEmpty(), "Should find at least one Bing result");
                for (Map<String, String> result : results) {
                    System.out.println("Title: " + result.get("title"));
                    System.out.println("URL: " + result.get("url"));
                    System.out.println("Snippet: " + result.get("snippet"));
                    System.out.println("---");

                    assertFalse(result.get("title").isEmpty(), "Title should not be empty");
                    assertFalse(result.get("url").isEmpty(), "URL should not be empty");
                    assertTrue(result.get("url").startsWith("http"), "URL should be a valid http/https URL");
                    assertFalse(result.get("url").contains("bing.com/ck/a"), "URL should not contain bing redirect");
                }

            } finally {
                browser.close();
            }
        }
    }

    @Test
    void webSearchTool_e2e_retrievesValidJsonResults() {
        String result = com.boris.tooling.tool.WebSearchTool.execute(
            java.util.Map.of("query", "java 21 programming", "count", 5)
        );

        System.out.println("WebSearchTool result: " + result);

        assertNotNull(result, "Result should not be null");
        assertTrue(result.contains("\"success\":true"), "Result should indicate success");
        assertTrue(result.contains("\"results\""), "Result should contain results array");
        assertTrue(result.contains("\"title\""), "Result should contain title fields");
        assertTrue(result.contains("\"url\""), "Result should contain url fields");
        assertTrue(result.contains("\"snippet\""), "Result should contain snippet fields");
    }

    @Test
    void webSearchTool_e2e_respectsMaxCallsPerTurn() {
        com.boris.tooling.tool.WebSearchTool.resetCallCount();

        // First call should succeed
        String result1 = com.boris.tooling.tool.WebSearchTool.execute(
            java.util.Map.of("query", "java 21 programming", "count", 3)
        );
        assertTrue(result1.contains("\"success\":true"), "First call should succeed");

        // Second call in same turn: MAX_CALLS=3 so it should still succeed
        String result2 = com.boris.tooling.tool.WebSearchTool.execute(
            java.util.Map.of("query", "another query", "count", 3)
        );
        assertTrue(result2.contains("\"success\":true"), "Second call should succeed (MAX_CALLS=3)");

        // Third call in same turn: MAX_CALLS=3 so it should still succeed
        String result3 = com.boris.tooling.tool.WebSearchTool.execute(
            java.util.Map.of("query", "third query", "count", 3)
        );
        assertTrue(result3.contains("\"success\":true"), "Third call should succeed (MAX_CALLS=3)");

        // Fourth call in same turn should be blocked by limit (MAX_CALLS=3)
        WebSearchLimitException limitException = assertThrows(WebSearchLimitException.class,
            () -> com.boris.tooling.tool.WebSearchTool.execute(
                java.util.Map.of("query", "fourth query", "count", 3)
            ));
        assertTrue(limitException.getMessage().toLowerCase().contains("límite"),
            "Exception should mention limit");

        // After reset, should work again
        com.boris.tooling.tool.WebSearchTool.resetCallCount();
        String result5 = com.boris.tooling.tool.WebSearchTool.execute(
            java.util.Map.of("query", "java 21 programming", "count", 3)
        );
        assertTrue(result5.contains("\"success\":true"), "Call after reset should succeed");
    }

    @Test
    void webSearchTool_e2e_respectsDedupPerTurn() {
        com.boris.tooling.tool.WebSearchTool.resetCallCount();

        // First call: ejecuta la búsqueda (puede fallar por red, pero registra la query)
        com.boris.tooling.tool.WebSearchTool.execute(
            java.util.Map.of("query", "java 21 programming", "count", 3)
        );

        // Second call with same query: debe lanzar dedup ANTES de incrementar el contador
        com.boris.exceptions.WebSearchDuplicateQueryException dedupException = assertThrows(
            com.boris.exceptions.WebSearchDuplicateQueryException.class,
            () -> com.boris.tooling.tool.WebSearchTool.execute(
                java.util.Map.of("query", "java 21 programming", "count", 3)
            ));
        assertTrue(dedupException.getMessage().contains("ya ejecutada en este turno"),
            "El mensaje de dedup debe ser explícito");
    }

    // ========== Nuevos tests de registro de sesión ==========

    @Test
    void webSearchTool_e2e_dedupSameQueryThrowsException() {
        com.boris.tooling.tool.WebSearchTool.resetCallCount();

        // First call: registra la query
        com.boris.tooling.tool.WebSearchTool.execute(
            java.util.Map.of("query", "java 21 programming", "count", 3)
        );

        // Second call with same query: debe lanzar dedup
        WebSearchDuplicateQueryException dedupException = assertThrows(
            WebSearchDuplicateQueryException.class,
            () -> com.boris.tooling.tool.WebSearchTool.execute(
                java.util.Map.of("query", "java 21 programming", "count", 3)
            ));
        assertTrue(dedupException.getMessage().contains("ya ejecutada en este turno"),
            "El mensaje de dedup debe ser explícito");
    }

    @Test
    void webSearchTool_e2e_dedupDifferentQueryAllowed() {
        com.boris.tooling.tool.WebSearchTool.resetCallCount();

        // First call
        String result1 = com.boris.tooling.tool.WebSearchTool.execute(
            java.util.Map.of("query", "java 21 programming", "count", 3)
        );
        assertTrue(result1.contains("\"success\":true"), "First call should succeed");

        // Second call with different query: debe ser permitida
        String result2 = com.boris.tooling.tool.WebSearchTool.execute(
            java.util.Map.of("query", "spring boot 3 features", "count", 3)
        );
        assertTrue(result2.contains("\"success\":true"), "Second call with different query should succeed");
    }

    @Test
    void webSearchTool_e2e_returnsValidJsonStructure() {
        com.boris.tooling.tool.WebSearchTool.resetCallCount();

        String result = com.boris.tooling.tool.WebSearchTool.execute(
            java.util.Map.of("query", "java 21 programming", "count", 5)
        );

        assertNotNull(result, "Result should not be null");
        assertTrue(result.contains("\"success\""), "Result should contain success field");
        assertTrue(result.contains("\"query\""), "Result should contain query field");
        assertTrue(result.contains("\"engines\""), "Result should contain engines field");
        assertTrue(result.contains("\"results\""), "Result should contain results array");
        assertTrue(result.contains("\"error\""), "Result should contain error field");

        // Validar que es JSON parseable
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result);
            assertTrue(node.has("success"), "JSON should have success field");
            assertTrue(node.has("results"), "JSON should have results array");
        } catch (Exception e) {
            fail("Result should be valid JSON: " + e.getMessage());
        }
    }

    @Test
    void webSearchTool_e2e_emptyQueryReturnsError() {
        com.boris.tooling.tool.WebSearchTool.resetCallCount();

        String result = com.boris.tooling.tool.WebSearchTool.execute(
            java.util.Map.of("query", "", "count", 3)
        );

        assertTrue(result.contains("\"success\":false"), "Empty query should return error");
        assertTrue(result.contains("\"error\""), "Result should contain error field");
    }

    @Test
    void webSearchTool_e2e_nullQueryReturnsError() {
        com.boris.tooling.tool.WebSearchTool.resetCallCount();

        String result = com.boris.tooling.tool.WebSearchTool.execute(
            java.util.Map.of("query", null, "count", 3)
        );

        assertTrue(result.contains("\"success\":false"), "Null query should return error");
        assertTrue(result.contains("\"error\""), "Result should contain error field");
    }

    // ========== Nuevos tests de registro de sesión ==========

    @Test
    void webSearchTool_registraBusquedaEnArchivoDeSesion() throws Exception {
        String query = "java 21 programming";
        String result = com.boris.tooling.tool.WebSearchTool.execute(
            java.util.Map.of("query", query, "count", 5)
        );

        // Construir la línea breve desde el resultado real (como hace ToolCallingConfig)
        String brief = buildSearchBriefFromResult(result);
        SearchContextWriter.append("web_search", query, brief, brief);

        Path file = SessionDirectoryResolver.searchesFile();
        assertTrue(Files.exists(file), "El archivo searchs.md debe existir tras la búsqueda");
        String content = Files.readString(file);

        assertTrue(content.contains("# Registro de búsquedas — Sesión " + SESSION_ID),
            "Debe contener el encabezado de sesión");
        assertTrue(content.contains(query), "Debe contener la query buscada");
        assertTrue(content.contains(brief), "Debe contener la línea breve con conteo y resultado principal");
        // El timestamp de la entrada está presente (formato ISO)
        assertTrue(content.contains("T"), "Debe contener el timestamp de la entrada");
    }

    @Test
    void searchsLog_acumulaEntradasEntreLlamadas() throws Exception {
        SearchContextWriter.append("web_search", "query 1", "1 resultado", "1 resultado · Principal: \"A\" (https://a.com)");
        WebSearchTool.resetCallCount();
        SearchContextWriter.append("web_search", "query 2", "2 resultados", "2 resultados · Principal: \"B\" (https://b.com)");

        String content = Files.readString(SessionDirectoryResolver.searchesFile());
        assertTrue(content.contains("query 1"), "Debe contener la primera query");
        assertTrue(content.contains("query 2"), "Debe contener la segunda query");
        assertTrue(content.contains("https://a.com"), "Debe contener la primera URL");
        assertTrue(content.contains("https://b.com"), "Debe contener la segunda URL");
        // Dos entradas = dos separadores ---
        assertEquals(2, countOccurrences(content, "---"), "Debe haber dos separadores entre entradas");
    }

    @Test
    void searchsLog_sinSessionId_lanzaExcepcion() {
        SearchSessionContext.clear();
        assertThrows(BorisException.class,
            () -> SearchContextWriter.append("web_search", "q", "s", "d"));
    }

    @Test
    void searchsLog_registraWebFetch() throws Exception {
        String urls = "[https://example.com]";
        SearchContextWriter.append("web_fetch", urls, "Fetch completado: 1 URLs",
            "Contenido extraído (1500 caracteres)");

        Path file = SessionDirectoryResolver.searchesFile();
        assertTrue(Files.exists(file), "El archivo searchs.md debe existir tras el fetch");
        String content = Files.readString(file);
        assertTrue(content.contains(urls), "Debe contener las URLs del fetch");
        assertTrue(content.contains("Contenido extraído (1500 caracteres)"), "Debe contener el detalle del fetch");
    }

    @Test
    void planModeGuardaPlanesEnSesionDeTest() throws Exception {
        PlanModeManager.shared().setMode(PlanMode.PLAN);
        Path plansDir = SessionDirectoryResolver.plansDirectory();
        Path planFile = plansDir.resolve("test-plan.md");

        WriteTool tool = new WriteTool(PlanModeManager.shared());
        String result = tool.execute(java.util.Map.of("path", planFile.toString(), "content", "# Plan de prueba"));

        assertTrue(result.contains("\"success\":true"), "El plan debe escribirse dentro de la sesión");
        assertTrue(Files.exists(planFile), "El archivo del plan debe existir");
        assertEquals("# Plan de prueba", Files.readString(planFile));

        // Un path fuera de la carpeta de planes de la sesión debe ser rechazado
        assertThrows(PlanModeException.class,
            () -> tool.execute(java.util.Map.of("path", "/tmp/fuera-de-sesion.md", "content", "x")));
    }

    @Test
    void searchsMd_esBreveYLegiblePorElAgente() throws Exception {
        String query = "java 21 programming";
        String result = com.boris.tooling.tool.WebSearchTool.execute(
            java.util.Map.of("query", query, "count", 3)
        );
        String brief = buildSearchBriefFromResult(result);
        SearchContextWriter.append("web_search", query, brief, brief);

        String content = Files.readString(SessionDirectoryResolver.searchesFile());

        // Debe ser un índice breve: sin snippets ni listas largas
        assertFalse(content.contains("snippet"), "No debe contener snippets");
        assertFalse(content.contains("b_caption"), "No debe contener HTML de snippets");
        // Cada entrada: encabezado + 1 línea de detalle + separador
        assertTrue(content.contains("##"), "Debe tener encabezados de entrada");
        assertTrue(content.contains("---"), "Debe tener separadores");
        // La línea breve es concisa (conteo + resultado principal)
        assertTrue(content.contains("resultados · Principal:"), "Debe tener la línea breve con resultado principal");
    }

    // ========== Helpers ==========

    private static String buildSearchBriefFromResult(String jsonResult) {
        try {
            var node = new ObjectMapper().readTree(jsonResult);
            if (node.has("success") && node.get("success").asBoolean() && node.has("results")) {
                var results = node.get("results");
                int count = results.isArray() ? results.size() : 0;
                if (count == 0) {
                    return "Sin resultados";
                }
                var first = results.get(0);
                String title = first.has("title") ? first.get("title").asText() : "";
                String url = first.has("url") ? first.get("url").asText() : "";
                return count + " resultados · Principal: \"" + title + "\" (" + url + ")";
            }
            return "Resultado no parseable";
        } catch (Exception e) {
            return "Resultado no parseable";
        }
    }

    private static int countOccurrences(String text, String substring) {
        int count = 0;
        int idx = 0;
        while ((idx = text.indexOf(substring, idx)) >= 0) {
            count++;
            idx += substring.length();
        }
        return count;
    }

    static boolean isE2ETestEnabled() {
        String enabled = System.getProperty("enableE2ETests", System.getenv().getOrDefault("ENABLE_E2E_TESTS", "false"));
        return Boolean.parseBoolean(enabled);
    }
}
