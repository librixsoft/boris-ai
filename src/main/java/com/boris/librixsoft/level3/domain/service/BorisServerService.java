package com.boris.librixsoft.level3.domain.service;

import com.boris.librixsoft.dto.ApiResponse;
import com.boris.librixsoft.dto.ChatMessageResponse;
import com.boris.librixsoft.dto.ConversationSession;
import com.boris.librixsoft.dto.LoadModelRequest;
import com.boris.librixsoft.level4.wrapper.llama.BorisLLamaServer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.HashMap;
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

    private final BorisLLamaServer llamaWrapper;
    private final ModelService modelService;
    private final ConversationHistoryService conversationHistoryService;
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

        System.out.println("📥 [Service] loadModel() called with: model=" + model + ", id=" + id + ", contextSize=" + contextSize + ", threads=" + threads + ", gpuLayers=" + gpuLayers);
        System.out.println("📥 [Service] Step 1: Validating model name...");

        if (model == null || model.isBlank()) {
            System.out.println("❌ [Service] Missing model field");
            return ApiResponse.error("Missing 'model' field");
        }
        try {
            String resolvedId = (id == null || id.isBlank()) ? model : id;
            System.out.println("📥 [Service] Step 2: resolvedId=" + resolvedId);
            System.out.println("📥 [Service] Step 3: Checking if shouldLoad=" + shouldLoad);

            // Only load model into VRAM if shouldLoad is true
            if (shouldLoad) {
                System.out.println("📥 [Service] Step 4: Calling llamaWrapper.loadModelWithParams...");
                long t0 = System.currentTimeMillis();
                llamaWrapper.loadModelWithParams(resolvedId, model, contextSize, threads, gpuLayers, batchSize, temperature, maxTokens, parallel);
                long t1 = System.currentTimeMillis();
                System.out.println("✅ [Service] llamaWrapper.loadModelWithParams completed in " + (t1 - t0) + " ms");
            } else {
                System.out.println("ℹ️ [Service] Skipping VRAM load (candidate-only mode)");
            }

            System.out.println("📥 [Service] Step 5: Building success response");
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
            System.out.println("❌ [Service] Exception caught: " + e.getMessage());
            e.printStackTrace();
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

            conversationHistoryService.clearHistory(sessionId);
            return ApiResponse.ok("Conversation state cleared without touching loaded model.");
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    public ApiResponse<ConversationSession> getConversationHistory(String sessionId) {
        try {
            return ApiResponse.success(conversationHistoryService.getSessionSnapshot(sessionId));
        } catch (Exception e) {
            return ApiResponse.error(e.getMessage());
        }
    }

    public ApiResponse<Map<String, Object>> executeAgentAction(String instruction, String sessionId) {
        System.out.println("🤖 \u001B[96m[Agent Instruction]\u001B[0m " + instruction);

        // Reset cancellation flag
        cancellationRequested.set(false);

        // Create task to run in separate thread
        CompletableFuture<ApiResponse<Map<String, Object>>> task = CompletableFuture.supplyAsync(() ->
                modelService.executeFlow(instruction, cancellationRequested, sessionId),
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

    public Flux<ServerSentEvent<ChatMessageResponse>> streamAgentAction(String instruction, String sessionId) {
        System.out.println("🤖 \u001B[96m[Agent Instruction - Stream]\u001B[0m " + instruction);

        cancellationRequested.set(false);
        return modelService.streamFlow(instruction, cancellationRequested, sessionId)
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

    public ApiResponse<Map<String, String>> cancelCurrentTask() {
        cancellationRequested.set(true);
        CompletableFuture<ApiResponse<Map<String, Object>>> task = currentTask.get();
        if (task != null && !task.isDone()) {
            task.cancel(true);
        }
        System.out.println("🛑 [Service] Task/Stream cancellation requested.");
        return ApiResponse.ok("Task cancellation requested");
    }


    // ── private helpers ───────────────────────────────────────────────────────

    private void logFlowResult(ApiResponse<?> result) {
        if ("success".equals(result.getStatus())) {
            System.out.println("✅ \u001B[92m[Flow Completed]\u001B[0m");
        } else {
            System.out.println("❌ \u001B[91m[Flow Failed]\u001B[0m");
        }
    }

}