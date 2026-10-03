package com.boris.llm.think;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

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
    void createChatModel_setsReasoningEffortNone_whenThinkDisabled() {
        var model = OllamaThinkSpringAiFactory.createChatModel(settingsWithOptions(null), null);
        var options = (org.springframework.ai.openai.OpenAiChatOptions) model.getDefaultOptions();
        assertEquals("none", options.getReasoningEffort());
    }

    @Test
    void createChatModel_setsReasoningEffortLevel_whenThinkEnabled() {
        var model = OllamaThinkSpringAiFactory.createChatModel(settingsWithOptions("low"), "low");
        var options = (org.springframework.ai.openai.OpenAiChatOptions) model.getDefaultOptions();
        assertEquals("low", options.getReasoningEffort());
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
    void injectThink_sendsFalseBoolean_whenThinkDisabled() throws Exception {
        byte[] body = "{\"model\": \"granite4.2:8b\", \"messages\": []}".getBytes();
        var root = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(ThinkClientHttpInterceptor.injectThink(body, Boolean.FALSE));
        assertTrue(root.get("think").isBoolean());
        assertFalse(root.get("think").asBoolean());
        assertTrue(root.get("options").get("think").isBoolean());
        assertFalse(root.get("options").get("think").asBoolean());
    }

    @Test
    void injectThink_sendsThinkString_whenThinkEnabled() throws Exception {
        byte[] body = "{\"model\": \"granite4.2:8b\", \"messages\": []}".getBytes();
        var root = new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(ThinkClientHttpInterceptor.injectThink(body, "low"));
        assertEquals("low", root.get("think").asText());
        assertEquals("low", root.get("options").get("think").asText());
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
