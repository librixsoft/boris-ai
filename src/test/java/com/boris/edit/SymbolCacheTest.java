package com.boris.edit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class SymbolCacheTest {

    private SymbolCache cache;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        cache = new SymbolCache();
    }

    @Test
    void testStandardClassesAlwaysKnown() {
        assertTrue(cache.isKnownClass("ArrayList"));
        assertTrue(cache.isKnownClass("HashMap"));
        assertTrue(cache.isKnownClass("String"));
        assertTrue(cache.isKnownClass("List"));
        assertTrue(cache.isKnownClass("Optional"));
    }

    @Test
    void testLoadFromContext() throws IOException {
        String contextContent = """
            # CONTEXT.md

            ## Lanterna GUI2

            ### `BasicWindow`
            - `BasicWindow()`
            - `BasicWindow(String title)`
            - `setComponent(Component)`
            - `close()`
            - `setTitle(String)`

            ### `Panel`
            - `Panel()`
            - `addComponent(Component)`
            - `setLayoutManager(LayoutManager)`

            ### `Label`
            - `Label(String text)`
            - `setText(String)`
            - `getText()`
            """;

        Path contextFile = tempDir.resolve("CONTEXT.md");
        Files.writeString(contextFile, contextContent);

        cache.loadFromContext(contextFile);

        assertTrue(cache.isKnownClass("BasicWindow"));
        assertTrue(cache.isKnownClass("Panel"));
        assertTrue(cache.isKnownClass("Label"));
        assertFalse(cache.isKnownClass("TextLabel")); // No existe
    }

    @Test
    void testGetMethodsForClass() throws IOException {
        String contextContent = """
            ### `Panel`
            - `addComponent(Component)`
            - `setLayoutManager(LayoutManager)`
            - `removeAllComponents()`
            """;

        Path contextFile = tempDir.resolve("CONTEXT.md");
        Files.writeString(contextFile, contextContent);

        cache.loadFromContext(contextFile);

        Set<String> methods = cache.getMethodsForClass("Panel");
        assertTrue(methods.contains("addComponent"));
        assertTrue(methods.contains("setLayoutManager"));
        assertTrue(methods.contains("removeAllComponents"));
    }

    @Test
    void testFindSimilarClasses() throws IOException {
        String contextContent = """
            ### `BasicWindow`
            ### `Panel`
            ### `Label`
            ### `TextBox`
            """;

        Path contextFile = tempDir.resolve("CONTEXT.md");
        Files.writeString(contextFile, contextContent);

        cache.loadFromContext(contextFile);

        // TextLabel no existe, pero Label sí
        List<String> similar = cache.findSimilarClasses("TextLabel");
        assertTrue(similar.contains("Label"));

        // BasicWndow (typo) debería sugerir BasicWindow
        List<String> similar2 = cache.findSimilarClasses("BasicWndow");
        assertTrue(similar2.contains("BasicWindow"));
    }

    @Test
    void testEmptyCache() {
        assertTrue(cache.isEmpty());
        assertEquals(0, cache.classCount());

        // Standard classes still work
        assertTrue(cache.isKnownClass("String"));
    }

    @Test
    void testClear() throws IOException {
        String contextContent = """
            ### `MyClass`
            - `myMethod()`
            """;

        Path contextFile = tempDir.resolve("CONTEXT.md");
        Files.writeString(contextFile, contextContent);

        cache.loadFromContext(contextFile);
        assertTrue(cache.isKnownClass("MyClass"));

        cache.clear();
        assertFalse(cache.isKnownClass("MyClass"));
        assertTrue(cache.isEmpty());
    }

    @Test
    void testHasMethod() throws IOException {
        String contextContent = """
            ### `Panel`
            - `addComponent(Component)`
            - `setLayoutManager(LayoutManager)`
            """;

        Path contextFile = tempDir.resolve("CONTEXT.md");
        Files.writeString(contextFile, contextContent);

        cache.loadFromContext(contextFile);

        assertTrue(cache.hasMethod("Panel", "addComponent"));
        assertTrue(cache.hasMethod("Panel", "setLayoutManager"));
        assertFalse(cache.hasMethod("Panel", "nonExistentMethod"));
    }
}
