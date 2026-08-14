package com.boris.tooling.tool;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FileToolTest {

    @TempDir
    Path tempDir;

    // --- read_file ---

    @Test
    void read_file_returnsContent_whenFileExists() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world");

        String result = FileTool.read_file(Map.of("path", file.toString()));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(result.contains("hello world"));
    }

    @Test
    void read_file_returnsError_whenFileNotFound() {
        String result = FileTool.read_file(Map.of("path", "/nonexistent/file.txt"));

        assertTrue(result.contains("Error"));
        assertTrue(result.contains("not found"));
    }

    @Test
    void read_file_returnsError_whenPathBlank() {
        String result = FileTool.read_file(Map.of("path", ""));

        assertTrue(result.contains("Error"));
        assertTrue(result.contains("path is required"));
    }

    @Test
    void read_file_returnsError_whenNullPath() {
        String result = FileTool.read_file(Map.of());

        assertTrue(result.contains("Error"));
        assertTrue(result.contains("path is required"));
    }

    // --- write_file ---

    @Test
    void write_file_createsNewFile() throws Exception {
        Path file = tempDir.resolve("new.txt");

        String result = FileTool.write_file(Map.of("path", file.toString(), "content", "test content"));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(Files.exists(file));
        assertEquals("test content", Files.readString(file));
    }

    @Test
    void write_file_overwritesExistingFile() throws Exception {
        Path file = tempDir.resolve("overwrite.txt");
        Files.writeString(file, "old content");

        String result = FileTool.write_file(Map.of("path", file.toString(), "content", "new content"));

        assertTrue(result.contains("\"success\":true"));
        assertEquals("new content", Files.readString(file));
    }

    @Test
    void write_file_createsParentDirectories() throws Exception {
        Path deepDir = tempDir.resolve("a/b/c");
        Path file = deepDir.resolve("file.txt");

        String result = FileTool.write_file(Map.of("path", file.toString(), "content", "deep"));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(Files.exists(file));
    }

    @Test
    void write_file_returnsError_whenPathBlank() {
        String result = FileTool.write_file(Map.of("path", "", "content", "x"));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("path is required"));
    }

    // --- delete_file ---

    @Test
    void delete_file_deletesExistingFile() throws Exception {
        Path file = tempDir.resolve("to_delete.txt");
        Files.writeString(file, "delete me");

        String result = FileTool.delete_file(Map.of("path", file.toString()));

        assertTrue(result.contains("\"success\":true"));
        assertFalse(Files.exists(file));
    }

    @Test
    void delete_file_returnsSuccess_whenFileNotFound() {
        String result = FileTool.delete_file(Map.of("path", "/nonexistent/file.txt"));

        assertTrue(result.contains("\"success\":true"));
    }

    @Test
    void delete_file_returnsError_forDirectory() throws Exception {
        Path dir = tempDir.resolve("adir");
        Files.createDirectory(dir);

        String result = FileTool.delete_file(Map.of("path", dir.toString()));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("directory"));
    }

    @Test
    void delete_file_returnsError_whenPathBlank() {
        String result = FileTool.delete_file(Map.of("path", ""));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("path is required"));
    }

    // --- list_files ---

    @Test
    void list_files_listsDirectoryContents() throws Exception {
        Path file1 = tempDir.resolve("file_a.txt");
        Files.writeString(file1, "a");
        Path dir1 = tempDir.resolve("dir_b");
        Files.createDirectory(dir1);

        String result = FileTool.list_files(Map.of("path", tempDir.toString()));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(result.contains("file_a.txt"));
        assertTrue(result.contains("dir_b"));
    }

    @Test
    void list_files_returnsError_whenPathNotFound() {
        String result = FileTool.list_files(Map.of("path", "/nonexistent/dir"));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("not found"));
    }

    @Test
    void list_files_returnsError_whenPathNotDirectory() throws Exception {
        Path file = tempDir.resolve("file.txt");
        Files.writeString(file, "x");

        String result = FileTool.list_files(Map.of("path", file.toString()));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("Not a directory"));
    }

    @Test
    void list_files_returnsError_whenPathBlank() {
        String result = FileTool.list_files(Map.of("path", ""));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("path is required"));
    }

    // --- ToolDefinition builders ---

    @Test
    void read_file_definition_hasCorrectNameAndDescription() {
        var def = FileTool.read_file();
        assertEquals("read_file", def.name());
        assertFalse(def.description().isBlank());
    }

    @Test
    void write_file_definition_hasParametersSchema() {
        var def = FileTool.write_file();
        Map<String, Object> props = (Map<String, Object>) def.parameters().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("path"));
        assertTrue(props.containsKey("content"));
    }

    @Test
    void delete_file_definition_hasParametersSchema() {
        var def = FileTool.delete_file();
        Map<String, Object> props = (Map<String, Object>) def.parameters().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("path"));
    }

    @Test
    void list_files_definition_hasParametersSchema() {
        var def = FileTool.list_files();
        Map<String, Object> props = (Map<String, Object>) def.parameters().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("path"));
    }
}
