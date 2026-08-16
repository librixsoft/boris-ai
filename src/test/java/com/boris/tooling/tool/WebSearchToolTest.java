package com.boris.tooling.tool;

import com.boris.tooling.ToolDefinition;
import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WebSearchToolTest {

    // -- ToolDefinition tests --

    @Test
    void web_search_definition_hasCorrectName() {
        var def = WebSearchTool.web_search();
        assertEquals("web_search", def.name());
    }

    @Test
    void web_search_definition_hasDescription() {
        var def = WebSearchTool.web_search();
        assertNotNull(def.description());
        assertTrue(def.description().toLowerCase().contains("duckduckgo") || def.description().toLowerCase().contains("search"));
    }

    @Test
    void web_search_definition_hasQueryParameter() {
        var def = WebSearchTool.web_search();
        Map<String, Object> props = (Map<String, Object>) def.parameters().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("query"));
    }

    @Test
    void web_search_definition_hasCountParameter() {
        var def = WebSearchTool.web_search();
        Map<String, Object> props = (Map<String, Object>) def.parameters().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("count"));
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

    // Note: Real web search tests are in PlaywrightSearchE2ETest.java
    // These unit tests focus on tool definition and input validation
    // The actual Playwright integration is tested end-to-end
}