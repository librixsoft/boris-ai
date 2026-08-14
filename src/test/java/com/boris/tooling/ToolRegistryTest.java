package com.boris.tooling;

import org.junit.jupiter.api.*;

import java.util.Map;
import java.util.Collection;

import static org.junit.jupiter.api.Assertions.*;

class ToolRegistryTest {

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new ToolRegistry();
    }

    @Test
    void register_addsToolAndExecutor() {
        ToolDefinition def = ToolDefinition.of("test_tool", "A test tool");
        ToolRegistry.ToolExecutor executor = args -> "result";

        registry.register(def, executor);

        assertTrue(registry.contains("test_tool"));
        assertEquals(def, registry.getByName("test_tool"));
    }

    @Test
    void register_multipleTools_allAvailable() {
        registry.register(ToolDefinition.of("tool_a", "First"), args -> "a");
        registry.register(ToolDefinition.of("tool_b", "Second"), args -> "b");

        assertEquals(2, registry.size());
        assertTrue(registry.contains("tool_a"));
        assertTrue(registry.contains("tool_b"));
    }

    @Test
    void unregister_removesToolAndExecutor() {
        registry.register(ToolDefinition.of("remove_me", "Will be removed"), args -> "x");
        assertTrue(registry.contains("remove_me"));

        registry.unregister("remove_me");

        assertFalse(registry.contains("remove_me"));
        assertNull(registry.getByName("remove_me"));
        assertEquals(0, registry.size());
    }

    @Test
    void execute_callsCorrectExecutor() {
        registry.register(ToolDefinition.of("echo", "Echo tool"), args ->
            String.valueOf(args.getOrDefault("input", ""))
        );

        String result = registry.execute("echo", Map.of("input", "hello"));

        assertEquals("hello", result);
    }

    @Test
    void execute_nullArguments_defaultsToEmptyMap() {
        registry.register(ToolDefinition.of("no_args", "No args tool"), args -> "ok");

        String result = registry.execute("no_args", null);

        assertEquals("ok", result);
    }

    @Test
    void execute_throwsWhenToolNotFound() {
        registry.register(ToolDefinition.of("exists", "Exists"), args -> "yes");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
            registry.execute("nonexistent", Map.of())
        );
        assertTrue(ex.getMessage().contains("Unknown tool"));
    }

    @Test
    void getAll_returnsAllTools() {
        registry.register(ToolDefinition.of("t1", "First"), args -> "a");
        registry.register(ToolDefinition.of("t2", "Second"), args -> "b");

        Collection<ToolDefinition> all = registry.getAll();

        assertEquals(2, all.size());
        assertTrue(all.stream().anyMatch(t -> t.name().equals("t1")));
        assertTrue(all.stream().anyMatch(t -> t.name().equals("t2")));
    }

    @Test
    void getAll_returnsUnmodifiableCollection() {
        registry.register(ToolDefinition.of("u1", "Test"), args -> "x");

        assertThrows(UnsupportedOperationException.class, () ->
            registry.getAll().clear()
        );
    }
}
