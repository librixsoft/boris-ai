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
import com.boris.task.TaskExecutionState;
import com.boris.task.TaskStateNotifier;
import com.boris.tooling.ToolProgressNotifier;
import com.boris.tooling.WriteArgsPreviewStreamer;
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
    private static final java.util.regex.Pattern STREAMING_EDIT_PATTERN =
            java.util.regex.Pattern.compile("```(?:edit:|\\w+:[^\\n]+)");

    private boolean isModificationResult(String toolName, String toolResult) {
        if (toolResult == null || toolResult.isBlank()) {
            return false;
        }
        if (toolResult.contains("\"diff\"")) {
            return true;
        }
        if (toolResult.contains("\"success\":true") || toolResult.contains("\"deleted\":true")) {
            String lowerName = toolName != null ? toolName.toLowerCase() : "";
            return lowerName.contains("edit") || lowerName.contains("write") || lowerName.contains("delete")
                    || lowerName.contains("create") || lowerName.contains("revert");
        }
        return false;
    }

    public TaskExecutionState getTaskState() {
        return TaskStateNotifier.getCurrentState();
    }

    private static final class ToolCallAccumulator {
        private String id;
        private String name;
        private final StringBuilder arguments = new StringBuilder();

        private ToolCall toToolCall(int index) {
            String resolvedId = (id != null && !id.isBlank()) ? id : "call_stream_" + index;
            String resolvedName = (name != null && !name.isBlank()) ? name : "unknown_tool_" + index;
            String resolvedArgs = arguments.length() > 0 ? arguments.toString() : "{}";
            return new ToolCall(resolvedId, "function", new ToolCallFunction(resolvedName, resolvedArgs));
        }
    }

    private record StreamedAssistantMessage(
            ChatMessage message,
            List<ToolCall> nativeToolCalls,
            List<Integer> nativeCallIndexes,
            WriteArgsPreviewStreamer previewStreamer
    ) {}

    private StreamedAssistantMessage streamAssistantMessage(
            ChatRequest request,
            Consumer<String> onChunk,
            Supplier<Boolean> isAborted) {

        StringBuilder content = new StringBuilder();
        StringBuilder reasoning = new StringBuilder();
        var accumulators = new java.util.LinkedHashMap<Integer, ToolCallAccumulator>();
        WriteArgsPreviewStreamer previewStreamer = new WriteArgsPreviewStreamer();

        chatStream(request, chunk -> {
            if (chunk == null || chunk.choices() == null || chunk.choices().isEmpty()) {
                return;
            }
            ChatChunk.ChunkChoice choice = chunk.choices().getFirst();
            if (choice.delta() == null) {
                return;
            }
            ChatChunk.Delta delta = choice.delta();

            if (delta.reasoningContent() != null && !delta.reasoningContent().isEmpty()) {
                reasoning.append(delta.reasoningContent());
            }

            if (delta.content() != null && !delta.content().isEmpty()) {
                content.append(delta.content());
                if (onChunk != null) {
                    onChunk.accept(delta.content());
                }
            }

            if (delta.toolCalls() != null) {
                for (ChatChunk.ToolCallChunk tc : delta.toolCalls()) {
                    ToolCallAccumulator acc = accumulators.computeIfAbsent(tc.index(), k -> new ToolCallAccumulator());
                    if (tc.id() != null && !tc.id().isEmpty()) {
                        acc.id = tc.id();
                    }
                    if (tc.function() != null && tc.function().name() != null && !tc.function().name().isEmpty()) {
                        acc.name = tc.function().name();
                    }
                    if (tc.function() != null && tc.function().arguments() != null && !tc.function().arguments().isEmpty()) {
                        String argsDelta = tc.function().arguments();
                        acc.arguments.append(argsDelta);
                        previewStreamer.onArgsDelta(tc.index(), acc.name, argsDelta);
                    }
                }
            }
        }, null, null, isAborted);

        List<ToolCall> nativeToolCalls = new ArrayList<>();
        List<Integer> nativeCallIndexes = new ArrayList<>();
        for (var entry : accumulators.entrySet()) {
            ToolCallAccumulator acc = entry.getValue();
            if (acc.name == null || acc.name.isBlank()) {
                continue;
            }
            nativeCallIndexes.add(entry.getKey());
            nativeToolCalls.add(acc.toToolCall(entry.getKey()));
        }

        ChatMessage streamedMessage = ChatMessage.assistant(
                content.toString(),
                nativeToolCalls.isEmpty() ? null : nativeToolCalls,
                reasoning.length() > 0 ? reasoning.toString() : null);

        if (nativeToolCalls.isEmpty()) {
            return new StreamedAssistantMessage(streamedMessage, null, null, null);
        }
        return new StreamedAssistantMessage(streamedMessage, nativeToolCalls, nativeCallIndexes, previewStreamer);
    }

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
        int iterationCount = 0;
        final int MAX_TOOL_ITERATIONS = 50;

        boolean taskPending = false;
        boolean hasModified = false;
        TaskStateNotifier.setState(TaskExecutionState.IDLE);

        while (true) {
            if (isAborted != null && Boolean.TRUE.equals(isAborted.get())) {
                TaskStateNotifier.setState(TaskExecutionState.IDLE);
                return new ChatTurn("", null);
            }

            if (iterationCount >= MAX_TOOL_ITERATIONS) {
                TaskStateNotifier.setState(TaskExecutionState.INCOMPLETE);
                String capWarning = "\n\n⚠️ Límite de " + MAX_TOOL_ITERATIONS + " iteraciones de herramientas alcanzado. Deteniendo el agente para evitar un bucle infinito.";
                if (onChunk != null) {
                    emitText(capWarning, onChunk);
                }
                String thinkingResult = totalThinking.length() > 0 ? totalThinking.toString().trim() : null;
                return new ChatTurn(capWarning.trim(), thinkingResult);
            }
            iterationCount++;

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

            StreamedAssistantMessage streamedMessage = null;
            ChatMessage assistantMsg;
            if (onChunk != null) {
                streamedMessage = streamAssistantMessage(reqBuilder.build(), onChunk, isAborted);
                assistantMsg = streamedMessage.message();
            } else {
                ChatResponse response = chat(reqBuilder.build());
                assistantMsg = response.getFirstMessage();
            }

            boolean emptyAssistantTurn = assistantMsg == null
                    || (streamedMessage != null
                            && (assistantMsg.content() == null || assistantMsg.content().isBlank())
                            && (assistantMsg.toolCalls() == null || assistantMsg.toolCalls().isEmpty())
                            && (assistantMsg.reasoningContent() == null || assistantMsg.reasoningContent().isBlank()));

            if (emptyAssistantTurn) {
                // TODO: FIX PENDIENTE DE VALIDAR - Deteccion de parada del agente con respuesta vacia del modelo.
                if (taskPending) {
                    taskPending = false;
                    TaskStateNotifier.setState(TaskExecutionState.INCOMPLETE);
                    String warningMsg = "\n\n⚠️ Tarea no completada: el agente se detuvo con una respuesta vacia del modelo.";
                    if (onChunk != null) {
                        emitText(warningMsg, onChunk);
                    }
                    String thinkingResult = totalThinking.length() > 0 ? totalThinking.toString().trim() : null;
                    return new ChatTurn(warningMsg.trim(), thinkingResult);
                }
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

            if (STREAMING_EDIT_PATTERN.matcher(cleanContent).find()) {
                hasModified = true;
            }

            List<ToolCall> toolCalls = assistantMsg.toolCalls();

            if (toolCalls == null || toolCalls.isEmpty() || toolRegistry == null) {
                // Caso A: Chat normal (nunca se activó ninguna herramienta) o sin registry
                if (!taskPending || toolRegistry == null) {
                    if (streamedMessage == null && onChunk != null && !cleanContent.isEmpty()) {
                        emitText(cleanContent, onChunk);
                    }
                    String thinkingResult = totalThinking.length() > 0 ? totalThinking.toString().trim() : null;
                    return new ChatTurn(cleanContent, thinkingResult);
                }

                // Caso B: Se ejecutaron tools y el modelo decide finalizar su turno. Se entrega la respuesta tal cual,
                // sin reintentos ni exigencia de modificaciones.
                taskPending = false;
                TaskStateNotifier.setState(hasModified ? TaskExecutionState.COMPLETED : TaskExecutionState.IDLE);
                if (streamedMessage == null && onChunk != null && !cleanContent.isEmpty()) {
                    emitText(cleanContent, onChunk);
                }
                String thinkingResult = totalThinking.length() > 0 ? totalThinking.toString().trim() : null;
                return new ChatTurn(cleanContent, thinkingResult);
            }

            // El modelo solicitó invocar herramientas
            if (streamedMessage == null && onChunk != null && !cleanContent.isEmpty()) {
                emitText(cleanContent + "\n", onChunk);
            }

            fullMessages.add(ChatMessage.assistant(cleanContent, toolCalls, assistantMsg.reasoningContent()));

            List<Integer> nativeCallIndexes =
                    streamedMessage != null && toolCalls == streamedMessage.nativeToolCalls()
                            ? streamedMessage.nativeCallIndexes()
                            : null;

            for (int callIndex = 0; callIndex < toolCalls.size(); callIndex++) {
                ToolCall call = toolCalls.get(callIndex);
                if (isAborted != null && Boolean.TRUE.equals(isAborted.get())) {
                    TaskStateNotifier.setState(TaskExecutionState.IDLE);
                    return new ChatTurn("", null);
                }

                String toolName = call.function() != null ? call.function().name() : "";
                String toolArgs = call.function() != null ? call.function().arguments() : "{}";
                String toolResult;

                if (nativeCallIndexes != null) {
                    streamedMessage.previewStreamer().prepareExecution(nativeCallIndexes.get(callIndex));
                }
                try {
                    toolResult = toolRegistry.execute(toolName, toolArgs);
                } catch (Exception e) {
                    toolResult = "Error ejecutando " + toolName + ": " + e.getMessage();
                } finally {
                    WriteArgsPreviewStreamer.clearLivePreview();
                }

                // En cuanto se invoca CUALQUIER herramienta, se activa taskPending
                taskPending = true;
                TaskStateNotifier.setState(TaskExecutionState.TASK_IN_PROGRESS);

                // Verificamos si esta herramienta aplicó una modificación real
                if (isModificationResult(toolName, toolResult)) {
                    hasModified = true;
                }

                fullMessages.add(ChatMessage.tool(call.id(), toolResult));
            }
        }

        TaskStateNotifier.setState(hasModified ? TaskExecutionState.COMPLETED : TaskExecutionState.IDLE);
        return new ChatTurn("", totalThinking.length() > 0 ? totalThinking.toString().trim() : null);
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
