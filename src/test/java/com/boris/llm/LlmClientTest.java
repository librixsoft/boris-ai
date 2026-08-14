package com.boris.llm;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.boris.settings.Settings;

import static org.junit.jupiter.api.Assertions.*;

class LlmClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    @TempDir
    Path tempDir;

    @Test
    void constructor_throwsWhenSettingsMissing() {
        assertThrows(IllegalStateException.class, () -> {
            new LlmClient("/nonexistent/path/settings.json").send("hola");
        });
    }

    @Test
    void constructor_succeedsWithValidSettingsFile() throws Exception {
        Settings settings = new Settings(
            new com.boris.settings.ModelConfig("http://localhost:11434/v1", "qwen3.6-35b-64k"),
            java.util.Map.of("OLLAMA_API_KEY", "ollama")
        );

        Path settingsFile = tempDir.resolve("settings.json");
        Files.writeString(settingsFile, MAPPER.writeValueAsString(settings));

        assertDoesNotThrow(() -> new LlmClient(settingsFile.toString()));
    }

    @Test
    void constructor_throwsWhenModelFieldMissing() throws Exception {
        Settings settings = new Settings(
            null,
            java.util.Map.of("OLLAMA_API_KEY", "ollama")
        );

        Path settingsFile = tempDir.resolve("settings.json");
        Files.writeString(settingsFile, MAPPER.writeValueAsString(settings));

        assertThrows(Exception.class, () -> new LlmClient(settingsFile.toString()));
    }
}
