package com.boris.llm;

import java.io.IOException;

import org.springframework.ai.chat.client.ChatClient;

import com.boris.llm.think.OllamaThinkSpringAiFactory;
import com.boris.settings.Settings;
import com.boris.settings.SettingsManager;

public class LlmClient {

    private final ChatClient chatClient;

    public LlmClient(ChatClient chatClient) {
        this.chatClient = chatClient;
    }

    public LlmClient(String settingsPath) throws IOException {
        SettingsManager settingsMgr = new SettingsManager();
        Settings settings = settingsMgr.loadSettings(settingsPath);
        if (settings == null || settings.getModel() == null) {
            throw new IllegalStateException("Settings file not found or invalid: " + settingsPath);
        }
        String thinkMode = OllamaThinkSpringAiFactory.resolveThinkMode(settings);
        try {
            this.chatClient = OllamaThinkSpringAiFactory.createChatClient(settings, thinkMode);
        } catch (IllegalStateException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to build Spring AI ChatClient: " + e.getMessage(), e);
        }
    }

    public ChatClient getChatClient() {
        return chatClient;
    }
}
