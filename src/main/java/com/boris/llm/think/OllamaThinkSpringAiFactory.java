package com.boris.llm.think;

import java.util.Map;

import com.boris.llm.client.BorisLlmClient;
import com.boris.settings.Settings;

/**
 * Fábrica para construir el BorisLlmClient configurado para Ollama / OpenAI.
 */
public final class OllamaThinkSpringAiFactory {

    private OllamaThinkSpringAiFactory() {
    }

    public static String resolveThinkMode(Settings settings) {
        if (settings == null) {
            return null;
        }
        if (settings.getOptions() != null) {
            Object thinkValue = settings.getOptions().get("think");
            if (thinkValue instanceof String thinkStr && !thinkStr.isBlank()) {
                return thinkStr;
            }
        }
        if (settings.getModel() != null && settings.getModel().getOptions() != null) {
            Object thinkValue = settings.getModel().getOptions().get("think");
            if (thinkValue instanceof String thinkStr && !thinkStr.isBlank()) {
                return thinkStr;
            }
        }
        if (settings.getThinkingMode() != null && !settings.getThinkingMode().isBlank()) {
            return settings.getThinkingMode();
        }
        return null;
    }

    public static BorisLlmClient createClient(Settings settings) {
        if (settings == null || settings.getModel() == null) {
            throw new IllegalStateException("Settings model is required");
        }
        String baseUrl = normalizeBaseUrl(settings.getModel().getBaseUrl());
        Map<String, String> envMap = settings.getEnv();
        String apiKey = "ollama";
        if (envMap != null) {
            apiKey = envMap.getOrDefault("OLLAMA_API_KEY", "ollama");
        }
        return new BorisLlmClient(baseUrl, apiKey);
    }

    public static String normalizeBaseUrl(String baseUrl) {
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
