package com.boris.llm.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Representa una solicitud de llamada a función emitida por el modelo LLM.
 *
 * <p><b>Referencia histórica / Equivalencia:</b><br>
 * Reemplaza a {@code org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ToolCall} de <b>Spring AI 1.0.0-M6</b>.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolCall(
        String id,
        String type,
        ToolCallFunction function
) {
    public ToolCall {
        if (type == null) {
            type = "function";
        }
    }
}
