package com.boris.librixsoft.level3.domain.service;

import com.boris.librixsoft.ai.Message;
import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.dto.ApiResponse;
import com.boris.librixsoft.dto.ChatMessageResponse;
import com.boris.librixsoft.dto.TokenInfo;
import com.boris.librixsoft.level2.application.agent.tools.ReadFileTool;
import com.boris.librixsoft.level4.wrapper.llama.BorisLLamaServer;
import com.boris.librixsoft.level4.wrapper.llama.LlamaChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
@Service
@RequiredArgsConstructor
public class ModelService {

    private static final String DEFAULT_SYSTEM_PROMPT = LlamaChatService.SYSTEM_PROMPT;

    private final LlamaChatService llamaChatService;
    private final BorisLLamaServer borisLLamaServer;
    private final ConversationHistoryService conversationHistoryService;

    public ApiResponse<Map<String, Object>> executeFlow(String instruction,
                                                        AtomicBoolean cancellationRequested,
                                                        String sessionId) {
        try {
            BorisProperties.ModelConfig cfg = resolveModelConfig();
            List<Message> history = conversationHistoryService.getHistoryAsAiMessages(sessionId);
            String modelResponse = llamaChatService.executePromptWithTools(
                cfg.getId(), DEFAULT_SYSTEM_PROMPT, instruction, cfg.getTemperature(), null, cancellationRequested, cfg.getMaxTokens(), history);

            conversationHistoryService.appendUserMessage(sessionId, instruction);
            conversationHistoryService.appendAssistantMessage(sessionId, modelResponse);

            TokenInfo tokens = llamaChatService.getTokenInfo();
            return ApiResponse.ok(modelResponse, Map.of(
                "type", "direct",
                "result", modelResponse,
                "sessionId", sessionId,
                "tokens", tokens != null ? tokens : Map.of()
            ));
        } catch (Exception e) {
            log.error("Error en executeFlow: {}", e.getMessage());
            return ApiResponse.error("Error: " + e.getMessage());
        }
    }

    public Flux<ChatMessageResponse> streamFlow(String instruction,
                                                AtomicBoolean cancellationRequested,
                                                String sessionId) {
        BorisProperties.ModelConfig cfg = resolveModelConfig();
        List<Message> history = conversationHistoryService.getHistoryAsAiMessages(sessionId);
        String normalizedSessionId = sessionId == null || sessionId.isBlank() ? "default-session" : sessionId;

        conversationHistoryService.appendUserMessage(normalizedSessionId, instruction);

        List<Message> localHistory = history != null ? new ArrayList<>(history) : new ArrayList<>();

        return streamFlowAgent(instruction, localHistory, 1, 8, cfg, cancellationRequested, normalizedSessionId)
                .doOnError(error -> {
                    conversationHistoryService.clearHistory(normalizedSessionId);
                    for (Message message : history) {
                        if (message != null && message.getText() != null && !message.getText().isBlank()) {
                            switch (message.getMessageType()) {
                                case USER -> conversationHistoryService.appendUserMessage(normalizedSessionId, message.getText());
                                case ASSISTANT -> conversationHistoryService.appendAssistantMessage(normalizedSessionId, message.getText());
                                case SYSTEM -> conversationHistoryService.appendSystemMessage(normalizedSessionId, message.getText());
                            }
                        }
                    }
                });
    }

    private Flux<ChatMessageResponse> streamFlowAgent(String currentInput,
                                                      List<Message> localHistory,
                                                      int turn,
                                                      int maxTurns,
                                                      BorisProperties.ModelConfig cfg,
                                                      AtomicBoolean cancellationRequested,
                                                      String normalizedSessionId) {
        if (turn >= maxTurns || cancellationRequested.get()) {
            return Flux.empty();
        }

        StringBuilder turnResponse = new StringBuilder();

        return llamaChatService.streamPrompt(
                cfg.getId(), DEFAULT_SYSTEM_PROMPT, currentInput, cfg.getTemperature(),
                cancellationRequested, localHistory, cfg.getMaxTokens()
        ).map(response -> {
            String text = response.getResults() != null && !response.getResults().isEmpty()
                    ? response.getResults().get(0).getOutput().getText()
                    : "";
            if (!text.isEmpty()) {
                turnResponse.append(text);
            }
            return new ChatMessageResponse(null, "success", null, text);
        }).concatWith(Flux.defer(() -> {
            if (cancellationRequested != null && cancellationRequested.get()) {
                log.info("🛑 [ModelService] Flow cancelled, skipping tool execution.");
                return Flux.empty();
            }

            String fullResponse = turnResponse.toString();
            String toolResults = llamaChatService.executeNativeToolsDirectly(fullResponse, cancellationRequested);

            if (cancellationRequested != null && cancellationRequested.get()) {
                log.info("🛑 [ModelService] Flow cancelled after tool execution.");
                return Flux.empty();
            }

            if (!toolResults.equals(fullResponse)) {
                log.info("🛠️ [JNA STREAM TOOL CALL] Turno {}: Herramientas ejecutadas", turn);

                localHistory.add(new com.boris.librixsoft.ai.UserMessage(currentInput));
                localHistory.add(new com.boris.librixsoft.ai.AssistantMessage(fullResponse));

                String toolSummary = toolResults.length() > 500 ? toolResults.substring(0, 500) + "..." : toolResults;
                localHistory.add(new com.boris.librixsoft.ai.UserMessage("[Resultado de herramienta]:\n" + toolSummary));

                // Guardar el bloque JSON y el resultado de la herramienta en la base de datos para que persistan en el chat
                conversationHistoryService.appendAssistantMessage(normalizedSessionId, fullResponse);

                String toolFeedbackText = "\n\n🛠️ *[Backend: Herramienta ejecutada]*\n```\n" + toolSummary + "\n```\n\n";
                conversationHistoryService.appendUserMessage(normalizedSessionId, toolFeedbackText);

                // Emitir feedback visual de ejecución de herramienta al frontend
                ChatMessageResponse toolFeedback = new ChatMessageResponse(
                        null, "success", null, toolFeedbackText
                );

                String nextInput = "[TOOL_RESULT]\n" + toolResults.trim() + "\n[/TOOL_RESULT]\n" +
                                   "Tarea completada. ¿Necesitas realizar alguna otra acción en los archivos leídos o ejecutar comandos?";

                return Flux.just(toolFeedback)
                        .concatWith(streamFlowAgent(nextInput, localHistory, turn + 1, maxTurns, cfg, cancellationRequested, normalizedSessionId));
            } else {
                if (fullResponse.length() > 0 && (cancellationRequested == null || !cancellationRequested.get())) {
                    conversationHistoryService.appendAssistantMessage(normalizedSessionId, fullResponse);
                }

                TokenInfo tokens = llamaChatService.getTokenInfo();
                if (tokens != null && (cancellationRequested == null || !cancellationRequested.get())) {
                    return Flux.just(new ChatMessageResponse(
                            null, "tokens", null, "",
                            tokens.getInputTokens(),
                            tokens.getOutputTokens(),
                            tokens.getTotalTokens(),
                            tokens.getContextSize()
                    ));
                }
                return Flux.empty();
            }
        }));
    }

    public void startNewConversation() {
        llamaChatService.startNewConversation();
    }

    public BorisProperties.ModelConfig resolveModelConfig() {
        return borisLLamaServer.resolveConfig(null, 0);
    }

    public BorisProperties.ModelConfig resolveModelConfig(String modelId) {
        return borisLLamaServer.resolveConfig(modelId, 0);
    }
}
