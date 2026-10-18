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

    private static final java.util.regex.Pattern THINK_BLOCK_PATTERN =
            java.util.regex.Pattern.compile("(?is)<(think|thinking|thought|reasoning)>(.*?)</\\1>");
    private static final java.util.regex.Pattern ORPHAN_THINK_TAGS =
            java.util.regex.Pattern.compile("(?i)</?(?:think|thinking|thought|reasoning)>");
    private static final java.util.regex.Pattern FALLBACK_MAVEN_BLOCK =
            java.util.regex.Pattern.compile("```(?:bash:compile|bash|sh|cmd)\\s*\\n\\s*(mvn\\s+[^`\\n]+)", java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern FALLBACK_TOOL_JSON_BLOCK =
            java.util.regex.Pattern.compile("```(?:tool:)?([a-zA-Z0-9_-]+)\\s*\\n\\s*(\\{[^`]+\\})\\s*```", java.util.regex.Pattern.DOTALL);

    /**
     * Bucle del agente: gestiona peticiones con llamadas a tools automáticas.
     */
    public ChatTurn executeChatWithTools(
            List<ChatMessage> conversation,
            String model,
            String systemPrompt,
            ToolRegistry toolRegistry,
            String thinkMode,
            Double temperature,
            Supplier<Boolean> isAborted) throws Exception {
        return executeChatWithTools(conversation, model, systemPrompt, toolRegistry, thinkMode, temperature, null, isAborted);
    }

    /**
     * Bucle del agente con soporte de streaming incremental de texto.
     */
    public ChatTurn executeChatWithTools(
            List<ChatMessage> conversation,
            String model,
            String systemPrompt,
            ToolRegistry toolRegistry,
            String thinkMode,
            Double temperature,
            Consumer<String> onChunk,
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

            // Capturar thinking / reasoning si el modelo lo envió en campo nativo
            if (assistantMsg.reasoningContent() != null && !assistantMsg.reasoningContent().isBlank()) {
                totalThinking.append(assistantMsg.reasoningContent()).append("\n");
            }

            // Capturar y limpiar thinking embebido en content (<think>...</think>)
            String rawContent = assistantMsg.content() != null ? assistantMsg.content() : "";
            var matcher = THINK_BLOCK_PATTERN.matcher(rawContent);
            while (matcher.find()) {
                totalThinking.append(matcher.group(2).trim()).append("\n");
            }
            String cleanContent = THINK_BLOCK_PATTERN.matcher(rawContent).replaceAll("").trim();
            cleanContent = ORPHAN_THINK_TAGS.matcher(cleanContent).replaceAll("").trim();

            List<ToolCall> toolCalls = assistantMsg.toolCalls();

            // Fallback de tool calling: Si el modelo no emitió tool_calls nativos pero simuló bloques bash o tool en texto
            if ((toolCalls == null || toolCalls.isEmpty()) && toolRegistry != null && !cleanContent.isBlank()) {
                toolCalls = extractFallbackToolCalls(cleanContent, toolRegistry);
            }

            if (toolCalls == null || toolCalls.isEmpty() || toolRegistry == null) {
                // El modelo respondió con texto final (sin llamadas a herramientas)
                if (onChunk != null && !cleanContent.isEmpty()) {
                    emitText(cleanContent, onChunk);
                }
                String thinkingResult = totalThinking.length() > 0 ? totalThinking.toString().trim() : null;
                return new ChatTurn(cleanContent, thinkingResult);
            }

            // El modelo solicitó invocar herramientas
            if (onChunk != null && !cleanContent.isEmpty()) {
                // Emitir texto previo a la invocación de la herramienta (ej. "Voy a compilar...")
                String displayContent = cleanContent;
                if (FALLBACK_MAVEN_BLOCK.matcher(displayContent).find()) {
                    displayContent = FALLBACK_MAVEN_BLOCK.matcher(displayContent).replaceAll("").trim();
                }
                if (!displayContent.isEmpty()) {
                    emitText(displayContent + "\n", onChunk);
                }
            }

            fullMessages.add(ChatMessage.assistant(cleanContent, toolCalls, assistantMsg.reasoningContent()));

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

        return new ChatTurn("", totalThinking.length() > 0 ? totalThinking.toString().trim() : null);
    }

    private List<ToolCall> extractFallbackToolCalls(String content, ToolRegistry registry) {
        List<ToolCall> list = new ArrayList<>();
        var mavenMatcher = FALLBACK_MAVEN_BLOCK.matcher(content);
        if (mavenMatcher.find() && registry.hasTool("maven_build")) {
            String mvnCmd = mavenMatcher.group(1).trim();
            String goal = mvnCmd.substring(3).trim();
            if (goal.isBlank()) goal = "compile";
            String jsonArgs = "{\"goal\":\"" + goal + "\"}";
            list.add(new ToolCall("call_fallback_maven", "function", new ToolCallFunction("maven_build", jsonArgs)));
            return list;
        }

        var jsonMatcher = FALLBACK_TOOL_JSON_BLOCK.matcher(content);
        if (jsonMatcher.find()) {
            String toolName = jsonMatcher.group(1).trim();
            String jsonArgs = jsonMatcher.group(2).trim();
            if (registry.hasTool(toolName)) {
                list.add(new ToolCall("call_fallback_" + toolName, "function", new ToolCallFunction(toolName, jsonArgs)));
                return list;
            }
        }

        return list;
    }

    private void emitText(String text, Consumer<String> onChunk) {
        if (text == null || text.isEmpty() || onChunk == null) return;
        String[] lines = text.split("(?<=\\n)");
        for (String line : lines) {
            onChunk.accept(line);
        }
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
