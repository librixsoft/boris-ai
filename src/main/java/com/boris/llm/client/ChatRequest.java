package com.boris.llm.client;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Payload de petición para el endpoint /v1/chat/completions de OpenAI / Ollama.
 *
 * <p><b>Referencia histórica / Equivalencia:</b><br>
 * Reemplaza y combina los objetos de <b>Spring AI 1.0.0-M6</b>:
 * <ul>
 *   <li>{@code org.springframework.ai.chat.prompt.Prompt}</li>
 *   <li>{@code org.springframework.ai.openai.OpenAiChatOptions}</li>
 *   <li>{@code org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest}</li>
 * </ul>
 * Incluye soporte directo para control de think en Ollama ({@code think}, {@code reasoning_effort}).
 * </p>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChatRequest(
        String model,
        List<ChatMessage> messages,
        Double temperature,
        @JsonProperty("max_tokens") Integer maxTokens,
        List<Map<String, Object>> tools,
        Boolean stream,
        @JsonProperty("parallel_tool_calls") Boolean parallelToolCalls,
        @JsonProperty("reasoning_effort") String reasoningEffort,
        Object think,
        Map<String, Object> options
) {

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String model;
        private List<ChatMessage> messages;
        private Double temperature;
        private Integer maxTokens = 32000;
        private List<Map<String, Object>> tools;
        private Boolean stream = false;
        private Boolean parallelToolCalls = false;
        private String reasoningEffort;
        private Object think;
        private Map<String, Object> options;

        public Builder model(String model) {
            this.model = model;
            return this;
        }

        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Builder temperature(Double temperature) {
            this.temperature = temperature;
            return this;
        }

        public Builder maxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
            return this;
        }

        public Builder tools(List<Map<String, Object>> tools) {
            this.tools = tools;
            return this;
        }

        public Builder stream(Boolean stream) {
            this.stream = stream;
            return this;
        }

        public Builder parallelToolCalls(Boolean parallelToolCalls) {
            this.parallelToolCalls = parallelToolCalls;
            return this;
        }

        public Builder reasoningEffort(String reasoningEffort) {
            this.reasoningEffort = reasoningEffort;
            return this;
        }

        public Builder think(Object think) {
            this.think = think;
            return this;
        }

        public Builder options(Map<String, Object> options) {
            this.options = options;
            return this;
        }

        public ChatRequest build() {
            return new ChatRequest(
                    model,
                    messages,
                    temperature,
                    maxTokens,
                    tools,
                    stream,
                    parallelToolCalls,
                    reasoningEffort,
                    think,
                    options
            );
        }
    }
}
