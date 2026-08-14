package com.boris.llm;

import java.io.IOException;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
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

        String baseUrl = settings.getModel().getBaseUrl();
        String modelName = settings.getModel().getName();
        Map<String, String> envMap = settings.getEnv();
        String apiKey = (String) envMap.getOrDefault("OLLAMA_API_KEY", "ollama");

        OpenAiApi openAiApi = new OpenAiApi.Builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        org.springframework.ai.openai.OpenAiChatModel chatModel = org.springframework.ai.openai.OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model(modelName).build())
                .build();

        this.chatClient = ChatClient.create(chatModel);
    }

}
