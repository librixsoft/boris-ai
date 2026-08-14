package com.boris.tooling;

import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolDefinitionTest {

    @Test
    void of_withNameAndDescription_only() {
        ToolDefinition tool = ToolDefinition.of("test_tool", "A test description");

        assertEquals("test_tool", tool.name());
        assertEquals("A test description", tool.description());
        assertTrue(tool.parameters().isEmpty());
    }

    @Test
    void of_withNameDescriptionAndParameters() {
        Map<String, Object> params = Map.of("type", "object", "properties", Map.of());
        ToolDefinition tool = ToolDefinition.of("write_file", "Write content to a file", params);

        assertEquals("write_file", tool.name());
        assertEquals("Write content to a file", tool.description());
        assertEquals(params, tool.parameters());
    }

    @Test
    void constructor_throwsWhenNameIsNull() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            new ToolDefinition(null, "desc", Map.of())
        );
        assertTrue(ex.getMessage().contains("name"));
    }

    @Test
    void constructor_throwsWhenNameIsBlank() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            new ToolDefinition("  ", "desc", Map.of())
        );
        assertTrue(ex.getMessage().contains("name"));
    }

    @Test
    void constructor_throwsWhenDescriptionIsNull() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            new ToolDefinition("tool", null, Map.of())
        );
        assertTrue(ex.getMessage().contains("description"));
    }

    @Test
    void constructor_parametersAreImmutable() {
        var mutableParams = new java.util.HashMap<String, Object>();
        mutableParams.put("key", "value");
        ToolDefinition tool = new ToolDefinition("tool", "desc", mutableParams);

        assertThrows(UnsupportedOperationException.class, () ->
            tool.parameters().put("newKey", "newValue")
        );
    }

    @Test
    void constructor_parametersNullBecomesEmptyMap() {
        ToolDefinition tool = new ToolDefinition("tool", "desc", null);
        assertTrue(tool.parameters().isEmpty());
    }
}
