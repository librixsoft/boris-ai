package com.boris.tooling.integration;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.boris.tooling.engine.ToolRegistry;
import com.boris.tooling.engine.ToolDefinition;

class ToolCallingConfigTest {

    @Test
    void buildToolRegistry_returnsNonEmptyRegistry() {
        ToolRegistry registry = ToolCallingConfig.buildToolRegistry();

        assertNotNull(registry);
        assertFalse(registry.getAllTools().isEmpty());
    }

    @Test
    void buildToolRegistry_allHaveValidDefinitions() {
        ToolRegistry registry = ToolCallingConfig.buildToolRegistry();

        for (ToolDefinition tool : registry.getAllTools()) {
            assertNotNull(tool.name());
            assertFalse(tool.description().isBlank());
            assertNotNull(tool.openAiToolSchema());
        }
    }

    @Test
    void buildToolRegistry_containsExpectedTools() {
        ToolRegistry registry = ToolCallingConfig.buildToolRegistry();

        assertTrue(registry.hasTool("delete_file"));
        assertTrue(registry.hasTool("get_system_info"));
        assertTrue(registry.hasTool("generate_pdf"));
        assertTrue(registry.hasTool("create_office_document"));
    }

    @Test
    void generatePdfTool_hasCorrectDefinition() {
        ToolRegistry registry = ToolCallingConfig.buildToolRegistry();
        ToolDefinition tool = registry.getTool("generate_pdf");

        assertNotNull(tool);
        assertEquals("generate_pdf", tool.name());
        assertTrue(tool.description().contains("PDF"));
        assertNotNull(tool.openAiToolSchema());
    }

    @Test
    void generatePdfTool_canBeInvoked() throws Exception {
        ToolRegistry registry = ToolCallingConfig.buildToolRegistry();

        // Test with invalid parameters to verify the tool is reachable
        String result = registry.execute("generate_pdf", "{\"content\":\"\",\"outputPath\":\"/tmp/test.pdf\",\"contentType\":\"text\"}");

        assertNotNull(result);
        assertTrue(result.contains("success") || result.contains("error"));
    }
}
