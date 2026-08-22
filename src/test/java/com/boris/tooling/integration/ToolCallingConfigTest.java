package com.boris.tooling.integration;

import org.junit.jupiter.api.*;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.ToolCallback;

import com.boris.settings.Settings;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallingConfigTest {

    private ToolCallback[] callbacks() {
        Settings s = new Settings();
        s.setContextWindow(8000);
        return ToolCallingConfig.buildNativeToolCallbacks(s);
    }

    @Test
    void buildNativeToolCallbacks_returnsNonEmptyArray() {
        ToolCallback[] callbacks = callbacks();

        assertNotNull(callbacks);
        assertTrue(callbacks.length > 0);
    }

    @Test
    void buildNativeToolCallbacks_allHaveValidDefinitions() {
        ToolCallback[] callbacks = callbacks();

        for (ToolCallback callback : callbacks) {
            assertNotNull(callback.getToolDefinition().name());
            assertFalse(callback.getToolDefinition().description().isBlank());
            assertNotNull(callback.getToolDefinition().inputSchema());
        }
    }

    @Test
    void buildNativeToolCallbacks_containsExpectedTools() {
        ToolCallback[] callbacks = callbacks();

        var names = java.util.Arrays.stream(callbacks)
                .map(cb -> cb.getToolDefinition().name())
                .toList();

        assertTrue(names.contains("read_file"));
        assertTrue(names.contains("write_file"));
        assertTrue(names.contains("delete_file"));
        assertTrue(names.contains("list_files"));
        assertTrue(names.contains("get_system_info"));
        assertTrue(names.contains("generate_pdf"));
    }

    @Test
    void buildNativeToolCallbacks_callbackCallsExecuteCorrectly() {
        ToolCallback[] callbacks = callbacks();

        var readCb = java.util.Arrays.stream(callbacks)
                .filter(cb -> "read_file".equals(cb.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();

        String result = readCb.call("{\"path\":\"/tmp/test_read.txt\"}");

        assertNotNull(result);
    }

    @Test
    void toolCallbacks_allAreFunctionCallbacks() {
        ToolCallback[] callbacks = callbacks();

        for (ToolCallback cb : callbacks) {
            assertInstanceOf(FunctionCallback.class, cb);
        }
    }

    @Test
    void generatePdfTool_callbackHasCorrectDefinition() {
        ToolCallback[] callbacks = callbacks();

        var pdfCb = java.util.Arrays.stream(callbacks)
                .filter(cb -> "generate_pdf".equals(cb.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();

        assertEquals("generate_pdf", pdfCb.getToolDefinition().name());
        assertTrue(pdfCb.getToolDefinition().description().contains("PDF"));
        assertNotNull(pdfCb.getToolDefinition().inputSchema());
    }

    @Test
    void generatePdfTool_callbackCanBeInvoked() {
        ToolCallback[] callbacks = callbacks();

        var pdfCb = java.util.Arrays.stream(callbacks)
                .filter(cb -> "generate_pdf".equals(cb.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();

        // Test with invalid parameters to verify the tool is reachable
        String result = pdfCb.call("{\"content\":\"\",\"outputPath\":\"/tmp/test.pdf\",\"contentType\":\"text\"}");

        assertNotNull(result);
        assertTrue(result.contains("success") || result.contains("error"));
    }
}
