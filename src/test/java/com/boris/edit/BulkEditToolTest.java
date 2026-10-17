package com.boris.edit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.boris.tooling.DiffLineNotifier;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class BulkEditToolTest {

    @TempDir
    Path tempDir;

    private ObjectMapper mapper;
    private List<String> notifications;

    @BeforeEach
    void setUp() {
        mapper = new ObjectMapper();
        notifications = new ArrayList<>();
        DiffLineNotifier.clearListeners();
        DiffLineNotifier.addListener(notifications::add);
    }

    @Test
    void testCreatesNewFile() throws Exception {
        Path targetFile = tempDir.resolve("NewFile.java");

        String content = "package test;\n\npublic class NewFile {\n}\n";

        Map<String, Object> args = Map.of(
            "path", targetFile.toString(),
            "content", content
        );

        String result = BulkEditTool.bulk_edit(args);
        JsonNode json = mapper.readTree(result);

        assertTrue(json.get("success").asBoolean());
        assertTrue(Files.exists(targetFile));
        assertEquals(content, Files.readString(targetFile));
    }

    @Test
    void testOverwritesExistingFile() throws Exception {
        Path targetFile = tempDir.resolve("Existing.java");
        Files.writeString(targetFile, "contenido viejo");

        String newContent = "contenido nuevo\n";

        Map<String, Object> args = Map.of(
            "path", targetFile.toString(),
            "content", newContent
        );

        String result = BulkEditTool.bulk_edit(args);
        JsonNode json = mapper.readTree(result);

        assertTrue(json.get("success").asBoolean());
        assertEquals(newContent, Files.readString(targetFile));
    }

    @Test
    void testReturnsErrorForEmptyPath() throws Exception {
        Map<String, Object> args = Map.of(
            "path", "",
            "content", "algo"
        );

        String result = BulkEditTool.bulk_edit(args);
        JsonNode json = mapper.readTree(result);

        assertFalse(json.get("success").asBoolean());
        assertTrue(json.get("message").asText().contains("requerido"));
    }

    @Test
    void testReturnsErrorForNullPath() throws Exception {
        Map<String, Object> args = Map.of(
            "content", "algo"
        );

        String result = BulkEditTool.bulk_edit(args);
        JsonNode json = mapper.readTree(result);

        assertFalse(json.get("success").asBoolean());
    }

    @Test
    void testCreatesParentDirectories() throws Exception {
        Path targetFile = tempDir.resolve("deep/nested/dir/File.java");

        Map<String, Object> args = Map.of(
            "path", targetFile.toString(),
            "content", "contenido"
        );

        String result = BulkEditTool.bulk_edit(args);
        JsonNode json = mapper.readTree(result);

        assertTrue(json.get("success").asBoolean());
        assertTrue(Files.exists(targetFile));
        assertTrue(Files.exists(targetFile.getParent()));
    }

    @Test
    void testNotifiesUIForEachLine() throws Exception {
        Path targetFile = tempDir.resolve("Notify.java");

        String content = "linea1\nlinea2\nlinea3\n";

        Map<String, Object> args = Map.of(
            "path", targetFile.toString(),
            "content", content
        );

        BulkEditTool.bulk_edit(args);

        // Debería notificar header + líneas
        assertTrue(notifications.size() >= 3);
        assertTrue(notifications.stream().anyMatch(n -> n.contains("@@@")));
    }

    @Test
    void testHandlesEmptyContent() throws Exception {
        Path targetFile = tempDir.resolve("Empty.java");

        Map<String, Object> args = Map.of(
            "path", targetFile.toString(),
            "content", ""
        );

        String result = BulkEditTool.bulk_edit(args);
        JsonNode json = mapper.readTree(result);

        assertTrue(json.get("success").asBoolean());
        assertTrue(Files.exists(targetFile));
    }

    @Test
    void testReturnsLineCount() throws Exception {
        Path targetFile = tempDir.resolve("Lines.java");

        String content = "linea1\nlinea2\nlinea3\nlinea4\n";

        Map<String, Object> args = Map.of(
            "path", targetFile.toString(),
            "content", content
        );

        String result = BulkEditTool.bulk_edit(args);
        JsonNode json = mapper.readTree(result);

        assertTrue(json.get("success").asBoolean());
        assertTrue(json.has("lines"));
        assertEquals(5, json.get("lines").asInt()); // 4 líneas + 1 vacía al final del split
    }

    @Test
    void testReturnsPath() throws Exception {
        Path targetFile = tempDir.resolve("Path.java");

        Map<String, Object> args = Map.of(
            "path", targetFile.toString(),
            "content", "contenido"
        );

        String result = BulkEditTool.bulk_edit(args);
        JsonNode json = mapper.readTree(result);

        assertTrue(json.has("path"));
        assertEquals(targetFile.toString(), json.get("path").asText());
    }
}
