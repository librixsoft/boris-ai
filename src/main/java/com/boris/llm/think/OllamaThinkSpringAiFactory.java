package com.boris.llm.think;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.web.client.RestClient;

import com.boris.settings.Settings;
import com.boris.tooling.integration.ToolCallingConfig;

/**
 * Fabrica Spring AI para Ollama con control de think.
 *
 * Mapeo implementado: el parametro custom del settings.json (options.think:
 * "low"/"medium"/"high") se traduce al campo estandar de Spring AI
 * OpenAiChatOptions.reasoningEffort, que viaja como "reasoning_effort" al
 * endpoint /v1/chat/completions de Ollama. Ollama lo mapea a su Think interno:
 * "low"/"medium"/"high" prenden el think, "none" lo apaga. El parametro nativo
 * "think"/"options.think" NO funciona en ese endpoint (solo en /api/chat), por
 * eso sin think configurado se envia reasoning_effort="none": si se omite,
 * Ollama auto-activa el think. Sin llamadas HTTP directas ni fallbacks.
 */
public final class OllamaThinkSpringAiFactory {

    private OllamaThinkSpringAiFactory() {
    }

    public static String resolveThinkMode(Settings settings) {
        if (settings == null || settings.getOptions() == null) {
            return null;
        }
        Object thinkValue = settings.getOptions().get("think");
        if (thinkValue instanceof String thinkStr && !thinkStr.isBlank()) {
            return thinkStr;
        }
        return null;
    }

    public static OpenAiChatModel createChatModel(Settings settings, String thinkMode) {
        if (settings == null || settings.getModel() == null) {
            throw new IllegalStateException("Settings model is required");
        }
        String baseUrl = normalizeBaseUrl(settings.getModel().getBaseUrl());
        String modelName = settings.getModel().getName();
        Map<String, String> envMap = settings.getEnv();
        String apiKey = "ollama";
        if (envMap != null) {
            apiKey = envMap.getOrDefault("OLLAMA_API_KEY", "ollama");
        }
        String completionsPath = "/v1/chat/completions";
        String embeddingsPath = "/v1/embeddings";

        OpenAiApi openAiApi = new OpenAiApi.Builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .completionsPath(completionsPath)
                .embeddingsPath(embeddingsPath)
                .restClientBuilder(RestClient.builder().requestInterceptor(new ThinkClientHttpInterceptor()))
                .build();

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder().model(modelName);
        if (settings.getTemperature() != null) {
            optionsBuilder.temperature(settings.getTemperature());
        }
        if (thinkMode != null && !thinkMode.isBlank()) {
            optionsBuilder.reasoningEffort(thinkMode);
        } else {
            optionsBuilder.reasoningEffort("none");
        }

        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(optionsBuilder.build())
                .build();
    }

    public static ChatClient createChatClient(Settings settings, String thinkMode) throws Exception {
        String prompt = ToolCallingConfig.loadSystemPrompt(settings);
        OpenAiChatModel chatModel = createChatModel(settings, thinkMode);
        return ChatClient.builder(chatModel)
                .defaultSystem(prompt)
                .defaultTools(ToolCallingConfig.buildNativeToolCallbacks())
                .build();
    }

    static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("Model baseUrl is required");
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
