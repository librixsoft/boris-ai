package com.boris.llm.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.boris.exceptions.ChatException;
import com.boris.exceptions.ChatException.ErrorType;
import com.boris.tooling.engine.ToolRegistry;

/**
 * Cliente HTTP nativo para Ollama / OpenAI compatible sin dependencias de Spring AI.
 * Implementa comunicación síncrona, streaming Server-Sent Events (SSE) y el bucle de ejecución de tools.
 *
 * <p><b>Referencia histórica / Equivalencia:</b><br>
 * Reemplaza y consolida las siguientes capas de <b>Spring AI 1.0.0-M6</b>:
 * <ul>
 *   <li>{@code org.springframework.ai.chat.client.ChatClient}: interfaz fluida y bucle de agente.</li>
 *   <li>{@code org.springframework.ai.openai.OpenAiChatModel}: orquestación de llamadas y tool execution loop.</li>
 *   <li>{@code org.springframework.ai.openai.api.OpenAiApi}: comunicación HTTP (/v1/chat/completions) y streaming SSE.</li>
 * </ul>
 * <b>Mejoras sobre la implementación anterior:</b>
 * <ul>
 *   <li>Soporte nativo de parámetros de razonamiento Ollama ({@code think}, {@code reasoning_effort}) sin necesidad
 *       de interceptar bytes HTTP (reemplaza a {@code ThinkClientHttpInterceptor}).</li>
 *   <li>Extracción directa de tokens de pensamiento ({@code reasoning_content}) tanto en modo síncrono como en SSE.</li>
 *   <li>Sin sobrecarga de Spring Boot starters, WebFlux o Project Reactor (usa {@code java.net.http.HttpClient} de Java 21).</li>
 * </ul>
 * </p>
 */
public class BorisLlmClient {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .setSerializationInclusion(JsonInclude.Include.NON_NULL);

    private final String baseUrl;
    private final String apiKey;
    private final HttpClient httpClient;
    private final Duration timeout;

    public BorisLlmClient(String baseUrl) {
        this(baseUrl, "ollama", Duration.ofMinutes(5));
    }

