package com.boris.settings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

public class SettingsManager {

    private static final String DEFAULT_SETTINGS_PATH = System.getProperty("user.home") + "/.boris/settings.json";

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

        String defaultJson = MAPPER.writeValueAsString(Settings.defaultSettings());
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
}
