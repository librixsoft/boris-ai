package com.boris.tooling.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test para verificar que LibraryContextTool extrae correctamente
 * la API de un repositorio de GitHub con optimizaciones de cache.
 */
class LibraryContextToolTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        // Limpiar directorio temporal antes de cada test
        Path borisDir = tempDir.resolve(".boris");
        if (Files.exists(borisDir)) {
            Files.walk(borisDir)
                .sorted((a, b) -> -a.compareTo(b))
                .forEach(p -> {
                    try { Files.deleteIfExists(p); } catch (Exception e) {}
                });
        }
    }

    @Test
    void testCreateNewContext() {
        // Arrange
        Map<String, Object> args = Map.of(
            "repo", "mabe02/lanterna",
            "version", "3.1.5",
            "project_path", tempDir.toString()
        );

        // Act
        String result = LibraryContextTool.execute(args);

        // Assert
        assertTrue(result.contains("success"), "Debe retornar success");

        Path contextPath = tempDir.resolve(".boris/CONTEXT.md");
        assertTrue(Files.exists(contextPath), "CONTEXT.md debe existir");

        try {
            String content = Files.readString(contextPath);

            // Verificar frontmatter YAML
            assertTrue(content.startsWith("---"), "Debe tener frontmatter YAML");
            assertTrue(content.contains("libraries:"), "Debe tener sección libraries");
            assertTrue(content.contains("repo: mabe02/lanterna"), "Debe incluir repo en frontmatter");
            assertTrue(content.contains("version: 3.1.5"), "Debe incluir version en frontmatter");
            assertTrue(content.contains("created:"), "Debe incluir timestamp");

            // Verificar contenido básico
            assertTrue(content.contains("lanterna") || content.contains("Lanterna"), "Debe mencionar lanterna");
            // imports puede estar vacío si GitHub no devuelve archivos
            assertTrue(content.contains("## Imports Verificados"), "Debe tener sección de imports");

            System.out.println("=== CONTEXT.MD ===");
            System.out.println(content.substring(0, Math.min(2000, content.length())));

        } catch (Exception e) {
            fail("Error leyendo CONTEXT.md: " + e.getMessage());
        }
    }

    @Test
    void testCacheHit() throws Exception {
        // Arrange - crear contexto inicial
        Map<String, Object> args = new HashMap<>();
        args.put("repo", "mabe02/lanterna");
        args.put("version", "3.1.5");
        args.put("project_path", tempDir.toString());

        // Primera ejecución - crea el archivo
        String firstResult = LibraryContextTool.execute(args);
        assertTrue(firstResult.contains("success"), "Primera ejecución debe ser exitosa");

        Path contextPath = tempDir.resolve(".boris/CONTEXT.md");
        long firstModified = Files.getLastModifiedTime(contextPath).toMillis();
        String firstContent = Files.readString(contextPath);

        // Pequeña pausa para detectar cambio de timestamp
        Thread.sleep(100);

        // Act - segunda ejecución (debe usar cache)
        String secondResult = LibraryContextTool.execute(args);

        // Assert
        assertTrue(secondResult.contains("ya contiene"), "Debe indicar cache hit");
        assertTrue(secondResult.contains("mabe02/lanterna"), "Debe mencionar el repo");

        // El archivo no debe haber sido modificado
        long secondModified = Files.getLastModifiedTime(contextPath).toMillis();
        assertEquals(firstModified, secondModified, "Archivo no debe ser modificado en cache hit");
    }

    @Test
    void testForceRecreate() throws Exception {
        // Arrange - crear contexto inicial
        Map<String, Object> args = new HashMap<>();
        args.put("repo", "mabe02/lanterna");
        args.put("version", "3.1.5");
        args.put("project_path", tempDir.toString());

        String firstResult = LibraryContextTool.execute(args);
        assertTrue(firstResult.contains("success"));

        Path contextPath = tempDir.resolve(".boris/CONTEXT.md");
        long firstModified = Files.getLastModifiedTime(contextPath).toMillis();

        Thread.sleep(100);

        // Act - segunda ejecución con force=true
        args.put("force", true);
        String secondResult = LibraryContextTool.execute(args);

        // Assert
        assertTrue(secondResult.contains("actualizado"), "Debe indicar que se actualizó");

        long secondModified = Files.getLastModifiedTime(contextPath).toMillis();
        assertTrue(secondModified > firstModified, "Archivo debe ser modificado con force=true");
    }

    @Test
    void testMultiLibrary() throws Exception {
        // Arrange - crear primer contexto
        Map<String, Object> args1 = new HashMap<>();
        args1.put("repo", "mabe02/lanterna");
        args1.put("version", "3.1.5");
        args1.put("project_path", tempDir.toString());

        LibraryContextTool.execute(args1);

        Path contextPath = tempDir.resolve(".boris/CONTEXT.md");
        String contentAfterFirst = Files.readString(contextPath);
        assertTrue(contentAfterFirst.contains("mabe02/lanterna"));

        // Act - agregar segunda librería
        Map<String, Object> args2 = new HashMap<>();
        args2.put("repo", "google/guava");
        args2.put("version", "32.0.0");
        args2.put("project_path", tempDir.toString());

        String result = LibraryContextTool.execute(args2);

        // Assert
        assertTrue(result.contains("success") || result.contains("actualizado"),
            "Debe ser exitoso: " + result);

        String contentAfterSecond = Files.readString(contextPath);

        // Debe tener ambas librerías en frontmatter
        assertTrue(contentAfterSecond.contains("repo: mabe02/lanterna"),
            "Debe preservar primera librería en frontmatter");
        assertTrue(contentAfterSecond.contains("repo: google/guava"),
            "Debe agregar segunda librería en frontmatter");

        // Debe tener dos entradas en libraries
        int libraryCount = countOccurrences(contentAfterSecond, "  - repo:");
        assertEquals(2, libraryCount, "Debe tener exactamente 2 librerías");
    }

    @Test
    void testDifferentVersionTriggersUpdate() throws Exception {
        // Arrange - crear contexto con versión 3.1.5
        Map<String, Object> args = new HashMap<>();
        args.put("repo", "mabe02/lanterna");
        args.put("version", "3.1.5");
        args.put("project_path", tempDir.toString());

        LibraryContextTool.execute(args);

        Path contextPath = tempDir.resolve(".boris/CONTEXT.md");
        String firstContent = Files.readString(contextPath);
        assertTrue(firstContent.contains("version: 3.1.5"));

        // Act - pedir versión diferente
        args.put("version", "3.2.0");
        String result = LibraryContextTool.execute(args);

        // Assert - debe actualizar porque la versión es diferente
        assertTrue(result.contains("actualizado") || result.contains("success"),
            "Debe actualizar para nueva versión");

        String secondContent = Files.readString(contextPath);
        assertTrue(secondContent.contains("version: 3.2.0"),
            "Debe tener la nueva versión");
    }

    private int countOccurrences(String str, String sub) {
        int count = 0;
        int idx = 0;
        while ((idx = str.indexOf(sub, idx)) != -1) {
            count++;
            idx += sub.length();
        }
        return count;
    }
}
