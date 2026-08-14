package com.boris.tooling.tool;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DeleteToolTest {

    @TempDir
    Path tempDir;

    @Test
    void delete_file_deletesExistingFile() throws Exception {
        Path file = tempDir.resolve("to_delete.txt");
        Files.writeString(file, "delete me");

        String result = DeleteTool.execute(Map.of("path", file.toString()));

        assertTrue(result.contains("\"success\":true"));
        assertFalse(Files.exists(file));
    }

    @Test
    void delete_file_returnsSuccess_whenFileNotFound() {
        String result = DeleteTool.execute(Map.of("path", "/nonexistent/file.txt"));

        assertTrue(result.contains("\"success\":true"));
    }

    @Test
    void delete_file_returnsError_forDirectory() throws Exception {
        Path dir = tempDir.resolve("adir");
        Files.createDirectory(dir);

        String result = DeleteTool.execute(Map.of("path", dir.toString()));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("directory"));
    }

    @Test
    void delete_file_returnsError_whenPathBlank() {
        String result = DeleteTool.execute(Map.of("path", ""));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("path is required"));
    }

    @Test
    void delete_file_definition_hasParametersSchema() {
        var def = DeleteTool.delete_file();
        Map<String, Object> props = (Map<String, Object>) def.parameters().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("path"));
    }
}
