// BorisServerController.java
package com.boris.librixsoft.server.controller;

import com.boris.librixsoft.server.dto.ApiResponse;
import com.boris.librixsoft.server.dto.ChatMessageResponse;
import com.boris.librixsoft.server.dto.LoadModelRequest;
import com.boris.librixsoft.server.dto.MultiModelRequest;
import com.boris.librixsoft.server.queue.ChatMessageQueue;
import com.boris.librixsoft.server.service.BorisServerService;
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
    private final ChatMessageQueue chatMessageQueue;

    // The /models endpoint is no longer used, UI calls /api/models/with-sizes instead

    /** Carga un modelo en memoria con los parámetros especificados (contextSize, threads, gpuLayers, etc.). */
    @PostMapping("/models/load")
    public ApiResponse<Map<String, Object>> loadModel(@RequestBody LoadModelRequest request) {
        return borisServerService.loadModel(request);
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

    /** Obtiene estadísticas de las colas de chat. */
    @GetMapping("/queue/stats")
    public ApiResponse<Map<String, Object>> getQueueStats() {
        return ApiResponse.success(chatMessageQueue.getStats());
    }

    /** Limpia la cola de una sesión específica. */
    @PostMapping("/queue/clear")
    public ApiResponse<Map<String, String>> clearSessionQueue(@RequestBody Map<String, String> body) {
        String sessionId = body.get("sessionId");
        if (sessionId == null || sessionId.isBlank()) {
            return ApiResponse.error("Missing 'sessionId' field");
        }
        chatMessageQueue.clearSession(sessionId);
        return ApiResponse.ok("Cleared queue for session: " + sessionId);
    }

}