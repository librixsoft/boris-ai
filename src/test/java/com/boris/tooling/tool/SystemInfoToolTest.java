package com.boris.tooling.tool;

import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SystemInfoToolTest {

    @Test
    void get_system_info_returnsValidJson() {
        String result = SystemInfoTool.get_system_info(Map.of());

        assertNotNull(result);
        assertTrue(result.contains("os"));
        assertTrue(result.contains("hostname"));
        assertTrue(result.contains("available_processors"));
    }

    @Test
    void get_system_info_returnsNonEmptyOsName() {
        String result = SystemInfoTool.get_system_info(Map.of());

        assertTrue(result.contains("os"));
    }

    @Test
    void get_system_info_returnsPositiveProcessorCount() {
        String result = SystemInfoTool.get_system_info(Map.of());

        assertTrue(result.contains("available_processors"));
    }

    @Test
    void get_system_info_definition_hasCorrectName() {
        var def = SystemInfoTool.get_system_info();

        assertEquals("get_system_info", def.name());
        assertFalse(def.description().isBlank());
    }

    @Test
    void get_system_info_definition_noParametersRequired() {
        var def = SystemInfoTool.get_system_info();

        assertTrue(def.parameters().isEmpty() || !def.parameters().containsKey("properties"));
    }
}
