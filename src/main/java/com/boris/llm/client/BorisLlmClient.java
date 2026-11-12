package com.boris.llm.client;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.boris.exceptions.ChatException;
import com.boris.exceptions.ChatException.ErrorType;
import com.boris.exceptions.ToolLoopException;
import com.boris.exceptions.WebSearchFallbackException;
import com.boris.task.TaskExecutionState;
import com.boris.task.TaskStateNotifier;
import com.boris.tooling.ToolProgressNotifier;
import com.boris.tooling.ToolBlockProtocol;
import com.boris.tooling.WriteArgsPreviewStreamer;
import com.boris.tooling.engine.ToolRegistry;
import com.boris.llm.think.ThinkingStallDetector;
import com.boris.llm.think.ToolCallDeduplicator;
import com.boris.llm.think.ToolProgressGuard;

/**
 * Cliente HTTP nativo para LLM compatible sin dependencias de Spring AI.
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
 *   <li>Soporte nativo de parámetros de razonamiento ({@code think}, {@code reasoning_effort}) sin necesidad
 *       de interceptar bytes HTTP.</li>
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
        this(baseUrl, null, Duration.ofMinutes(5));
    }

    public BorisLlmClient(String baseUrl, String apiKey) {
        this(baseUrl, apiKey, Duration.ofMinutes(5));
    }

    public BorisLlmClient(String baseUrl, String apiKey, Duration timeout) {
        this.baseUrl = normalizeBaseUrl(baseUrl);
        this.apiKey = apiKey;
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
            java.util.regex.Pattern.compile("```\\w+:[^\\n]+");

    private static final int MAX_IDENTICAL_TOOL_EXECUTIONS_PER_TURN = 1;
    private static final int MAX_CONSECUTIVE_BLOCKED_COMMANDS = 3;

    private boolean isCommandBlocked(String toolResult) {
        if (toolResult == null || toolResult.isBlank()) {
            return false;
        }
        return toolResult.contains("Command blocked:")
                || toolResult.contains("duplicates the native tool");
    }

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
        StringBuilder reasoningLine = new StringBuilder();
        boolean[] reasoningStarted = {false};
        var accumulators = new java.util.LinkedHashMap<Integer, ToolCallAccumulator>();
        WriteArgsPreviewStreamer previewStreamer = new WriteArgsPreviewStreamer();

        // Compuera: se cierra al detectar el primer bloque ```tool:...``` completo.
        // Esto evita que el modelo emita decenas de bloques repetidos en un único stream.
        boolean[] gateClosed = {false};
        int[] emittedContentLength = {0};

        Supplier<Boolean> combinedAborted = () -> gateClosed[0]
                || (isAborted != null && Boolean.TRUE.equals(isAborted.get()));

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
                reasoningLine.append(delta.reasoningContent());
                reasoningStarted[0] = true;
                if (onChunk != null) {
                    int newlineIndex;
                    while ((newlineIndex = reasoningLine.indexOf("\n")) >= 0) {
                        String line = reasoningLine.substring(0, newlineIndex);
                        reasoningLine.delete(0, newlineIndex + 1);
                        if (!line.isBlank()) {
                            String sanitizedLine = line.replaceAll("(?i)</?(think|thinking|thought|reasoning)>", "").trim();
                            if (!sanitizedLine.isBlank()) {
                                onChunk.accept("<thinking>" + sanitizedLine + "</thinking>\n");
                            }
                        }
                    }
                }
            }

            if (delta.content() != null && !delta.content().isEmpty()) {
                if (onChunk != null && reasoningStarted[0]) {
                    reasoningStarted[0] = false;
                    String pendingLine = reasoningLine.toString();
                    reasoningLine.setLength(0);
                    if (!pendingLine.isBlank()) {
                        String sanitizedLine = pendingLine.replaceAll("(?i)</?(think|thinking|thought|reasoning)>", "").trim();
                        if (!sanitizedLine.isBlank()) {
                            onChunk.accept("<thinking>" + sanitizedLine + "</thinking>\n");
                        }
                    }
                }
                String contentText = delta.content();
                content.append(contentText);

                // Detectar fin del primer bloque ```tool:...``` cerrado.
                // Al encontrarlo, truncamos el buffer y activamos la compuerta para abortar el stream.
                if (!gateClosed[0]) {
                    int endIdx = ToolBlockProtocol.endOfFirstClosedBlock(content.toString());
                    if (endIdx > 0) {
                        // Truncar el contenido en el punto donde termina el primer bloque tool:
                        int oldLength = content.length();
                        content.setLength(endIdx);
                        // Ajustar la longitud emitida para no re-enviar el bloque truncado
                        if (emittedContentLength[0] > endIdx) {
                            emittedContentLength[0] = endIdx;
                        }
                        gateClosed[0] = true;
                    }
                }

                if (onChunk != null) {
                    // Sanitizar tags de thinking que el modelo pueda haber emitido en content
                    String sanitizedContent = contentText.replaceAll("(?i)</?(?:think|thinking|thought|reasoning)>", "");
                    if (!sanitizedContent.isEmpty()) {
                        onChunk.accept(sanitizedContent);
                    }
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
        }, null, null, combinedAborted);

        if (onChunk != null && reasoningStarted[0] && reasoningLine.length() > 0) {
            String pendingLine = reasoningLine.toString().trim();
            reasoningLine.setLength(0);
            if (!pendingLine.isBlank()) {
                String sanitizedLine = pendingLine.replaceAll("(?i)</?(think|thinking|thought|reasoning)>", "").trim();
                if (!sanitizedLine.isBlank()) {
                    onChunk.accept("<thinking>" + sanitizedLine + "</thinking>\n");
                }
            }
        }

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
        String effectiveSystemPrompt = systemPrompt;
        if (effectiveSystemPrompt != null && !effectiveSystemPrompt.isBlank()) {
            fullMessages.add(ChatMessage.system(effectiveSystemPrompt));
        }
        if (conversation != null) {
            fullMessages.addAll(conversation);
        }

        StringBuilder totalThinking = new StringBuilder();
        int iterationCount = 0;
        final int MAX_TOOL_ITERATIONS = 50;
        int consecutiveBlockedCommands = 0;
        // Detector de estancamiento por thinking repetitivo
        final ThinkingStallDetector stallDetector = new ThinkingStallDetector(3, 10);
        // Deduplicador inteligente: reemplaza la política punitiva de RepeatedToolCallGuard.
        final ToolCallDeduplicator toolCallDeduplicator = new ToolCallDeduplicator();
        final ToolProgressGuard toolProgressGuard = new ToolProgressGuard();
        int consecutiveEmptyIterations = 0;
        List<ThinkingStallDetector.ToolCallSignature> pendingTextToolSignatures = new ArrayList<>();

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
                String thinkingResult = buildThinkingResult(totalThinking);
                return new ChatTurn(capWarning.trim(), thinkingResult);
            }
            iterationCount++;

            ChatRequest.Builder reqBuilder = ChatRequest.builder()
                    .model(model)
                    .messages(fullMessages)
                    .temperature(temperature)
                    .parallelToolCalls(false);

            boolean thinkingRequested = thinkMode != null && !thinkMode.isBlank()
                    && !"none".equalsIgnoreCase(thinkMode) && !"off".equalsIgnoreCase(thinkMode);

            if (thinkingRequested) {
                reqBuilder.reasoningEffort(thinkMode);
                reqBuilder.think(true);
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
                    String thinkingResult = buildThinkingResult(totalThinking);
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

            // Detectar estancamiento por thinking repetitivo sin tool calls
            // Las firmas de tool blocks de texto ejecutados en la iteración anterior
            // se combinan aquí para que el detector también los vea.
            List<ThinkingStallDetector.ToolCallSignature> toolCallSignatures =
                    new ArrayList<>(pendingTextToolSignatures);
            pendingTextToolSignatures.clear();
            if (assistantMsg.toolCalls() != null) {
                for (ToolCall tc : assistantMsg.toolCalls()) {
                    if (tc.function() != null) {
                        toolCallSignatures.add(new ThinkingStallDetector.ToolCallSignature(
                                tc.function().name(),
                                tc.function().arguments() != null ? tc.function().arguments() : "{}"));
                    }
                }
            }
            boolean stall = stallDetector.recordThinking(
                    assistantMsg.reasoningContent() != null ? assistantMsg.reasoningContent() : "",
                    toolCallSignatures);
            if (stall) {
                TaskStateNotifier.setState(TaskExecutionState.INCOMPLETE);
                String warningMsg = "\n\n⚠️ Agente estancado: thinking repetitivo sin progreso. Deteniendo para evitar bucle infinito.";
                if (onChunk != null) {
                    emitText(warningMsg, onChunk);
                }
                String thinkingResult = buildThinkingResult(totalThinking);
                return new ChatTurn(warningMsg.trim(), thinkingResult);
            }
            if (stallDetector.isThinkingLimitReached()) {
                TaskStateNotifier.setState(TaskExecutionState.INCOMPLETE);
                String warningMsg = "\n\n⚠️ Límite de thinking alcanzado (" + stallDetector.getConsecutiveThinkingOnlyIterations() + " iteraciones sin tool calls). Forzando finalización.";
                if (onChunk != null) {
                    emitText(warningMsg, onChunk);
                }
                String thinkingResult = buildThinkingResult(totalThinking);
                return new ChatTurn(warningMsg.trim(), thinkingResult);
            }

            List<ToolCall> toolCalls = assistantMsg.toolCalls();
            // Truncar cleanContent al cierre del primer bloque ```tool:...``` para evitar
            // procesar bloques repetidos emitidos por el modelo en un mismo turno.
            String cleanContentTruncated = cleanContent;
            int endIdx = ToolBlockProtocol.endOfFirstClosedBlock(cleanContent);
            if (endIdx > 0) {
                cleanContentTruncated = cleanContent.substring(0, endIdx);
            }
            List<ToolBlockProtocol.ToolBlock> textToolBlocks =
                    (toolCalls == null || toolCalls.isEmpty()) && toolRegistry != null && !cleanContentTruncated.isBlank()
                            ? ToolBlockProtocol.parse(cleanContentTruncated)
                            : java.util.List.of();

            // Detección de iteraciones vacías: LLM devuelve texto sin tool calls y sin thinking
            boolean hasToolCalls = toolCalls != null && !toolCalls.isEmpty();
            boolean hasTextToolBlocks = !textToolBlocks.isEmpty();
            if (!hasToolCalls && !hasTextToolBlocks) {
                if (assistantMsg.reasoningContent() == null || assistantMsg.reasoningContent().isBlank()) {
                    consecutiveEmptyIterations++;
                    if (consecutiveEmptyIterations >= 3) {
                        TaskStateNotifier.setState(TaskExecutionState.INCOMPLETE);
                        String warningMsg = "\n\n⚠️ El agente no ha realizado ninguna acción en 3 iteraciones consecutivas. Deteniendo para evitar bucle.";
                        if (onChunk != null) {
                            emitText(warningMsg, onChunk);
                        }
                        String thinkingResult = buildThinkingResult(totalThinking);
                        return new ChatTurn(warningMsg.trim(), thinkingResult);
                    }
                } else {
                    consecutiveEmptyIterations = 0;
                }
            } else {
                consecutiveEmptyIterations = 0;
            }

            if (toolCalls == null || toolCalls.isEmpty() || toolRegistry == null) {
                if (!textToolBlocks.isEmpty()) {
                    if (streamedMessage == null && onChunk != null && !cleanContent.isEmpty()) {
                        emitText(cleanContent + "\n", onChunk);
                    }

                    fullMessages.add(ChatMessage.assistant(cleanContent));

                    StringBuilder resultsPayload = new StringBuilder("[TOOL_RESULTS]\n");
                    for (ToolBlockProtocol.ToolBlock block : textToolBlocks) {
                        if (isAborted != null && Boolean.TRUE.equals(isAborted.get())) {
                            TaskStateNotifier.setState(TaskExecutionState.IDLE);
                            return new ChatTurn("", null);
                        }

                        // Deduplicación: si la firma ya fue ejecutada en este turno,
                        // devolver el resultado previo al modelo sin volver a ejecutar.
                        String duplicateMsg = toolCallDeduplicator.recordExecution(
                                block.toolName(), block.jsonArguments(), null);
                        if (duplicateMsg != null) {
                            if (duplicateMsg.startsWith("[STOPPED]")) {
                                TaskStateNotifier.setState(TaskExecutionState.INCOMPLETE);
                                String thinkingResult = buildThinkingResult(totalThinking);
                                return new ChatTurn(duplicateMsg, thinkingResult);
                            }
                            resultsPayload.append("<tool name=\"").append(block.toolName()).append("\">\n")
                                    .append(duplicateMsg)
                                    .append("\n</tool>\n");
                            continue;
                        }

                        pendingTextToolSignatures.add(new ThinkingStallDetector.ToolCallSignature(
                                block.toolName(),
                                block.jsonArguments() != null ? block.jsonArguments() : "{}"));

                        String toolResult;
                        try {
                            toolResult = toolRegistry.execute(block.toolName(), block.jsonArguments());
                        } catch (Exception e) {
                            toolResult = "Error ejecutando " + block.toolName() + ": " + e.getMessage();
                        }

                        // Actualizar el resultado guardado en el deduplicador para futuras llamadas duplicadas
                        toolCallDeduplicator.recordExecution(block.toolName(), block.jsonArguments(), toolResult);

                        // Post-check: detectar falta de progreso informativo (mismo resultado consecutivo)
                        boolean progressLoop = toolProgressGuard.recordResult(
                                block.toolName(), block.jsonArguments(), toolResult);
                        if (progressLoop) {
                            TaskStateNotifier.setState(TaskExecutionState.INCOMPLETE);
                            String warningMsg = "\n\n⚠️ Se detectó un bucle de progreso: la herramienta '"
                                    + block.toolName() + "' devolvió el mismo resultado consecutivamente. Deteniendo el agente.";
                            if (onChunk != null) {
                                emitText(warningMsg, onChunk);
                            }
                            String thinkingResult = buildThinkingResult(totalThinking);
                            return new ChatTurn(warningMsg.trim(), thinkingResult);
                        }

                        taskPending = true;
                        TaskStateNotifier.setState(TaskExecutionState.TASK_IN_PROGRESS);

                        if (isCommandBlocked(toolResult)) {
                            consecutiveBlockedCommands++;
                            if (consecutiveBlockedCommands >= MAX_CONSECUTIVE_BLOCKED_COMMANDS) {
                                TaskStateNotifier.setState(TaskExecutionState.INCOMPLETE);
                                String warningMsg = "\n\n⚠️ El agente intentó ejecutar " + MAX_CONSECUTIVE_BLOCKED_COMMANDS
                                        + " comandos bloqueados consecutivamente. Deteniendo el agente para evitar un bucle infinito.";
                                if (onChunk != null) {
                                    emitText(warningMsg, onChunk);
                                }
                                String thinkingResult = buildThinkingResult(totalThinking);
                                return new ChatTurn(warningMsg.trim(), thinkingResult);
                            }
                        } else {
                            consecutiveBlockedCommands = 0;
                        }

                        if (isModificationResult(block.toolName(), toolResult)) {
                            hasModified = true;
                        }

                        resultsPayload.append("<tool name=\"").append(block.toolName()).append("\">\n")
                                .append(toolResult)
                                .append("\n</tool>\n");
                    }

                    fullMessages.add(ChatMessage.user(resultsPayload.toString()));
                    continue;
                }

                // Caso A: Chat normal (nunca se activó ninguna herramienta) o sin registry
                if (!taskPending || toolRegistry == null) {
                    if (streamedMessage == null && onChunk != null && !cleanContent.isEmpty()) {
                        emitText(cleanContent, onChunk);
                    }
                    String thinkingResult = buildThinkingResult(totalThinking);
                    return new ChatTurn(cleanContent, thinkingResult);
                }

                // Caso B: Se ejecutaron tools y el modelo decide finalizar su turno. Se entrega la respuesta tal cual,
                // sin reintentos ni exigencia de modificaciones.
                taskPending = false;
                TaskStateNotifier.setState(hasModified ? TaskExecutionState.COMPLETED : TaskExecutionState.IDLE);
                if (streamedMessage == null && onChunk != null && !cleanContent.isEmpty()) {
                    emitText(cleanContent, onChunk);
                }
                String thinkingResult = buildThinkingResult(totalThinking);
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

                // Deduplicación: si la firma ya fue ejecutada en este turno,
                // devolver el resultado previo al modelo sin volver a ejecutar.
                String duplicateMsg = toolCallDeduplicator.recordExecution(toolName, toolArgs, null);
                if (duplicateMsg != null) {
                    if (duplicateMsg.startsWith("[STOPPED]")) {
                        TaskStateNotifier.setState(TaskExecutionState.INCOMPLETE);
                        String thinkingResult = buildThinkingResult(totalThinking);
                        return new ChatTurn(duplicateMsg, thinkingResult);
                    }
                    fullMessages.add(ChatMessage.tool(call.id(), duplicateMsg));
                    continue;
                }

                if (nativeCallIndexes != null) {
                    streamedMessage.previewStreamer().prepareExecution(nativeCallIndexes.get(callIndex));
                }
                try {
                    toolResult = toolRegistry.execute(toolName, toolArgs);
                } catch (WebSearchFallbackException e) {
                    // Fallback: usar contenido previamente fetchado (web_fetch) si está disponible
                    String fallbackContent = extractPreviousFetchContent(fullMessages);
                    if (!fallbackContent.isBlank()) {
                        toolResult = "{\"success\":true,\"message\":\"Búsqueda web falló, usando contenido previamente fetchado.\",\"content\":\"" + fallbackContent.replace("\"", "\\\"") + "\"}";
                        ToolProgressNotifier.notify("web_search", "Fallback: usando contenido previamente fetchado");
                    } else {
                        toolResult = "{\"success\":false,\"error\":\"" + e.getMessage().replace("\"", "\\\"") + "\"}";
                    }
                } catch (Exception e) {
                    toolResult = "Error ejecutando " + toolName + ": " + e.getMessage();
                } finally {
                    WriteArgsPreviewStreamer.clearLivePreview();
                }

                // Actualizar el resultado guardado en el deduplicador para futuras llamadas duplicadas
                toolCallDeduplicator.recordExecution(toolName, toolArgs, toolResult);

                // Post-check: detectar falta de progreso informativo (mismo resultado consecutivo)
                boolean progressLoop = toolProgressGuard.recordResult(toolName, toolArgs, toolResult);
                if (progressLoop) {
                    TaskStateNotifier.setState(TaskExecutionState.INCOMPLETE);
                    String warningMsg = "\n\n⚠️ Se detectó un bucle de progreso: la herramienta '"
                            + toolName + "' devolvió el mismo resultado consecutivamente. Deteniendo el agente.";
                    if (onChunk != null) {
                        emitText(warningMsg, onChunk);
                    }
                    String thinkingResult = buildThinkingResult(totalThinking);
                    return new ChatTurn(warningMsg.trim(), thinkingResult);
                }

                // En cuanto se invoca CUALQUIER herramienta, se activa taskPending
                taskPending = true;
                TaskStateNotifier.setState(TaskExecutionState.TASK_IN_PROGRESS);

                if (isCommandBlocked(toolResult)) {
                    consecutiveBlockedCommands++;
                    if (consecutiveBlockedCommands >= MAX_CONSECUTIVE_BLOCKED_COMMANDS) {
                        TaskStateNotifier.setState(TaskExecutionState.INCOMPLETE);
                        String warningMsg = "\n\n⚠️ El agente intentó ejecutar " + MAX_CONSECUTIVE_BLOCKED_COMMANDS
                                + " comandos bloqueados consecutivamente. Deteniendo el agente para evitar un bucle infinito.";
                        if (onChunk != null) {
                            emitText(warningMsg, onChunk);
                        }
                        String thinkingResult = buildThinkingResult(totalThinking);
                        return new ChatTurn(warningMsg.trim(), thinkingResult);
                    }
                } else {
                    consecutiveBlockedCommands = 0;
                }

                // Verificamos si esta herramienta aplicó una modificación real
                if (isModificationResult(toolName, toolResult)) {
                    hasModified = true;
                }

                fullMessages.add(ChatMessage.tool(call.id(), toolResult));
            }
        }

        TaskStateNotifier.setState(hasModified ? TaskExecutionState.COMPLETED : TaskExecutionState.IDLE);
        return new ChatTurn("", buildThinkingResult(totalThinking));
    }

    private void emitText(String text, Consumer<String> onChunk) {
        if (text == null || text.isEmpty() || onChunk == null) return;
        String[] lines = text.split("(?<=\\n)");
        for (String line : lines) {
            onChunk.accept(line);
        }
    }

    /**
     * Extrae contenido previamente fetchado (web_fetch) de los mensajes de conversación
     * para usarlo como fallback cuando web_search falla.
     */
    private static String extractPreviousFetchContent(List<ChatMessage> conversation) {
        StringBuilder fetchedContent = new StringBuilder();
        for (ChatMessage msg : conversation) {
            String json = msg.content() != null ? msg.content() : "";
            if (json.contains("web_fetch") && json.contains("\"success\":true")) {
                int contentIdx = json.indexOf("\"content\"");
                if (contentIdx >= 0) {
                    int valueStart = json.indexOf(':', contentIdx) + 1;
                    int valueEnd = json.indexOf('}', valueStart);
                    if (valueEnd < 0) valueEnd = json.length();
                    String contentValue = json.substring(valueStart, valueEnd).trim();
                    if (contentValue.startsWith("\"") && contentValue.endsWith("\"")) {
                        contentValue = contentValue.substring(1, contentValue.length() - 1);
                    }
                    fetchedContent.append(contentValue).append("\n");
                }
            }
        }
        return fetchedContent.toString().trim();
    }

    public record ChatTurn(String response, String thinking) {}

    private static String buildThinkingResult(StringBuilder totalThinking) {
        if (totalThinking.length() == 0) {
            return null;
        }
        String cleaned = ORPHAN_THINK_TAGS.matcher(totalThinking.toString().trim()).replaceAll("").trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

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
