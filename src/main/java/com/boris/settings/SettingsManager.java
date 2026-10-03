package com.boris.settings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class SettingsManager {

    private static final String DEFAULT_SETTINGS_PATH = System.getProperty("user.home") + "/.boris/settings.json";

    private static final String SETTINGS_JSON_RESOURCE = "/prompts/init/settings.json";
    private static final String AGENTS_MD_RESOURCE = "/prompts/init/AGENTS.md";
    private static final String AGENTS_MD_DEST = System.getProperty("user.home") + "/.boris/AGENTS.md";

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    public void ensureExists(String path) throws IOException {
        Path settingsFile = Paths.get(path);
        if (Files.exists(settingsFile)) {
            return;
        }
        createDefault(settingsFile);
    }

    public void ensureSettings() throws IOException {
        ensureExists(DEFAULT_SETTINGS_PATH);
    }

    private void createDefault(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        try (var in = getClass().getResourceAsStream(SETTINGS_JSON_RESOURCE)) {
            if (in != null) {
                Files.copy(in, path, StandardCopyOption.REPLACE_EXISTING);
                return;
            }
        }

        // Fallback en caso de que no exista el recurso en el classpath
        Settings defaultSettings = new Settings();
        ModelConfig defaultModel = new ModelConfig("http://localhost:8080", "granite4.2:8b");
        defaultModel.setOptions(Map.of("think", "high"));
        defaultSettings.setModel(defaultModel);
        defaultSettings.setEnv(Map.of("OLLAMA_API_KEY", "ollama"));
        defaultSettings.setMaxHistorySize(20);
        defaultSettings.setEnableHistory(true);
        defaultSettings.setEnforceSequentialExecution(true);
        defaultSettings.setTemperature(0.7);
        defaultSettings.setContextWindow(10000);
        defaultSettings.setThinkingEnabled(true);
        defaultSettings.setThinkingMode("think");

        String defaultJson = MAPPER.writeValueAsString(defaultSettings);
        Files.writeString(path, defaultJson, StandardCharsets.UTF_8);
    }

    public Settings loadSettings(String path) throws IOException {
        Path settingsFile = Paths.get(path);
        if (!Files.exists(settingsFile)) {
            return null;
        }
        String json = Files.readString(settingsFile, StandardCharsets.UTF_8);
        try {
            return MAPPER.readValue(json, Settings.class);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new com.boris.exceptions.BorisException(
                    "Invalid JSON in settings file: " + path
                            + ". Check for trailing commas or missing quotes.", e);
        }
    }

    public String load(String path) throws IOException {
        Path settingsFile = Paths.get(path);
        if (!Files.exists(settingsFile)) {
            return null;
        }
        return Files.readString(settingsFile, StandardCharsets.UTF_8);
    }

    public void ensureAgentsMd() throws IOException {
        Path agentsFile = Paths.get(AGENTS_MD_DEST);
        if (Files.exists(agentsFile)) {
            return;
        }
        Path parent = agentsFile.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }
        try (var in = getClass().getResourceAsStream(AGENTS_MD_RESOURCE)) {
            if (in == null) {
                throw new IOException("Template AGENTS.md not found on classpath");
            }
            Files.copy(in, agentsFile);
        }
    }
}
