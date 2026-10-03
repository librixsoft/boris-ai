package com.boris.tooling.tool;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EditToolTest {

    @TempDir
    Path tempDir;

    // --- apply_edit ---

    @Test
    void apply_edit_replacesText_whenFound() throws Exception {
        Path file = tempDir.resolve("test.txt");
        Files.writeString(file, "hello world foo bar");

        String result = EditTool.apply_edit(Map.of(
                "path", file.toString(),
                "old_text", "foo",
                "new_text", "bar"
        ));

        assertTrue(result.contains("\"success\":true"));
        assertEquals("hello world bar bar", Files.readString(file));
    }

    @Test
    void apply_edit_returnsError_whenOldTextNotFound() throws Exception {
        Path file = tempDir.resolve("test2.txt");
        Files.writeString(file, "hello world");

        String result = EditTool.apply_edit(Map.of(
                "path", file.toString(),
                "old_text", "xyz",
                "new_text", "abc"
        ));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("not found"));
    }

    @Test
    void apply_edit_returnsError_whenPathBlank() throws Exception {
        String result = EditTool.apply_edit(Map.of(
                "path", "",
                "old_text", "x",
                "new_text", "y"
        ));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("path is required"));
    }

    @Test
    void apply_edit_returnsError_whenOldTextBlank() throws Exception {
        String result = EditTool.apply_edit(Map.of(
                "path", "/some/path",
                "old_text", "",
                "new_text", "y"
        ));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("old_text is required"));
    }

    @Test
    void apply_edit_returnsError_whenFileNotFound() {
        String result = EditTool.apply_edit(Map.of(
                "path", "/nonexistent/file.txt",
                "old_text", "x",
                "new_text", "y"
        ));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("not found"));
    }

    @Test
    void apply_edit_replacesFirstOccurrenceOnly() throws Exception {
        Path file = tempDir.resolve("multi.txt");
        Files.writeString(file, "aaa bbb aaa ccc aaa");

        String result = EditTool.apply_edit(Map.of(
                "path", file.toString(),
                "old_text", "aaa",
                "new_text", "zzz"
        ));

        assertTrue(result.contains("\"success\":true"));
        assertEquals("zzz bbb aaa ccc aaa", Files.readString(file));
    }

    @Test
    void apply_edit_handlesPlainReplacementText() throws Exception {
        Path file = tempDir.resolve("special.txt");
        Files.writeString(file, "hello world");

        String result = EditTool.apply_edit(Map.of(
                "path", file.toString(),
                "old_text", "world",
                "new_text", "replaced text"
        ));

        assertTrue(result.contains("\"success\":true"));
        assertEquals("hello replaced text", Files.readString(file));
    }

    // --- multi_edit ---

    @Test
    void multi_edit_appliesMultipleEdits() throws Exception {
        Path file = tempDir.resolve("multi-edit.txt");
        Files.writeString(file, "alpha beta gamma delta");

        String result = EditTool.multi_edit(Map.of(
                "path", file.toString(),
                "edits", List.of(
                        Map.of("old_text", "beta", "new_text", "BETA"),
                        Map.of("old_text", "delta", "new_text", "DELTA")
                )
        ));

        assertTrue(result.contains("\"success\":true"));
        assertEquals("alpha BETA gamma DELTA", Files.readString(file));
    }

    @Test
    void multi_edit_returnsError_whenEditsIsNull() throws Exception {
        Path file = tempDir.resolve("null-edits.txt");
        Files.writeString(file, "hello");

        Map<String, Object> params = new java.util.HashMap<>();
        params.put("path", file.toString());
        params.put("edits", (List<Map<String, Object>>) null);

        String result = EditTool.multi_edit(params);

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("edits array is required"));
    }

    @Test
    void multi_edit_returnsError_whenEditsIsEmpty() throws Exception {
        Path file = tempDir.resolve("empty-edits.txt");
        Files.writeString(file, "hello");

        String result = EditTool.multi_edit(Map.of(
                "path", file.toString(),
                "edits", List.of()
        ));

        assertTrue(result.contains("\"success\":false"));
    }

    @Test
    void multi_edit_returnsError_whenOldTextNotFoundAtIndex() throws Exception {
        Path file = tempDir.resolve("fail-index.txt");
        Files.writeString(file, "hello world");

        String result = EditTool.multi_edit(Map.of(
                "path", file.toString(),
                "edits", List.of(
                        Map.of("old_text", "hello", "new_text", "hi"),
                        Map.of("old_text", "nonexistent", "new_text", "x")
                )
        ));

        assertTrue(result.contains("\"success\":false"));
    }

    @Test
    void multi_edit_returnsError_whenEditHasEmptyOldText() throws Exception {
        Path file = tempDir.resolve("empty-old.txt");
        Files.writeString(file, "hello world");

        String result = EditTool.multi_edit(Map.of(
                "path", file.toString(),
                "edits", List.of(
                        Map.of("old_text", "", "new_text", "x")
                )
        ));

        assertTrue(result.contains("\"success\":false"));
    }

    @Test
    void multi_edit_returnsError_whenPathBlank() {
        String result = EditTool.multi_edit(Map.of(
                "path", "",
                "edits", List.of(Map.of("old_text", "x", "new_text", "y"))
        ));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("path is required"));
    }

    // --- revert_edit ---

    @Test
    void revert_edit_restoresOriginalText() throws Exception {
        Path file = tempDir.resolve("revert.txt");
        Files.writeString(file, "old content modified");

        String result = EditTool.revert_edit(Map.of(
                "path", file.toString(),
                "old_text", "modified",
                "new_text", "original"
        ));

        assertTrue(result.contains("\"success\":true"));
        assertEquals("old content original", Files.readString(file));
    }

    @Test
    void revert_edit_returnsError_whenOldTextNotFound() throws Exception {
        Path file = tempDir.resolve("revert-fail.txt");
        Files.writeString(file, "hello world");

        String result = EditTool.revert_edit(Map.of(
                "path", file.toString(),
                "old_text", "xyz",
                "new_text", "abc"
        ));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("not found"));
    }

    @Test
    void revert_edit_returnsError_whenPathBlank() {
        String result = EditTool.revert_edit(Map.of(
                "path", "",
                "old_text", "x",
                "new_text", "y"
        ));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("path is required"));
    }

    @Test
    void revert_edit_returnsError_whenOldTextBlank() {
        String result = EditTool.revert_edit(Map.of(
                "path", "/some/path",
                "old_text", "",
                "new_text", "y"
        ));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("old_text is required"));
    }

    @Test
    void revert_edit_returnsError_whenFileNotFound() {
        String result = EditTool.revert_edit(Map.of(
                "path", "/nonexistent/file.txt",
                "old_text", "x",
                "new_text", "y"
        ));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("not found"));
    }

    // --- ToolDefinition builders ---

    @Test
    void apply_edit_definition_hasCorrectNameAndDescription() {
        var def = EditTool.apply_edit();
        assertEquals("apply_edit", def.name());
        assertFalse(def.description().isBlank());
    }

    @Test
    void apply_edit_definition_hasParametersSchema() {
        var def = EditTool.apply_edit();
        Map<String, Object> props = (Map<String, Object>) def.parameters().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("path"));
        assertTrue(props.containsKey("old_text"));
        assertTrue(props.containsKey("new_text"));
    }

    @Test
    void multi_edit_definition_hasCorrectNameAndDescription() {
        var def = EditTool.multi_edit();
        assertEquals("multi_edit", def.name());
        assertFalse(def.description().isBlank());
    }

    @Test
    void multi_edit_definition_hasParametersSchema() {
        var def = EditTool.multi_edit();
        Map<String, Object> props = (Map<String, Object>) def.parameters().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("path"));
        assertTrue(props.containsKey("edits"));
    }

    @Test
    void revert_edit_definition_hasCorrectNameAndDescription() {
        var def = EditTool.revert_edit();
        assertEquals("revert_edit", def.name());
        assertFalse(def.description().isBlank());
    }

    @Test
    void revert_edit_definition_hasParametersSchema() {
        var def = EditTool.revert_edit();
        Map<String, Object> props = (Map<String, Object>) def.parameters().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("path"));
        assertTrue(props.containsKey("old_text"));
        assertTrue(props.containsKey("new_text"));
    }
}
