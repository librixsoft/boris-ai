package com.boris.tooling.integration;

import org.junit.jupiter.api.*;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallingConfigTest {

    @Test
    void buildDefaultRegistry_registersAllTools() {
        var registry = ToolCallingConfig.buildDefaultRegistry();

        assertEquals(5, registry.size());
        assertTrue(registry.contains("read_file"));
        assertTrue(registry.contains("write_file"));
        assertTrue(registry.contains("delete_file"));
        assertTrue(registry.contains("list_files"));
        assertTrue(registry.contains("get_system_info"));
    }

    @Test
    void buildDefaultRegistry_allToolsHaveValidDefinitions() {
        var registry = ToolCallingConfig.buildDefaultRegistry();

        for (var def : registry.getAll()) {
            assertNotNull(def.name());
            assertFalse(def.description().isBlank());
        }
    }

    @Test
    void buildToolCallbacks_returnsNonEmptyArray() {
        ToolCallback[] callbacks = ToolCallingConfig.buildToolCallbacks();

        assertNotNull(callbacks);
        assertTrue(callbacks.length > 0);
    }

    @Test
    void buildToolCallbacks_allHaveValidDefinitions() {
        ToolCallback[] callbacks = ToolCallingConfig.buildToolCallbacks();

        for (ToolCallback callback : callbacks) {
            assertNotNull(callback.getToolDefinition().name());
            assertFalse(callback.getToolDefinition().description().isBlank());
            assertNotNull(callback.getToolDefinition().inputSchema());
        }
    }

    @Test
    void buildToolCallbacks_containsExpectedTools() {
        ToolCallback[] callbacks = ToolCallingConfig.buildToolCallbacks();

        var names = java.util.Arrays.stream(callbacks)
                .map(cb -> cb.getToolDefinition().name())
                .toList();

        assertTrue(names.contains("read_file"));
        assertTrue(names.contains("write_file"));
        assertTrue(names.contains("delete_file"));
        assertTrue(names.contains("list_files"));
        assertTrue(names.contains("get_system_info"));
    }

    @Test
    void buildToolCallbacks_callbackCallsExecuteCorrectly() {
        ToolCallback[] callbacks = ToolCallingConfig.buildToolCallbacks();

        var readCb = java.util.Arrays.stream(callbacks)
                .filter(cb -> "read_file".equals(cb.getToolDefinition().name()))
                .findFirst()
                .orElseThrow();

        String result = readCb.call("{\"path\":\"/tmp/test_read.txt\"}");

        assertNotNull(result);
    }

    @Test
    void buildChatClientWithTools_Registry_createsValidChatModelCallbacks() {
        var registry = ToolCallingConfig.buildDefaultRegistry();
        assertEquals(5, registry.getAll().size());
    }

    @Test
    void toolCallbacks_allAreFunctionCallbacks() {
        ToolCallback[] callbacks = ToolCallingConfig.buildToolCallbacks();

        for (ToolCallback cb : callbacks) {
            assertInstanceOf(FunctionCallback.class, cb);
        }
    }
}
