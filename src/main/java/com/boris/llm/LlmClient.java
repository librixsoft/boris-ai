package com.boris.llm;

import java.io.IOException;

import com.boris.llm.client.BorisLlmClient;
import com.boris.llm.think.OllamaThinkSpringAiFactory;
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
            this.borisLlmClient = OllamaThinkSpringAiFactory.createClient(settings);
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
