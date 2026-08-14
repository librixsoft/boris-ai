package com.boris.llm;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LlmClientTest {

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
        Path settingsFile = tempDir.resolve("settings.json");
        Files.writeString(settingsFile, """
                {
                  "model": {
                    "baseUrl": "http://localhost:11434/v1",
                    "name": "qwen3.6-35b-64k"
                  },
                  "env": {
                    "OLLAMA_API_KEY": "ollama"
                  }
                }\
                """);

        assertDoesNotThrow(() -> new LlmClient(settingsFile.toString()));
    }

    @Test
    void constructor_throwsWhenModelFieldMissing() throws Exception {
        Path settingsFile = tempDir.resolve("settings.json");
        Files.writeString(settingsFile, """
                {
                  "env": {
                    "OLLAMA_API_KEY": "ollama"
                  }
                }\
                """);

        assertThrows(Exception.class, () -> new LlmClient(settingsFile.toString()));
    }
}
