package com.boris.llm.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Respuesta sincrónica recibida del endpoint /v1/chat/completions.
 *
 * <p><b>Referencia histórica / Equivalencia:</b><br>
 * Reemplaza a las clases de respuesta de <b>Spring AI 1.0.0-M6</b>:
 * <ul>
 *   <li>{@code org.springframework.ai.chat.model.ChatResponse}</li>
 *   <li>{@code org.springframework.ai.chat.model.Generation}</li>
 *   <li>{@code org.springframework.ai.openai.api.OpenAiApi.ChatCompletion}</li>
 * </ul>
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatResponse(
        String id,
        List<Choice> choices
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Choice(
            int index,
            ChatMessage message,
            @JsonProperty("finish_reason") String finishReason
    ) {}

    public ChatMessage getFirstMessage() {
        if (choices != null && !choices.isEmpty() && choices.getFirst() != null) {
            return choices.getFirst().message();
        }
        return null;
    }

    public String getFinishReason() {
        if (choices != null && !choices.isEmpty() && choices.getFirst() != null) {
            return choices.getFirst().finishReason();
        }
        return null;
    }
}
