package com.boris.tooling.tool;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ListFilesToolTest {

    @TempDir
    Path tempDir;

    @Test
    void list_files_listsDirectoryContents() throws Exception {
        Path file1 = tempDir.resolve("file_a.txt");
        Files.writeString(file1, "a");
        Path dir1 = tempDir.resolve("dir_b");
        Files.createDirectory(dir1);

        String result = ListFilesTool.execute(Map.of("path", tempDir.toString()));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(result.contains("file_a.txt"));
        assertTrue(result.contains("dir_b"));
    }

    @Test
    void list_files_returnsError_whenPathNotFound() {
        String result = ListFilesTool.execute(Map.of("path", "/nonexistent/dir"));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("not found"));
    }

    @Test
    void list_files_returnsError_whenPathNotDirectory() throws Exception {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "x");

        String result = ListFilesTool.execute(Map.of("path", file.toString()));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("Not a directory"));
    }

    @Test
    void list_files_returnsError_whenPathBlank() {
        String result = ListFilesTool.execute(Map.of("path", ""));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("path is required"));
    }

    @Test
    void list_files_definition_hasParametersSchema() {
        var def = ListFilesTool.list_files();
        Map<String, Object> props = (Map<String, Object>) def.parameters().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("path"));
    }
}
