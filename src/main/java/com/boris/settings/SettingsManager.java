package com.boris.settings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class SettingsManager {

    private static final String DEFAULT_SETTINGS_PATH = System.getProperty("user.home") + "/.boris/settings.json";

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

    private void createDefault(Path path) throws IOException {
        Path parent = path.getParent();
        if (parent != null && !Files.exists(parent)) {
            Files.createDirectories(parent);
        }

        // Crear configuración por defecto con los nuevos campos
        Settings defaultSettings = new Settings();
        defaultSettings.setModel(new ModelConfig("http://localhost:11434", "qwen3.6-35b-64k"));
        defaultSettings.setEnv(Map.of("OLLAMA_API_KEY", "ollama"));
        defaultSettings.setMaxHistorySize(20);
        defaultSettings.setEnableHistory(true);
        defaultSettings.setEnforceSequentialExecution(true);
        defaultSettings.setTemperature(0.7);
        defaultSettings.setContextWindow(10000);

        String defaultJson = MAPPER.writeValueAsString(defaultSettings);
        Files.writeString(path, defaultJson, StandardCharsets.UTF_8);
    }

    public Settings loadSettings(String path) throws IOException {
        Path settingsFile = Paths.get(path);
        if (!Files.exists(settingsFile)) {
            return null;
        }
        String json = Files.readString(settingsFile, StandardCharsets.UTF_8);
        return MAPPER.readValue(json, Settings.class);
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
