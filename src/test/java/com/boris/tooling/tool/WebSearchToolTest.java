package com.boris.tooling.tool;

import com.boris.tooling.engine.ToolRegistry;
import com.boris.tooling.integration.ToolCallingConfig;
import com.boris.settings.Settings;
import com.boris.settings.ModelConfig;
import com.boris.cli.ui.PlanModeManager;
import com.boris.exceptions.WebSearchLimitException;
import com.boris.exceptions.WebSearchDuplicateQueryException;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebSearchToolTest {

    private static ToolCallingConfig createTestConfig() {
        return new ToolCallingConfig(
                new com.boris.tooling.tool.DeleteTool(),
                new com.boris.tooling.tool.EditTool(),
                new com.boris.tooling.tool.SystemInfoTool(),
                new com.boris.tooling.tool.PdfGenerationTool(),
                new com.boris.tooling.tool.OfficeDocumentTool(),
                new com.boris.task.decomposition.TaskPlannerTool(),
                new Settings(new ModelConfig("http://localhost:11434", "test"), null),
                PlanModeManager.shared()
        );
    }

    @BeforeEach
    void setUp() {
        WebSearchTool.resetCallCount();
    }

    // -- ToolDefinition tests via ToolRegistry --

    @Test
    void web_search_tool_isRegisteredInRegistry() {
        ToolCallingConfig config = createTestConfig();
        ToolRegistry registry = ToolRegistry.from(config);
        assertTrue(registry.hasTool("web_search"), "web_search tool must be registered");
    }

    @Test
    void web_search_tool_hasCorrectName() {
        ToolCallingConfig config = createTestConfig();
        ToolRegistry registry = ToolRegistry.from(config);
        var def = registry.getTool("web_search");
        assertNotNull(def);
        assertEquals("web_search", def.name());
    }

    @Test
    void web_search_tool_hasDescription() {
        ToolCallingConfig config = createTestConfig();
        ToolRegistry registry = ToolRegistry.from(config);
        var def = registry.getTool("web_search");
        assertNotNull(def);
        assertNotNull(def.description());
        assertTrue(def.description().toLowerCase().contains("bing") || def.description().toLowerCase().contains("search"));
    }

    @Test
    void web_search_tool_hasQueryParameter() {
        ToolCallingConfig config = createTestConfig();
        ToolRegistry registry = ToolRegistry.from(config);
        var def = registry.getTool("web_search");
        assertNotNull(def);
        boolean hasQuery = def.parameters().stream()
                .anyMatch(p -> "query".equals(p.name()));
        assertTrue(hasQuery, "web_search must have a 'query' parameter");
    }

    @Test
    void web_search_tool_hasCountParameter() {
        ToolCallingConfig config = createTestConfig();
        ToolRegistry registry = ToolRegistry.from(config);
        var def = registry.getTool("web_search");
        assertNotNull(def);
        boolean hasCount = def.parameters().stream()
                .anyMatch(p -> "count".equals(p.name()));
        assertTrue(hasCount, "web_search must have a 'count' parameter");
    }

    // -- Input validation tests --

    @Test
    void execute_returnsError_whenQueryIsNull() {
        var args = new java.util.HashMap<String, Object>();
        args.put("query", (Object) null);
        String result = WebSearchTool.execute(args);
        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.toLowerCase().contains("query"));
    }

    @Test
    void execute_returnsError_whenQueryIsEmpty() {
        String result = WebSearchTool.execute(Map.ofEntries(Map.entry("query", "")));
        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.toLowerCase().contains("query"));
    }

    @Test
    void execute_returnsError_whenQueryIsBlank() {
        String result = WebSearchTool.execute(Map.ofEntries(Map.entry("query", "   ")));
        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.toLowerCase().contains("query"));
    }

    // -- Call counter tests --

    @Test
    void execute_blocksWhenMaxCallsExceeded() {
        // Simular que ya se realizaron las MAX_CALLS (3) llamadas permitidas
        WebSearchTool.incrementCallCount();
        WebSearchTool.incrementCallCount();
        WebSearchTool.incrementCallCount();
        assertEquals(3, WebSearchTool.getCallCount());

        // La siguiente llamada debe ser bloqueada: lanza WebSearchLimitException
        WebSearchLimitException limitException = assertThrows(WebSearchLimitException.class, () -> {
            WebSearchTool.execute(Map.ofEntries(Map.entry("query", "cualquier busqueda")));
        });
        assertTrue(limitException.getMessage().toLowerCase().contains("límite"),
            "El mensaje debe mencionar el límite");
    }

    @Test
    void resetCallCount_resetsCounterToZero() {
        WebSearchTool.incrementCallCount();
        assertEquals(1, WebSearchTool.getCallCount());

        WebSearchTool.resetCallCount();
        assertEquals(0, WebSearchTool.getCallCount());
    }

    // -- Dedup de queries por turno --

    @Test
    void execute_mismaQueryDosVecesMismoTurno_lanzaWebSearchDuplicateQueryException() {
        WebSearchTool.resetCallCount();

        // Primera llamada: se incrementa el contador y se registra la query
        WebSearchTool.execute(Map.ofEntries(Map.entry("query", "info de gatos")));

        // Segunda llamada con la misma query: debe lanzar dedup ANTES de incrementar el contador
        assertThrows(WebSearchDuplicateQueryException.class, () -> {
            WebSearchTool.execute(Map.ofEntries(Map.entry("query", "info de gatos")));
        });
    }

    @Test
    void execute_queryRefinadaDistinta_noLanzaDedup() {
        WebSearchTool.resetCallCount();

        // Primera llamada
        WebSearchTool.execute(Map.ofEntries(Map.entry("query", "info de gatos")));

        // Query refinada (distinta tras normalización): no debe lanzar dedup.
        // Puede lanzar WebSearchLimitException si MAX_CALLS está agotado, que es otro caso.
        // Lo importante es que NO sea WebSearchDuplicateQueryException.
        try {
            WebSearchTool.execute(Map.ofEntries(Map.entry("query", "info de gatos cafes")));
        } catch (WebSearchDuplicateQueryException e) {
            fail("Query refinada no debe lanzar dedup: " + e.getMessage());
        } catch (WebSearchLimitException expected) {
            // Límite alcanzado: es correcto, no es dedup
        }
    }

    @Test
    void execute_queryConEspaciosExtra_normalizadaIgualQueQueryOriginal() {
        WebSearchTool.resetCallCount();

        WebSearchTool.execute(Map.ofEntries(Map.entry("query", "info de gatos")));

        // Misma query con espacios extra y mayúsculas: debe ser detectada como duplicada
        assertThrows(WebSearchDuplicateQueryException.class, () -> {
            WebSearchTool.execute(Map.ofEntries(Map.entry("query", "  INFO   DE   GATOS  ")));
        });
    }

    @Test
    void resetCallCount_tambienLimpiaQueriesEjecutadas() {
        WebSearchTool.resetCallCount();

        WebSearchTool.execute(Map.ofEntries(Map.entry("query", "info de gatos")));

        // Reset entre turnos: la query ya ejecutada debe poder repetirse
        WebSearchTool.resetCallCount();

        // Tras reset, la misma query debe ejecutarse de nuevo (no dedup)
        try {
            WebSearchTool.execute(Map.ofEntries(Map.entry("query", "info de gatos")));
        } catch (WebSearchDuplicateQueryException e) {
            fail("Tras resetCallCount la query debe poder ejecutarse de nuevo: " + e.getMessage());
        }
    }
}
