package com.boris.tooling;

import org.junit.jupiter.api.*;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ToolCallHandlerTest {

    private ToolCallHandler handler;
    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        handler = new ToolCallHandler();
        registry = new ToolRegistry();
    }

    @Test
    void handle_callsRegisteredTool_withNullArgs() {
        registry.register(ToolDefinition.of("test", "test tool"), args -> "ok");

        String result = handler.handle(registry, "test", null);

        assertEquals("ok", result);
    }

    @Test
    void handle_callsRegisteredTool_withBlankArgs() {
        registry.register(ToolDefinition.of("test", "test tool"), args -> "ok");

        String result = handler.handle(registry, "test", "");

        assertEquals("ok", result);
    }

    @Test
    void handle_parsesJsonArgs_correctly() {
        registry.register(ToolDefinition.of("echo", "Echo tool"), args ->
                String.valueOf(args.getOrDefault("value", "")));

        String json = "{\"value\":\"hello\"}";
        String result = handler.handle(registry, "echo", json);

        assertEquals("hello", result);
    }

    @Test
    void handle_propagatesUnknownToolException() {
        registry.register(ToolDefinition.of("exists", "Exists"), args -> "yes");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                handler.handle(registry, "nonexistent", "{}"));

        assertTrue(ex.getMessage().contains("Unknown tool"));
    }

    @Test
    void handle_customParser_usedWhenProvided() {
        registry.register(ToolDefinition.of("custom_parser", "test"), args ->
                String.valueOf(args.getOrDefault("key", "default")));

        ToolCallHandler customHandler = new ToolCallHandler(json -> Map.of("key", "parsed_value"));
        String result = customHandler.handle(registry, "custom_parser", "{}");

        assertEquals("parsed_value", result);
    }

    @Test
    void handle_withComplexJsonArgs() {
        registry.register(ToolDefinition.of("complex", "test"), args ->
                Map.class.cast(args.get("nested")).getOrDefault("name", "").toString());

        String json = "{\"nested\":{\"name\":\"boris\"}}";
        String result = handler.handle(registry, "complex", json);

        assertEquals("boris", result);
    }

    @Test
    void handle_withEmptyJsonObject() {
        registry.register(ToolDefinition.of("empty", "test"), args -> args.isEmpty() ? "empty" : "not_empty");

        String result = handler.handle(registry, "empty", "{}");

        assertEquals("empty", result);
    }

    @Test
    void handle_preservesNullInRegistryWhenParserFails() {
        registry.register(ToolDefinition.of("fail_parse", "test"), args -> {
            if (args.isEmpty()) return "default";
            return "has_args";
        });

        String result = handler.handle(registry, "fail_parse", "{invalid json!!!");

        assertEquals("default", result);
    }
}
