package com.boris.llm.client;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Mensaje de conversación para el protocolo de chat OpenAI / Ollama.
 *
 * <p><b>Referencia histórica / Equivalencia:</b><br>
 * Reemplaza a las clases de mensajes de <b>Spring AI 1.0.0-M6</b>:
 * <ul>
 *   <li>{@code org.springframework.ai.chat.messages.SystemMessage}</li>
 *   <li>{@code org.springframework.ai.chat.messages.UserMessage}</li>
 *   <li>{@code org.springframework.ai.chat.messages.AssistantMessage}</li>
 *   <li>{@code org.springframework.ai.chat.messages.ToolResponseMessage}</li>
 * </ul>
 * Soporta de forma unificada roles (system, user, assistant, tool), tool_calls y reasoning_content.
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatMessage(
        String role,
        String content,
        @JsonProperty("tool_call_id") String toolCallId,
        @JsonProperty("tool_calls") List<ToolCall> toolCalls,
        @JsonProperty("reasoning_content")
        @JsonAlias({"reasoning", "thinking", "reasoning_text", "thought"})
        String reasoningContent
) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null, null);
    }

    public static ChatMessage assistant(String content, List<ToolCall> toolCalls, String reasoningContent) {
        return new ChatMessage("assistant", content, null, toolCalls, reasoningContent);
    }

    public static ChatMessage tool(String toolCallId, String content) {
        return new ChatMessage("tool", content, toolCallId, null, null);
    }
}
