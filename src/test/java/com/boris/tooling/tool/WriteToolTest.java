package com.boris.tooling.tool;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class WriteToolTest {

    private final WriteTool writeTool = new WriteTool(8000);

    @TempDir
    Path tempDir;

    @Test
    void write_file_createsNewFile() throws Exception {
        Path file = tempDir.resolve("new.txt");

        String result = writeTool.execute(Map.of("path", file.toString(), "content", "test content"));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(Files.exists(file));
        assertEquals("test content", Files.readString(file));
    }

    @Test
    void write_file_overwritesExistingFile() throws Exception {
        Path file = tempDir.resolve("overwrite.txt");
        Files.writeString(file, "old content");

        String result = writeTool.execute(Map.of("path", file.toString(), "content", "new content"));

        assertTrue(result.contains("\"success\":true"));
        assertEquals("new content", Files.readString(file));
    }

    @Test
    void write_file_createsParentDirectories() throws Exception {
        Path deepDir = tempDir.resolve("a/b/c");
        Path file = deepDir.resolve("file.txt");

        String result = writeTool.execute(Map.of("path", file.toString(), "content", "deep"));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(Files.exists(file));
    }

    @Test
    void write_file_returnsError_whenPathBlank() {
        String result = writeTool.execute(Map.of("path", "", "content", "x"));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("path is required"));
    }

    @Test
    void write_file_rejectsContentExceedingLimit() {
        Path file = tempDir.resolve("big.txt");
        String hugeContent = "x".repeat(20000);

        String result = writeTool.execute(Map.of("path", file.toString(), "content", hugeContent));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("exceeds the model context limit"));
        assertTrue(!Files.exists(file));
    }

    @Test
    void write_file_acceptsContentWithinLimit() {
        Path file = tempDir.resolve("ok.txt");
        String content = "x".repeat(1000);

        String result = writeTool.execute(Map.of("path", file.toString(), "content", content));

        assertTrue(result.contains("\"success\":true"));
    }

    @Test
    void write_file_definition_hasParametersSchema() {
        var def = WriteTool.write_file();
        Map<String, Object> props = (Map<String, Object>) def.parameters().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("path"));
        assertTrue(props.containsKey("content"));
    }
}
