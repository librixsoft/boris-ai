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

class SearchReplaceEditToolTest {

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
    void testSingleReplacement() throws Exception {
        Path targetFile = tempDir.resolve("Test.java");
        Files.writeString(targetFile, "public class Test {\n    int valor = 10;\n}\n");

        List<Map<String, String>> replacements = List.of(
            Map.of("search", "valor = 10", "replace", "valor = 20")
        );

        String result = SearchReplaceEditTool.search_replace(targetFile.toString(), replacements);
        JsonNode json = mapper.readTree(result);

        assertTrue(json.get("success").asBoolean());
        String content = Files.readString(targetFile);
        assertTrue(content.contains("valor = 20"));
        assertFalse(content.contains("valor = 10"));
    }

    @Test
    void testMultipleReplacements() throws Exception {
        Path targetFile = tempDir.resolve("Multi.java");
        Files.writeString(targetFile, "class A {\n    int x = 1;\n    int y = 2;\n}\n");

        List<Map<String, String>> replacements = List.of(
            Map.of("search", "x = 1", "replace", "x = 100"),
            Map.of("search", "y = 2", "replace", "y = 200")
        );

        String result = SearchReplaceEditTool.search_replace(targetFile.toString(), replacements);
        JsonNode json = mapper.readTree(result);

        assertTrue(json.get("success").asBoolean());
        String content = Files.readString(targetFile);
        assertTrue(content.contains("x = 100"));
        assertTrue(content.contains("y = 200"));
    }

    @Test
    void testErrorWhenSearchNotFound() throws Exception {
        Path targetFile = tempDir.resolve("NotFound.java");
        Files.writeString(targetFile, "contenido original");

        List<Map<String, String>> replacements = List.of(
            Map.of("search", "texto inexistente", "replace", "nuevo")
        );

        String result = SearchReplaceEditTool.search_replace(targetFile.toString(), replacements);
        JsonNode json = mapper.readTree(result);

        assertFalse(json.get("success").asBoolean());
        assertTrue(json.get("message").asText().toLowerCase().contains("no encontrado") ||
                   json.has("errors"));
        // El archivo no debería cambiar
        assertEquals("contenido original", Files.readString(targetFile));
    }

    @Test
    void testErrorWhenSearchAmbiguous() throws Exception {
        Path targetFile = tempDir.resolve("Ambiguous.java");
        Files.writeString(targetFile, "foo bar foo baz foo");

        List<Map<String, String>> replacements = List.of(
            Map.of("search", "foo", "replace", "qux")
        );

        String result = SearchReplaceEditTool.search_replace(targetFile.toString(), replacements);
        JsonNode json = mapper.readTree(result);

        assertFalse(json.get("success").asBoolean());
        // El archivo no debería cambiar
        assertEquals("foo bar foo baz foo", Files.readString(targetFile));
    }

    @Test
    void testErrorWhenFileNotExists() throws Exception {
        Path targetFile = tempDir.resolve("NoExiste.java");

        List<Map<String, String>> replacements = List.of(
            Map.of("search", "algo", "replace", "otro")
        );

        String result = SearchReplaceEditTool.search_replace(targetFile.toString(), replacements);
        JsonNode json = mapper.readTree(result);

        assertFalse(json.get("success").asBoolean());
    }

    @Test
    void testErrorWhenPathNull() throws Exception {
        List<Map<String, String>> replacements = List.of(
            Map.of("search", "algo", "replace", "otro")
        );

        String result = SearchReplaceEditTool.search_replace(null, replacements);
        JsonNode json = mapper.readTree(result);

        assertFalse(json.get("success").asBoolean());
    }

    @Test
    void testErrorWhenReplacementsEmpty() throws Exception {
        Path targetFile = tempDir.resolve("Empty.java");
        Files.writeString(targetFile, "contenido");

        String result = SearchReplaceEditTool.search_replace(targetFile.toString(), List.of());
        JsonNode json = mapper.readTree(result);

        assertFalse(json.get("success").asBoolean());
    }

    @Test
    void testDeleteByReplacingWithEmpty() throws Exception {
        Path targetFile = tempDir.resolve("Delete.java");
        Files.writeString(targetFile, "linea1\nlinea_a_borrar\nlinea3");

        List<Map<String, String>> replacements = List.of(
            Map.of("search", "linea_a_borrar\n", "replace", "")
        );

        String result = SearchReplaceEditTool.search_replace(targetFile.toString(), replacements);
        JsonNode json = mapper.readTree(result);

        assertTrue(json.get("success").asBoolean());
        String content = Files.readString(targetFile);
        assertFalse(content.contains("linea_a_borrar"));
        assertTrue(content.contains("linea1"));
        assertTrue(content.contains("linea3"));
    }

    @Test
    void testReplacementCount() throws Exception {
        Path targetFile = tempDir.resolve("Count.java");
        Files.writeString(targetFile, "a = 1;\nb = 2;\nc = 3;");

        List<Map<String, String>> replacements = List.of(
            Map.of("search", "a = 1", "replace", "a = 10"),
            Map.of("search", "b = 2", "replace", "b = 20")
        );

        String result = SearchReplaceEditTool.search_replace(targetFile.toString(), replacements);
        JsonNode json = mapper.readTree(result);

        assertTrue(json.get("success").asBoolean());
        assertTrue(json.has("replacements"));
        assertEquals(2, json.get("replacements").asInt());
    }

    @Test
    void testNotifiesUIOnSuccess() throws Exception {
        Path targetFile = tempDir.resolve("Notify.java");
        Files.writeString(targetFile, "original");

        List<Map<String, String>> replacements = List.of(
            Map.of("search", "original", "replace", "nuevo")
        );

        SearchReplaceEditTool.search_replace(targetFile.toString(), replacements);

        assertTrue(notifications.size() > 0);
    }
}
