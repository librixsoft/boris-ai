// BorisServerController.java
package com.boris.librixsoft.level1.presentation.rest.server.controller;

import com.boris.librixsoft.dto.ApiResponse;
import com.boris.librixsoft.dto.ChatMessageResponse;
import com.boris.librixsoft.dto.LoadModelRequest;
import com.boris.librixsoft.dto.MultiModelRequest;
import com.boris.librixsoft.level3.domain.service.BorisServerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.Map;

@RestController
@RequestMapping("/boris/v1")
@RequiredArgsConstructor
public class BorisServerController {

    private final BorisServerService borisServerService;

    // The /models endpoint is no longer used, UI calls /api/models/with-sizes instead

    /** Carga un modelo en memoria con los parámetros especificados (contextSize, threads, gpuLayers, etc.). */
    @PostMapping("/models/load")
    public ApiResponse<Map<String, Object>> loadModel(@RequestBody LoadModelRequest request) {
        System.out.println("🔥 [CONTROLLER] POST /models/load received. model=" + request.getModel() + ", id=" + request.getId());
        long t0 = System.currentTimeMillis();
        ApiResponse<Map<String, Object>> result = borisServerService.loadModel(request);
        long t1 = System.currentTimeMillis();
        System.out.println("✅ [CONTROLLER] POST /models/load returning after " + (t1 - t0) + " ms. status=" + result.getStatus());
        return result;
    }

    /** Descarga un modelo de memoria liberando VRAM/RAM. */
    @PostMapping("/models/unload")
    public ApiResponse<Map<String, String>> unloadModel(@RequestBody Map<String, String> body) {
        return borisServerService.unloadModel(body);
    }

    /** Limpia la conversación activa y el KV cache sin descargar el modelo. */
    @PostMapping("/clearmodel")
    public ApiResponse<Map<String, String>> clearModel(@RequestBody Map<String, Object> body) {
        return borisServerService.clearModel(body);
    }

    @GetMapping("/conversations/{sessionId}")
    public ApiResponse<?> getConversationHistory(@PathVariable String sessionId) {
        return borisServerService.getConversationHistory(sessionId);
    }

    /** Ejecuta una instrucción de agente usando un único modelo. */
    @PostMapping("/chat/completions")
    public ApiResponse<Map<String, Object>> executeAgentAction(@RequestBody Map<String, Object> payload) {
        String instruction = (String) payload.get("instruction");
        String sessionId = (String) payload.get("sessionId");

        System.out.println("🔍 [DEBUG] Received sessionId: " + sessionId + " (null? " + (sessionId == null) + ", blank? " + (sessionId != null && sessionId.isBlank()) + ")");

        if (instruction == null || instruction.isBlank()) {
            return ApiResponse.error("Missing 'instruction' field");
        }

        return borisServerService.executeAgentAction(instruction, sessionId);
    }

    /** Ejecuta una instrucción de agente usando streaming SSE. */
    @PostMapping(value = "/chat/completions", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatMessageResponse>> executeAgentActionStream(@RequestBody Map<String, Object> payload) {
        String instruction = (String) payload.get("instruction");
        String sessionId = (String) payload.get("sessionId");

        if (instruction == null || instruction.isBlank()) {
            return Flux.just(ServerSentEvent.<ChatMessageResponse>builder()
                    .data(new ChatMessageResponse(null, "error", "Missing 'instruction' field", null))
                    .build());
        }

        return borisServerService.streamAgentAction(instruction, sessionId);
    }

    /** Cancela la tarea actual del agente en ejecución. */
    @PostMapping("/agent/cancel")
    public ApiResponse<Map<String, String>> cancelCurrentTask() {
        return borisServerService.cancelCurrentTask();
    }

}