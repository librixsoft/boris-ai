package com.boris.llm.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Chunk de streaming recibido en eventos Server-Sent Events (SSE) desde /v1/chat/completions.
 *
 * <p><b>Referencia histórica / Equivalencia:</b><br>
 * Reemplaza a los objetos de streaming de <b>Spring AI 1.0.0-M6</b>:
 * <ul>
 *   <li>{@code org.springframework.ai.chat.model.ChatResponse} (emitido por {@code Flux<ChatResponse>})</li>
 *   <li>{@code org.springframework.ai.openai.api.OpenAiApi.ChatCompletionChunk}</li>
 * </ul>
 * Provee acceso directo a {@code content}, {@code reasoning_content} y {@code finish_reason}.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChatChunk(
        String id,
        List<ChunkChoice> choices
) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChunkChoice(
            int index,
            Delta delta,
            @JsonProperty("finish_reason") String finishReason
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Delta(
            String role,
            String content,
            @JsonProperty("reasoning_content") String reasoningContent,
            @JsonProperty("tool_calls") List<ToolCallChunk> toolCalls
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ToolCallChunk(
            int index,
            String id,
            String type,
            ToolCallFunction function
    ) {}

    public String getContent() {
        if (choices != null && !choices.isEmpty() && choices.getFirst() != null) {
            Delta d = choices.getFirst().delta();
            return d != null ? d.content() : null;
        }
        return null;
    }

    public String getReasoningContent() {
        if (choices != null && !choices.isEmpty() && choices.getFirst() != null) {
            Delta d = choices.getFirst().delta();
            return d != null ? d.reasoningContent() : null;
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
