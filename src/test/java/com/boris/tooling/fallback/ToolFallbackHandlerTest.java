package com.boris.tooling.fallback;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.*;

class ToolFallbackHandlerTest {

    @TempDir
    Path tempDir;

    @Test
    void testDeepSeekR1SimulatedJsonWriteFile() throws IOException {
        Path targetFile = tempDir.resolve("hola.md");
        String responseText = """
                ●\s
                ```json
                {
                  "status": "success",
                  "message": "Archivo creado con éxito.",
                  "path": "%s",
                  "content": "# Hola Mundo\\n\\nEste es un archivo Markdown de ejemplo."
                }
                ```
                """.formatted(targetFile.toAbsolutePath().toString().replace("\\", "\\\\"));

        List<ToolFallbackHandler.FallbackResult> results = ToolFallbackHandler.handleFallback(responseText);

        assertEquals(1, results.size());
        ToolFallbackHandler.FallbackResult res = results.get(0);
        assertTrue(res.executed());
        assertTrue(res.success());
        assertEquals("write_file", res.toolName());
        assertEquals(targetFile.toAbsolutePath().toString(), res.targetPath());

        assertTrue(Files.exists(targetFile));
    }

    @Test
    void testExplicitToolCallWriteFile() throws IOException {
        Path targetFile = tempDir.resolve("test.txt");
        String responseText = """
                Voy a crear el archivo para ti:
                ```tool_call
                {
                  "tool": "write_file",
                  "path": "%s",
                  "content": "Contenido de prueba"
                }
                ```
                """.formatted(targetFile.toAbsolutePath().toString().replace("\\", "\\\\"));

        List<ToolFallbackHandler.FallbackResult> results = ToolFallbackHandler.handleFallback(responseText);

        assertEquals(1, results.size());
        assertTrue(results.get(0).success());
        assertTrue(Files.exists(targetFile));
        assertEquals("Contenido de prueba", Files.readString(targetFile));
    }

    @Test
    void testApplyEditFallback() throws IOException {
        Path targetFile = tempDir.resolve("edit_test.txt");
        Files.writeString(targetFile, "Hello World");

        String responseText = """
                ```json
                {
                  "tool": "apply_edit",
                  "path": "%s",
                  "old_text": "World",
                  "new_text": "Boris"
                }
                ```
                """.formatted(targetFile.toAbsolutePath().toString().replace("\\", "\\\\"));

        List<ToolFallbackHandler.FallbackResult> results = ToolFallbackHandler.handleFallback(responseText);

        assertEquals(1, results.size());
        assertTrue(results.get(0).success());
        assertEquals("apply_edit", results.get(0).toolName());
        assertEquals("Hello Boris", Files.readString(targetFile));
    }

    @Test
    void testNormalTextDoesNotTriggerFallback() {
        String responseText = "Hola, ¿en qué te puedo ayudar hoy? No tengo ningún tool que ejecutar.";
        List<ToolFallbackHandler.FallbackResult> results = ToolFallbackHandler.handleFallback(responseText);
        assertTrue(results.isEmpty());
    }

    @Test
    void testInvalidJsonDoesNotCrash() {
        String responseText = "```json\n{ not valid json\n```";
        List<ToolFallbackHandler.FallbackResult> results = ToolFallbackHandler.handleFallback(responseText);
        assertTrue(results.isEmpty());
    }

    @Test
    void testDeepSeekR1FilesArrayNoCodeFence() throws IOException {
        // Exact format DeepSeek-R1 outputs: raw JSON with "files" array, no markdown fences
        Path targetFile = tempDir.resolve("hola.md");
        String responseText = """
                {
                  "response": "**Hola Mundo** en Markdown generado:",
                  "files": [
                    {
                      "path": "%s",
                      "content": "# Hola Mundo\\n\\nEste es un documento Markdown con el mensaje solicitado."
                    }
                  ]
                }
                """.formatted(targetFile.toAbsolutePath().toString().replace("\\", "\\\\"));

        List<ToolFallbackHandler.FallbackResult> results = ToolFallbackHandler.handleFallback(responseText);

        assertEquals(1, results.size());
        ToolFallbackHandler.FallbackResult res = results.get(0);
        assertTrue(res.executed(), "Fallback should have executed");
        assertTrue(res.success(), "Fallback should have succeeded");
        assertEquals("write_file", res.toolName());
        assertTrue(Files.exists(targetFile), "File should exist on disk");
    }

    @Test
    void testRawJsonWithPathAndContent() throws IOException {
        // Raw JSON without code fences, simple path+content structure
        Path targetFile = tempDir.resolve("raw.txt");
        String responseText = "Aquí está el resultado: {\"path\": \"" +
                targetFile.toAbsolutePath().toString().replace("\\", "\\\\") +
                "\", \"content\": \"hola mundo\"}";

        List<ToolFallbackHandler.FallbackResult> results = ToolFallbackHandler.handleFallback(responseText);

        assertEquals(1, results.size());
        assertTrue(results.get(0).executed());
        assertTrue(results.get(0).success());
        assertTrue(Files.exists(targetFile));
        assertEquals("hola mundo", Files.readString(targetFile));
    }
}
