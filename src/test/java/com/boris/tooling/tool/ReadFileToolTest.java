package com.boris.tooling.tool;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ReadFileToolTest {

    @TempDir
    Path tempDir;

    @Test
    void read_file_returnsContent_whenFileExists() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world");

        String result = new ReadFileTool().execute(Map.of("path", file.toString()));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(result.contains("hello world"));
    }

    @Test
    void read_file_returnsError_whenFileNotFound() {
        String result = new ReadFileTool().execute(Map.of("path", "/nonexistent/file.txt"));

        assertTrue(result.contains("Error"));
        assertTrue(result.contains("not found"));
    }

    @Test
    void read_file_returnsError_whenPathBlank() {
        String result = new ReadFileTool().execute(Map.of("path", ""));

        assertTrue(result.contains("Error"));
        assertTrue(result.contains("path is required"));
    }

    @Test
    void read_file_returnsError_whenNullPath() {
        String result = new ReadFileTool().execute(Map.of());

        assertTrue(result.contains("Error"));
        assertTrue(result.contains("path is required"));
    }

    @Test
    void read_file_returnsError_forDirectory() throws Exception {
        Path dir = tempDir.resolve("adir");
        Files.createDirectory(dir);

        String result = new ReadFileTool().execute(Map.of("path", dir.toString()));

        assertTrue(result.contains("Error"));
        assertTrue(result.contains("not a regular file"));
    }

    @Test
    void read_file_definition_hasCorrectNameAndDescription() {
        var def = new ReadFileTool().read_file();
        assertEquals("read_file", def.name());
        assertFalse(def.description().isBlank());
    }
}
