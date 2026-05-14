package com.boris.librixsoft.server.service;

import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.server.dto.ApiResponse;
import com.boris.librixsoft.server.dto.ChatMessageResponse;
import com.boris.librixsoft.server.dto.LoadModelRequest;
import com.boris.librixsoft.server.queue.ChatMessageQueue;
import com.boris.librixsoft.server.service.llama.BorisLLamaServerWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
@Service
@RequiredArgsConstructor
public class BorisServerService {

    private final BorisLLamaServerWrapper llamaWrapper;
    private final ModelService modelService;
    private final ChatSessionService chatSessionService;
    private final ChatMessageQueue chatMessageQueue;
    private final ChatQueueProcessorService chatQueueProcessorService;
    private final AtomicReference<CompletableFuture<ApiResponse<Map<String, Object>>>> currentTask = new AtomicReference<>(null);
    private final AtomicBoolean cancellationRequested = new AtomicBoolean(false);
    private final ExecutorService taskExecutor = Executors.newSingleThreadExecutor();

    // listModels removed as the endpoint was removed

    public ApiResponse<Map<String, Object>> loadModel(LoadModelRequest request) {
        String id          = request.getId();
        String model       = request.getModel();
        Integer contextSize = request.getContextSize();
        Integer threads    = request.getThreads();
        Integer gpuLayers  = request.getGpuLayers();
        Integer batchSize  = request.getBatchSize();
        Double temperature = request.getTemperature();
        Integer maxTokens  = request.getMaxTokens();
        Integer parallel   = request.getParallel();
        Boolean shouldLoad = request.getLoadModel() != null ? request.getLoadModel() : true;

        System.out.println("📥 [LoadModel] Received request: model=" + model + ", id=" + id + ", loadModel=" + shouldLoad);

        if (model == null || model.isBlank()) {
            return ApiResponse.error("Missing 'model' field");
        }
        try {
            String resolvedId = (id == null || id.isBlank()) ? model : id;

            // Only load model into VRAM if shouldLoad is true
            if (shouldLoad) {
                llamaWrapper.loadModelWithParams(resolvedId, model, contextSize, threads, gpuLayers, batchSize, temperature, maxTokens, parallel);
                System.out.println("✅ [LoadModel] Model loaded into VRAM: " + model);
            } else {
                System.out.println("ℹ️ [LoadModel] Skipping VRAM load (candidate-only mode) for: " + model);
            }

            String actionMsg = shouldLoad ? "Loading model: " : "Registered candidate (not loaded): ";
            Map<String, Object> responseData = new HashMap<>();
            responseData.put("id", resolvedId);
            return ApiResponse.ok(actionMsg + model
                    + (contextSize  != null ? " (context: "    + contextSize  + ")" : "")
                    + (threads      != null ? " (threads: "    + threads      + ")" : "")
                    + (gpuLayers    != null ? " (gpu-layers: " + gpuLayers    + ")" : "")
                    + (batchSize    != null ? " (batch: "      + batchSize    + ")" : "")
                    + (temperature  != null ? " (temperature: "+ temperature  + ")" : "")
                    + (maxTokens    != null ? " (maxTokens: "  + maxTokens    + ")" : ""), responseData);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }


    public ApiResponse<Map<String, String>> unloadModel(Map<String, String> body) {
        String model = body.get("model");
        if (model == null || model.isBlank()) {
            return ApiResponse.error("Missing 'model' field");
        }
        try {
            llamaWrapper.unloadModel(model);
            return ApiResponse.ok("Unloaded model: " + model);
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    public ApiResponse<Map<String, String>> clearModel(Map<String, Object> body) {
        String sessionId = (String) body.get("sessionId");
        boolean deep = Boolean.TRUE.equals(body.get("all"));
        boolean reinit = !Boolean.FALSE.equals(body.get("reinit"));

        try {
            if (deep) {
                System.out.println("🚨 [EXTREME CLEAN] Deep cleanup requested - clearing ALL resources");
                llamaWrapper.forceUnloadAll();
                chatSessionService.clearAll();
                System.gc();

                if (reinit) {
                    llamaWrapper.reloadPreloadedModels();
                }

                return ApiResponse.ok("Extreme cleanup complete: VRAM released, sessions purged, and preloaded models re-initialized.");
            }

            System.out.println("🧹 [CHAT RESET] Clearing current chat only. sessionId=" + sessionId);
            try {
                llamaWrapper.clearModel();
            } catch (Exception e) {
                System.err.println("Failed to clear KV cache: " + e.getMessage());
            }

            if (sessionId != null && !sessionId.isBlank()) {
                chatSessionService.deleteSession(sessionId);
            }
            return ApiResponse.ok("Conversation state cleared without touching model warmup");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    public ApiResponse<Map<String, Object>> executeAgentAction(String instruction, String sessionId) {
        System.out.println("🤖 \u001B[96m[Agent Instruction]\u001B[0m " + instruction);

        // Reset cancellation flag
        cancellationRequested.set(false);

        // Enviar mensaje a la cola
        CompletableFuture<ChatMessageResponse> queueResponse = chatMessageQueue.sendMessage(instruction, sessionId);
        
        // Iniciar procesador para la sesión
        chatQueueProcessorService.startProcessingSession(
            (sessionId == null || sessionId.isBlank()) ? "default" : sessionId
        );

        try {
            // Esperar respuesta de la cola (con timeout de 2 minutos)
            ChatMessageResponse response = queueResponse.get(2, java.util.concurrent.TimeUnit.MINUTES);
            
            // Convertir ChatMessageResponse a Map<String, Object>
            Map<String, Object> resultData = new HashMap<>();
            if (response.getContent() != null) {
                resultData.put("result", response.getContent());
            }
            
            ApiResponse<Map<String, Object>> result = ApiResponse.success(response.getMessage(), resultData);
            logFlowResult(result);
            return result;
        } catch (java.util.concurrent.TimeoutException e) {
            log.error("Timeout waiting for queue response");
            return ApiResponse.error("Timeout waiting for response");
        } catch (Exception e) {
            if (cancellationRequested.get()) {
                return ApiResponse.cancelled("Task cancelled by user");
            }
            logFlowResult(ApiResponse.error(e.getMessage()));
            return ApiResponse.error(e.getMessage());
        }
    }

    public Flux<ServerSentEvent<ChatMessageResponse>> streamAgentAction(String instruction, String sessionId) {
        System.out.println("🤖 \u001B[96m[Agent Instruction - Stream]\u001B[0m " + instruction);

        cancellationRequested.set(false);
        String effectiveSessionId = chatSessionService.getOrCreateSession(sessionId);
        List<Message> rawHistory = chatSessionService.getSessionHistory(effectiveSessionId);
        List<Message> history = trimHistory(rawHistory, 6);

        return modelService.streamFlow(instruction, history, cancellationRequested, effectiveSessionId)
                .map(response -> ServerSentEvent.<ChatMessageResponse>builder()
                        .data(response)
                        .build())
                .onErrorResume(e -> {
                    log.error("Stream error: {}", e.getMessage());
                    return Flux.just(ServerSentEvent.<ChatMessageResponse>builder()
                            .data(new ChatMessageResponse(null, "error", e.getMessage(), null))
                            .build());
                });
    }

    /**
     * Método legacy para ejecutar acciones directamente sin cola.
     * Se mantiene por compatibilidad pero se recomienda usar executeAgentAction con colas.
     */
    public ApiResponse<Map<String, Object>> executeAgentActionDirect(String instruction, String sessionId) {
        System.out.println("🤖 \u001B[96m[Agent Instruction - Direct]\u001B[0m " + instruction);

        // Reset cancellation flag
        cancellationRequested.set(false);

// Resolve session for history
String effectiveSessionId = chatSessionService.getOrCreateSession(sessionId);
List<Message> rawHistory = chatSessionService.getSessionHistory(effectiveSessionId);
List<Message> history = trimHistory(rawHistory, 6); // ← últimos 3 intercambios

        // Create task to run in separate thread
        CompletableFuture<ApiResponse<Map<String, Object>>> task = CompletableFuture.supplyAsync(() ->
                modelService.executeFlow(instruction, history, cancellationRequested, effectiveSessionId),
                taskExecutor);

        // Store reference for cancellation
        currentTask.set(task);

        try {
            ApiResponse<Map<String, Object>> result = task.get();
            logFlowResult(result);
            return result;
        } catch (Exception e) {
            if (cancellationRequested.get()) {
                return ApiResponse.cancelled("Task cancelled by user");
            }
            logFlowResult(ApiResponse.error(e.getMessage()));
            return ApiResponse.error(e.getMessage());
        } finally {
            currentTask.set(null);
        }
    }

    public ApiResponse<Map<String, String>> cancelCurrentTask() {
        CompletableFuture<ApiResponse<Map<String, Object>>> task = currentTask.get();
        if (task != null && !task.isDone()) {
            cancellationRequested.set(true);
            task.cancel(true);
            return ApiResponse.ok("Task cancellation requested");
        }
        return ApiResponse.error("No active task to cancel");
    }


    // ── private helpers ───────────────────────────────────────────────────────

    private void logFlowResult(ApiResponse<?> result) {
        if ("success".equals(result.getStatus())) {
            System.out.println("✅ \u001B[92m[Flow Completed]\u001B[0m");
        } else {
            System.out.println("❌ \u001B[91m[Flow Failed]\u001B[0m");
        }
    }

        /**
 * Limita el historial a los últimos N intercambios para evitar
 * re-procesar un contexto creciente en cada request.
 * Siempre conserva pares user/assistant completos.
 */
private List<Message> trimHistory(List<Message> history, int maxMessages) {
    if (history == null || history.size() <= maxMessages) return history;
    // maxMessages debe ser par para conservar pares completos
    int even = maxMessages % 2 == 0 ? maxMessages : maxMessages - 1;
    return history.subList(history.size() - even, history.size());
}

}