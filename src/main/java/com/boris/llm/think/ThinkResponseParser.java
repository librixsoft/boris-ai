package com.boris.llm.think;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Extrae el trace de razonamiento del JSON crudo de Ollama (choices[].message
 * o choices[].delta: thinking/reasoning_content/reasoning/reasoning_text/
 * thought). Spring AI ignora esos campos desconocidos, por eso se leen aqui.
 */
public final class ThinkResponseParser {

    private static final String[] THINKING_FIELDS = {
            "thinking", "reasoning_content", "reasoning", "reasoning_text", "thought"
    };

    private ThinkResponseParser() {
    }

    public static String extractThinking(JsonNode root) {
        if (root == null || root.isNull()) {
            return null;
        }
        JsonNode choices = root.get("choices");
        if (choices != null && choices.isArray() && !choices.isEmpty()) {
            for (JsonNode choice : choices) {
                String found = extractFromChoice(choice);
                if (found != null && !found.isBlank()) {
                    return found;
                }
            }
        }
        JsonNode message = root.get("message");
        if (message != null && message.isObject()) {
            String found = extractFromMessage(message);
            if (found != null && !found.isBlank()) {
                return found;
            }
        }
        return null;
    }

    private static String extractFromChoice(JsonNode choice) {
        if (choice == null || !choice.isObject()) {
            return null;
        }
        JsonNode message = choice.get("message");
        String fromMessage = extractFromMessage(message);
        if (fromMessage != null && !fromMessage.isBlank()) {
            return fromMessage;
        }
        JsonNode delta = choice.get("delta");
        String fromDelta = extractFromMessage(delta);
        if (fromDelta != null && !fromDelta.isBlank()) {
            return fromDelta;
        }
        for (String field : THINKING_FIELDS) {
            JsonNode direct = choice.get(field);
            if (direct != null && direct.isTextual() && !direct.asText().isBlank()) {
                return direct.asText();
            }
        }
        return null;
    }

    private static String extractFromMessage(JsonNode message) {
        if (message == null || !message.isObject()) {
            return null;
        }
        for (String field : THINKING_FIELDS) {
            JsonNode node = message.get(field);
            if (node != null && node.isTextual() && !node.asText().isBlank()) {
                return node.asText();
            }
        }
        return null;
    }
}
