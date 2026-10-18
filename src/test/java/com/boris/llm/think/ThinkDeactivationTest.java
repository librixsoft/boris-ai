package com.boris.llm.think;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.boris.llm.client.BorisLlmClient;
import com.boris.llm.client.ChatRequest;
import com.boris.settings.ModelConfig;
import com.boris.settings.Settings;
import com.boris.settings.SettingsManager;

import static org.junit.jupiter.api.Assertions.*;

class ThinkDeactivationTest {

    @TempDir
    Path tempDir;

    private static Settings settingsWithOptions(Object thinkValue) {
        Settings s = new Settings(new ModelConfig("http://localhost:11434", "granite4.2:8b"), Map.of());
        s.setOptions(thinkValue == null ? null : Map.of("think", thinkValue));
        return s;
    }

    @Test
    void resolveThinkMode_returnsNull_whenOptionsMissing() {
        Settings s = new Settings(new ModelConfig("http://localhost:11434", "granite4.2:8b"), Map.of());
        assertNull(OllamaThinkSpringAiFactory.resolveThinkMode(s));
    }

    @Test
    void resolveThinkMode_returnsNull_whenSettingsNull() {
        assertNull(OllamaThinkSpringAiFactory.resolveThinkMode(null));
    }

    @Test
    void resolveThinkMode_returnsValue_whenThinkPresent() {
        assertEquals("low", OllamaThinkSpringAiFactory.resolveThinkMode(settingsWithOptions("low")));
    }

    @Test
    void resolveThinkMode_returnsNull_whenThinkBlankOrNotString() {
        assertNull(OllamaThinkSpringAiFactory.resolveThinkMode(settingsWithOptions("  ")));
        assertNull(OllamaThinkSpringAiFactory.resolveThinkMode(settingsWithOptions(42)));
    }

    @Test
    void createClient_configuresBaseUrlCorrectly() {
        BorisLlmClient client = OllamaThinkSpringAiFactory.createClient(settingsWithOptions(null));
        assertNotNull(client);
        assertEquals("http://localhost:11434", client.getBaseUrl());
    }

    @Test
    void chatRequest_setsReasoningEffortNone_whenThinkDisabled() {
        ChatRequest req = ChatRequest.builder()
                .model("granite4.2:8b")
                .reasoningEffort("none")
                .think(false)
                .build();
        assertEquals("none", req.reasoningEffort());
        assertEquals(Boolean.FALSE, req.think());
    }

    @Test
    void chatRequest_setsReasoningEffortLevel_whenThinkEnabled() {
        ChatRequest req = ChatRequest.builder()
                .model("granite4.2:8b")
                .reasoningEffort("low")
                .think("low")
                .build();
        assertEquals("low", req.reasoningEffort());
        assertEquals("low", req.think());
    }

    @Test
    void loadSettings_loadsFileWithoutOptions() throws Exception {
        Path file = tempDir.resolve("settings.json");
        Files.writeString(file, """
                {"model": {"baseUrl": "http://localhost:11434", "name": "granite4.2:8b"},
                 "maxHistorySize": 20, "temperature": 0.1, "contextWindow": 42000}""");
        Settings s = new SettingsManager().loadSettings(file.toString());
        assertNotNull(s);
        assertNull(s.getOptions());
        assertNull(OllamaThinkSpringAiFactory.resolveThinkMode(s));
    }

    @Test
    void loadSettings_throwsClearError_whenJsonMalformed() throws Exception {
        Path file = tempDir.resolve("settings.json");
        Files.writeString(file, "{\"contextWindow\": 42000,\n}");
        var ex = assertThrows(com.boris.exceptions.BorisException.class,
                () -> new SettingsManager().loadSettings(file.toString()));
        assertTrue(ex.getMessage().contains(file.toString()));
    }
}
