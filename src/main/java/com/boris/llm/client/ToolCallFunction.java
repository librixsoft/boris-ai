package com.boris.llm.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Detalles de la función invocada (nombre y argumentos en JSON string).
 *
 * <p><b>Referencia histórica / Equivalencia:</b><br>
 * Reemplaza a {@code org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage.ChatCompletionFunction} de <b>Spring AI 1.0.0-M6</b>.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ToolCallFunction(
        String name,
        String arguments
) {}
