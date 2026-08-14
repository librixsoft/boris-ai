package com.boris.llm;

import java.io.IOException;
import java.nio.file.Files;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import com.google.gson.JsonObject;
import com.boris.settings.SettingsManager;

public class LlmClient {

    private final ChatClient chatClient;

    public LlmClient(String settingsPath) throws IOException {
        SettingsManager settingsMgr = new SettingsManager();
        String json = settingsMgr.load(settingsPath);
        if (json == null) {
            throw new IllegalStateException("Settings file not found: " + settingsPath);
        }

        JsonObject root = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
        String baseUrl = root.getAsJsonObject("model").get("baseUrl").getAsString();
        String modelName = root.getAsJsonObject("model").get("name").getAsString();

        Map<String, Object> envMap = new LinkedHashMap<>();
        JsonObject envObj = root.get("env").getAsJsonObject();
        for (String key : envObj.keySet()) {
            envMap.put(key, envObj.get(key).getAsString());
        }

        String apiKey = (String) envMap.getOrDefault("OLLAMA_API_KEY", "ollama");

        OpenAiApi openAiApi = new OpenAiApi.Builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        ChatModel chatModel = new org.springframework.ai.openai.OpenAiChatModel(
                openAiApi,
                OpenAiChatOptions.builder().model(modelName).build());

        this.chatClient = ChatClient.create(chatModel);
    }

    public String send(String userMessage) {
        return chatClient.prompt(userMessage).call().content();
    }
}
