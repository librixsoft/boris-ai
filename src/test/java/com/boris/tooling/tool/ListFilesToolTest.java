package com.boris.tooling.tool;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

import com.boris.context.ProjectContext;

class ListFilesToolTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        ProjectContext.setProjectPath(tempDir.toString());
        // Estructura anidada para probar depth
        Files.createDirectories(tempDir.resolve("src/main/java/com/boris"));
        Files.createDirectories(tempDir.resolve("docs"));
        Files.createFile(tempDir.resolve("pom.xml"));
    }

    @AfterEach
    void tearDown() {
        ProjectContext.clear();
    }

    @Test
    void defaultDepth_returns3Levels() throws Exception {
        ListFilesTool tool = new ListFilesTool();
        String result = tool.execute(Map.of("path", tempDir.toString()));

        assertTrue(result.contains("\"success\":true"), "Expected success, got: " + result);
        // depth 3 desde tempDir: nivel 1 = src, docs, pom.xml; nivel 2 = src/main; nivel 3 = src/main/java
        assertTrue(result.contains("src/"), "Debe mostrar src/");
        assertTrue(result.contains("src/main/"), "Debe mostrar src/main/ (nivel 2)");
        assertTrue(result.contains("src/main/java/"), "Debe mostrar src/main/java/ (nivel 3)");
        // No debe mostrar src/main/java/com/ (nivel 4) con depth 3
        assertFalse(result.contains("src/main/java/com/"), "No debe mostrar nivel 4 con depth 3");
    }

    @Test
    void depth1_returnsImmediateChildrenOnly() throws Exception {
        ListFilesTool tool = new ListFilesTool();
        String result = tool.execute(Map.of("path", tempDir.toString(), "depth", 1));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(result.contains("src/"));
        assertTrue(result.contains("docs/"));
        assertTrue(result.contains("pom.xml"));
        // depth 1 no debe mostrar subdirectorios
        assertFalse(result.contains("src/main/"), "depth 1 no debe mostrar src/main/");
    }

    @Test
    void depth5_returnsDeepStructure() throws Exception {
        ListFilesTool tool = new ListFilesTool();
        String result = tool.execute(Map.of("path", tempDir.toString(), "depth", 5));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(result.contains("src/main/java/com/boris/"), "depth 5 debe mostrar la estructura más profunda");
    }

    @Test
    void depthAboveMax_clampedTo5() throws Exception {
        ListFilesTool tool = new ListFilesTool();
        String result = tool.execute(Map.of("path", tempDir.toString(), "depth", 99));

        assertTrue(result.contains("\"success\":true"));
        // depth 99 se clampa a 5, debe mostrar hasta src/main/java/com/boris/
        assertTrue(result.contains("src/main/java/com/boris/"), "depth 99 clampa a 5");
    }

    @Test
    void depthZeroOrNegative_clampedTo1() throws Exception {
        ListFilesTool tool = new ListFilesTool();
        String result = tool.execute(Map.of("path", tempDir.toString(), "depth", 0));

        assertTrue(result.contains("\"success\":true"));
        // depth 0 se clampa a 1, solo hijos inmediatos
        assertTrue(result.contains("src/"));
        assertFalse(result.contains("src/main/"), "depth 0 clampa a 1, no debe mostrar src/main/");
    }

    @Test
    void deepListing_showsRelativePaths() throws Exception {
        ListFilesTool tool = new ListFilesTool();
        String result = tool.execute(Map.of("path", tempDir.toString(), "depth", 3));

        assertTrue(result.contains("\"success\":true"));
        // Las entries deben mostrar rutas relativas completas: src/main/java/ no solo java/
        assertTrue(result.contains("src/main/java/"), "Debe mostrar ruta relativa completa");
        // No debe haber una entrada que sea solo el nombre del directorio sin ruta
        assertFalse(result.contains("d java/\n"), "No debe mostrar solo el nombre del directorio");
    }

    @Test
    void list_files_definition_hasDepthParameter() {
        var def = ListFilesTool.list_files();
        Map<String, Object> props = (Map<String, Object>) def.parameters().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("path"));
        assertTrue(props.containsKey("depth"));
        var depthProp = (Map<String, Object>) props.get("depth");
        assertEquals("integer", depthProp.get("type"));
        assertEquals(3, depthProp.get("default"));
    }
}
