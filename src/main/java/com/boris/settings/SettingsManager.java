package com.boris.settings;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

public class SettingsManager {

    private static final String DEFAULT_SETTINGS_PATH = System.getProperty("user.home") + "/.boris/settings.json";

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

        String defaultJson = """
                {
                  "model": {
                    "baseUrl": "http://localhost:11434/v1",
                    "name": "qwen3.6-35b-64k"
                  },
                  "env": {
                    "OLLAMA_API_KEY": "ollama"
                  }
                }\
                """;

        Files.writeString(path, defaultJson, StandardCharsets.UTF_8);
    }

    public String load(String path) throws IOException {
        Path settingsFile = Paths.get(path);
        if (!Files.exists(settingsFile)) {
            return null;
        }
        return Files.readString(settingsFile, StandardCharsets.UTF_8);
    }
}
