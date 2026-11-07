package com.boris.llm;

import java.io.IOException;
import java.util.Map;

import com.boris.llm.client.BorisLlmClient;
import com.boris.settings.Settings;
import com.boris.settings.SettingsManager;

public class LlmClient {

    private final BorisLlmClient borisLlmClient;

    public LlmClient(BorisLlmClient borisLlmClient) {
        this.borisLlmClient = borisLlmClient;
    }

    public LlmClient(String settingsPath) throws IOException {
        SettingsManager settingsMgr = new SettingsManager();
        Settings settings = settingsMgr.loadSettings(settingsPath);
        if (settings == null || settings.getModel() == null) {
            throw new IllegalStateException("Settings file not found or invalid: " + settingsPath);
        }
        try {
            String baseUrl = settings.getModel().getBaseUrl();
            if (baseUrl == null || baseUrl.isBlank()) {
                throw new IllegalStateException("Model baseUrl is required");
            }
            String normalized = baseUrl.trim();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            Map<String, String> envMap = settings.getEnv();
            String apiKey = envMap != null ? envMap.get("OLLAMA_API_KEY") : null;
            this.borisLlmClient = new BorisLlmClient(normalized, apiKey);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to build BorisLlmClient: " + e.getMessage(), e);
        }
    }

    public BorisLlmClient getClient() {
        return borisLlmClient;
    }
}