    public BorisLlmClient(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, Duration.ofMinutes(5));
    }

    public BorisLlmClient(String baseUrl, String apiKey, Duration timeout) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = (apiKey != null && !apiKey.isBlank()) ? apiKey : "ollama";
        this.timeout = timeout != null ? timeout : Duration.ofMinutes(5);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    /**
     * Envía una petición sincrónica al endpoint /v1/chat/completions.
     */
    public ChatResponse chat(ChatRequest request) throws IOException, InterruptedException {
        byte[] requestBody = MAPPER.writeValueAsBytes(request);
        HttpRequest httpRequest = buildHttpRequest("/v1/chat/completions", requestBody);

        HttpResponse<String> response = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() >= 400) {
            throw new ChatException("Error en respuesta LLM (HTTP " + response.statusCode() + "): " + response.body(),
                    ErrorType.UNKNOWN);
        }

        return MAPPER.readValue(response.body(), ChatResponse.class);
    }

    /**
     * Envía una petición con streaming (SSE) y consume cada chunk conforme llega de la red.
     */
    public void chatStream(
            ChatRequest request,
            Consumer<ChatChunk> onChunk,
            Runnable onComplete,
            Consumer<Throwable> onError,
            Supplier<Boolean> isAborted) {

        ChatRequest streamRequest = request.stream() ? request :
                new ChatRequest(
                        request.model(),
                        request.messages(),
                        request.temperature(),
                        request.maxTokens(),
                        request.tools(),
                        true, // Forzar stream=true
                        request.parallelToolCalls(),
                        request.reasoningEffort(),
                        request.think(),
                        request.options()
                );

        try {
            byte[] requestBody = MAPPER.writeValueAsBytes(streamRequest);
            HttpRequest httpRequest = buildHttpRequest("/v1/chat/completions", requestBody);

            HttpResponse<java.util.stream.Stream<String>> response =
                    httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() >= 400) {
                throw new ChatException("Error HTTP en streaming (" + response.statusCode() + ")", ErrorType.STREAMING_ERROR);
            }

            try (java.util.stream.Stream<String> lines = response.body()) {
                for (java.util.Iterator<String> it = lines.iterator(); it.hasNext(); ) {
                    if (isAborted != null && Boolean.TRUE.equals(isAborted.get())) {
                        break;
                    }
                    String line = it.next();
                    if (line == null || line.isBlank()) {
                        continue;
                    }

                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) {
                            break;
                        }

                        try {
                            ChatChunk chunk = MAPPER.readValue(data, ChatChunk.class);
                            if (onChunk != null) {
                                onChunk.accept(chunk);
                            }
                        } catch (Exception e) {
                            // Ignorar chunks mal formateados
                        }
                    }
                }
            }

            if (onComplete != null) {
                onComplete.run();
            }
        } catch (Throwable t) {
            if (onError != null) {
                onError.accept(t);
            } else {
                throw new ChatException("Error en streaming LLM: " + t.getMessage(), t, ErrorType.STREAMING_ERROR);
            }
        }
    }

    /**
     * Bucle del agente: gestiona peticiones síncronas con llamadas a tools automáticas.
     */
    public ChatTurn executeChatWithTools(
            List<ChatMessage> conversation,
            String model,
            String systemPrompt,
            ToolRegistry toolRegistry,
            String thinkMode,
            Double temperature,
            Supplier<Boolean> isAborted) throws Exception {

        List<ChatMessage> fullMessages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            fullMessages.add(ChatMessage.system(systemPrompt));
        }
        if (conversation != null) {
            fullMessages.addAll(conversation);
        }

        List<Map<String, Object>> toolsSchema = toolRegistry != null ? toolRegistry.getOpenAiToolsSchema() : null;
        if (toolsSchema != null && toolsSchema.isEmpty()) {
            toolsSchema = null;
        }

        StringBuilder totalThinking = new StringBuilder();
        int maxTurns = 15;
        int currentTurn = 0;

        while (currentTurn < maxTurns) {
            if (isAborted != null && Boolean.TRUE.equals(isAborted.get())) {
                return new ChatTurn("", null);
            }
            currentTurn++;

            ChatRequest.Builder reqBuilder = ChatRequest.builder()
                    .model(model)
                    .messages(fullMessages)
                    .temperature(temperature)
                    .tools(toolsSchema)
                    .parallelToolCalls(false);

            if (thinkMode != null && !thinkMode.isBlank()) {
                reqBuilder.reasoningEffort(thinkMode);
                reqBuilder.think(thinkMode);
            } else {
                reqBuilder.reasoningEffort("none");
                reqBuilder.think(false);
            }

            ChatResponse response = chat(reqBuilder.build());
            ChatMessage assistantMsg = response.getFirstMessage();

            if (assistantMsg == null) {
                break;
            }

            // Capturar thinking / reasoning si el modelo lo envió
            if (assistantMsg.reasoningContent() != null && !assistantMsg.reasoningContent().isBlank()) {
                totalThinking.append(assistantMsg.reasoningContent());
            }

            List<ToolCall> toolCalls = assistantMsg.toolCalls();
            if (toolCalls == null || toolCalls.isEmpty() || toolRegistry == null) {
                // El modelo respondió con texto final
                String finalContent = assistantMsg.content() != null ? assistantMsg.content() : "";
                String thinkingResult = totalThinking.length() > 0 ? totalThinking.toString() : null;
                return new ChatTurn(finalContent, thinkingResult);
            }

            // El modelo solicitó invocar herramientas
            fullMessages.add(assistantMsg);

            for (ToolCall call : toolCalls) {
                if (isAborted != null && Boolean.TRUE.equals(isAborted.get())) {
                    return new ChatTurn("", null);
                }

                String toolName = call.function() != null ? call.function().name() : "";
                String toolArgs = call.function() != null ? call.function().arguments() : "{}";
                String toolResult;

                try {
                    toolResult = toolRegistry.execute(toolName, toolArgs);
                } catch (Exception e) {
                    toolResult = "Error ejecutando " + toolName + ": " + e.getMessage();
                }

                fullMessages.add(ChatMessage.tool(call.id(), toolResult));
            }
        }

        return new ChatTurn("", totalThinking.length() > 0 ? totalThinking.toString() : null);
    }

    public record ChatTurn(String response, String thinking) {}

    private HttpRequest buildHttpRequest(String path, byte[] body) {
        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + path))
                .header("Content-Type", "application/json")
                .timeout(timeout);

        if (apiKey != null && !apiKey.isBlank()) {
            builder.header("Authorization", "Bearer " + apiKey);
        }

        return builder.POST(HttpRequest.BodyPublishers.ofByteArray(body)).build();
    }

    public static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalArgumentException("baseUrl es requerido");
        }
        String normalized = baseUrl.trim();
        while (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized;
    }
}
