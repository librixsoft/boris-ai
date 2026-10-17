package com.boris.tooling.tool;

import org.junit.jupiter.api.Test;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test para verificar que LibraryContextTool extrae correctamente
 * la API de un repositorio de GitHub.
 */
class LibraryContextToolTest {

    @Test
    void testExtractLanternaContext() {
        // Arrange
        Map<String, Object> args = Map.of(
            "repo", "mabe02/lanterna",
            "version", "3.1.5",
            "project_path", "/tmp/test-lanterna"
        );

        // Act
        String result = LibraryContextTool.execute(args);

        // Assert
        System.out.println("=== RESULTADO ===");
        System.out.println(result);

        assertTrue(result.contains("success"), "Debe retornar success");

        // Verificar que el archivo se creó
        java.nio.file.Path contextPath = java.nio.file.Paths.get("/tmp/test-lanterna/.boris/CONTEXT.md");
        assertTrue(java.nio.file.Files.exists(contextPath), "CONTEXT.md debe existir");

        // Leer y mostrar el contenido
        try {
            String content = java.nio.file.Files.readString(contextPath);
            System.out.println("\n=== CONTEXT.MD ===");
            System.out.println(content);

            // Verificar que tiene contenido básico
            assertTrue(content.contains("lanterna"), "Debe mencionar lanterna");
            assertTrue(content.contains("import"), "Debe tener imports");

            // Verificar que tiene clases importantes de GUI
            assertTrue(content.contains("BasicWindow"), "Debe tener BasicWindow");
            assertTrue(content.contains("Panel"), "Debe tener Panel");
            assertTrue(content.contains("MultiWindowTextGUI"), "Debe tener MultiWindowTextGUI");

            // Verificar que tiene algunos métodos reales
            assertTrue(content.contains("createLayoutData") || content.contains("singleLine"),
                "Debe tener al menos algunos métodos documentados");

            // Verificar que tiene estructura de clases con métodos
            assertTrue(content.contains("### `"), "Debe tener secciones de clases");

        } catch (Exception e) {
            fail("Error leyendo CONTEXT.md: " + e.getMessage());
        }
    }
}
