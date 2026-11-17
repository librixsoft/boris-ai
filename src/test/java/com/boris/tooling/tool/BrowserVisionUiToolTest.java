package com.boris.tooling.tool;

import com.boris.tooling.engine.ToolRegistry;
import com.boris.tooling.integration.ToolCallingConfig;
import com.boris.settings.Settings;
import com.boris.settings.ModelConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BrowserVisionUiToolTest {

    private static ToolCallingConfig createTestConfig() {
        return new ToolCallingConfig(
            new com.boris.tooling.tool.DeleteTool(),
            new com.boris.tooling.tool.EditTool(),
            new com.boris.tooling.tool.SystemInfoTool(),
            new com.boris.tooling.tool.PdfGenerationTool(),
            new com.boris.tooling.tool.OfficeDocumentTool(),
            new Settings(new ModelConfig("http://localhost:11434", "test"), null)
        );
    }

    @Test
    void browserVisionUiToolIsRegisteredInRegistry() {
        ToolCallingConfig config = createTestConfig();
        ToolRegistry registry = ToolRegistry.from(config);
        assertTrue(registry.hasTool("browser_ui_action"), "browser_ui_action tool must be registered");
    }

    @Test
    void browserVisionUiToolClassHasToolAnnotation() throws Exception {
        // Verifica que la clase tiene el método anotado con @Tool
        var method = com.boris.tooling.tool.BrowserVisionUiTool.class.getMethod(
            "executeBrowserAction",
            java.lang.String.class,
            java.lang.String.class,
            java.lang.String.class,
            java.lang.String.class,
            java.lang.String.class,
            java.lang.String.class,
            java.lang.String.class
        );
        assertNotNull(method.getAnnotation(com.boris.tooling.annotation.Tool.class),
            "executeBrowserAction debe tener anotación @Tool");
    }

    @Test
    void browserVisionUiToolHasCorrectToolName() throws Exception {
        var method = com.boris.tooling.tool.BrowserVisionUiTool.class.getMethod(
            "executeBrowserAction",
            java.lang.String.class,
            java.lang.String.class,
            java.lang.String.class,
            java.lang.String.class,
            java.lang.String.class,
            java.lang.String.class,
            java.lang.String.class
        );
        var toolAnnotation = method.getAnnotation(com.boris.tooling.annotation.Tool.class);
        assertNotNull(toolAnnotation);
        assertEquals("browser_ui_action", toolAnnotation.name());
    }
}
