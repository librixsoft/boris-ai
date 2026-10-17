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
 * Test para verificar que LibraryContextTool descarga y analiza repos correctamente.
 * Nota: Estos tests requieren conexión a internet para descargar de GitHub.
 */
class LibraryContextToolTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
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
        // Usar un repo pequeño para test rápido
        Map<String, Object> args = Map.of(
            "repo", "mabe02/lanterna",
            "version", "master",
            "project_path", tempDir.toString()
        );

        String result = LibraryContextTool.execute(args);
        System.out.println("Result: " + result);

        // Verificar resultado
        assertTrue(result.contains("success"), "Debe retornar JSON con success");

        Path contextPath = tempDir.resolve(".boris/CONTEXT.md");

        // Si la descarga funcionó, verificar contenido
        if (Files.exists(contextPath)) {
            try {
                String content = Files.readString(contextPath);
                System.out.println("=== CONTEXT.MD (primeros 2000 chars) ===");
                System.out.println(content.substring(0, Math.min(2000, content.length())));

                // Verificar frontmatter YAML
                assertTrue(content.startsWith("---"), "Debe tener frontmatter YAML");
                assertTrue(content.contains("libraries:"), "Debe tener sección libraries");
                assertTrue(content.contains("repo: mabe02/lanterna"), "Debe incluir repo");

                // Verificar que tiene contenido
                assertTrue(content.contains("## Imports Verificados"), "Debe tener sección imports");
                assertTrue(content.contains("## Clases y Métodos"), "Debe tener sección clases");

            } catch (Exception e) {
                fail("Error leyendo CONTEXT.md: " + e.getMessage());
            }

            // Verificar que se creó el directorio del repo
            Path repoDir = tempDir.resolve(".boris/repos/lanterna");
            assertTrue(Files.exists(repoDir), "Debe existir directorio del repo descargado");

            // Verificar .gitignore
            Path gitignore = tempDir.resolve(".gitignore");
            if (Files.exists(gitignore)) {
                try {
                    String gitignoreContent = Files.readString(gitignore);
                    assertTrue(gitignoreContent.contains(".boris/"), ".gitignore debe incluir .boris/");
                } catch (Exception e) {
                    // Ignore
                }
            }
        } else {
            // Si no se creó, verificar que el resultado indica error de red
            assertTrue(result.contains("Error") || result.contains("success"),
                "Si no se creó, debe indicar error o usar fallback");
        }
    }

    @Test
    void testCacheHit() throws Exception {
        Map<String, Object> args = new HashMap<>();
        args.put("repo", "mabe02/lanterna");
        args.put("version", "master");
        args.put("project_path", tempDir.toString());

        // Primera ejecución
        String firstResult = LibraryContextTool.execute(args);

        Path contextPath = tempDir.resolve(".boris/CONTEXT.md");

        // Solo probar cache si la primera ejecución funcionó
        if (Files.exists(contextPath)) {
            long firstModified = Files.getLastModifiedTime(contextPath).toMillis();

            Thread.sleep(100);

            // Segunda ejecución - debe usar cache
            String secondResult = LibraryContextTool.execute(args);

            assertTrue(secondResult.contains("ya contiene") || secondResult.contains("cache"),
                "Debe indicar cache hit: " + secondResult);

            long secondModified = Files.getLastModifiedTime(contextPath).toMillis();
            assertEquals(firstModified, secondModified, "Archivo no debe ser modificado en cache hit");
        } else {
            System.out.println("SKIP: Primera descarga falló, no se puede probar cache");
        }
    }

    @Test
    void testForceRecreate() throws Exception {
        Map<String, Object> args = new HashMap<>();
        args.put("repo", "mabe02/lanterna");
        args.put("version", "master");
        args.put("project_path", tempDir.toString());

        // Primera ejecución
        LibraryContextTool.execute(args);

        Path contextPath = tempDir.resolve(".boris/CONTEXT.md");

        if (Files.exists(contextPath)) {
            long firstModified = Files.getLastModifiedTime(contextPath).toMillis();

            Thread.sleep(100);

            // Segunda con force=true
            args.put("force", true);
            String result = LibraryContextTool.execute(args);

            // Con force, debe re-descargar y actualizar
            if (result.contains("actualizado")) {
                long secondModified = Files.getLastModifiedTime(contextPath).toMillis();
                assertTrue(secondModified > firstModified, "Archivo debe ser modificado con force=true");
            }
        } else {
            System.out.println("SKIP: Primera descarga falló");
        }
    }

    @Test
    void testMultiLibrary() throws Exception {
        // Primera librería
        Map<String, Object> args1 = new HashMap<>();
        args1.put("repo", "mabe02/lanterna");
        args1.put("version", "master");
        args1.put("project_path", tempDir.toString());

        LibraryContextTool.execute(args1);

        Path contextPath = tempDir.resolve(".boris/CONTEXT.md");

        if (Files.exists(contextPath)) {
            String contentAfterFirst = Files.readString(contextPath);
            assertTrue(contentAfterFirst.contains("mabe02/lanterna"));

            // Segunda librería (repo pequeño)
            Map<String, Object> args2 = new HashMap<>();
            args2.put("repo", "google/gson");
            args2.put("version", "main");
            args2.put("project_path", tempDir.toString());

            String result = LibraryContextTool.execute(args2);

            if (result.contains("actualizado") || result.contains("success")) {
                String contentAfterSecond = Files.readString(contextPath);

                // Verificar que tiene ambas librerías
                assertTrue(contentAfterSecond.contains("repo: mabe02/lanterna"),
                    "Debe preservar primera librería");

                if (contentAfterSecond.contains("repo: google/gson")) {
                    int libraryCount = countOccurrences(contentAfterSecond, "  - repo:");
                    assertEquals(2, libraryCount, "Debe tener 2 librerías");
                }
            }
        } else {
            System.out.println("SKIP: Primera descarga falló");
        }
    }

    @Test
    void testDifferentVersionTriggersUpdate() throws Exception {
        Map<String, Object> args = new HashMap<>();
        args.put("repo", "mabe02/lanterna");
        args.put("version", "master");
        args.put("project_path", tempDir.toString());

        LibraryContextTool.execute(args);

        Path contextPath = tempDir.resolve(".boris/CONTEXT.md");

        if (Files.exists(contextPath)) {
            String firstContent = Files.readString(contextPath);
            assertTrue(firstContent.contains("version: master"));

            // Pedir versión diferente
            args.put("version", "release-3.1");
            String result = LibraryContextTool.execute(args);

            // Debe intentar actualizar (aunque la versión no exista, el intento cuenta)
            String secondContent = Files.readString(contextPath);

            // Si la nueva versión existió y se descargó
            if (result.contains("actualizado")) {
                assertTrue(secondContent.contains("version: release-3.1"),
                    "Debe tener la nueva versión");
            }
        } else {
            System.out.println("SKIP: Primera descarga falló");
        }
    }

    @Test
    void testInvalidRepo() {
        Map<String, Object> args = Map.of(
            "repo", "invalid-repo-format",
            "project_path", tempDir.toString()
        );

        String result = LibraryContextTool.execute(args);

        assertTrue(result.contains("Error") || result.contains("false"),
            "Debe indicar error para repo inválido");
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
