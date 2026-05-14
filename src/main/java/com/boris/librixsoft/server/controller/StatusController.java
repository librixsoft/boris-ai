package com.boris.librixsoft.server.controller;

import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.server.dto.ApiResponse;
import com.boris.librixsoft.server.service.llama.BorisLLamaServerWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/status")
@RequiredArgsConstructor
public class StatusController {

    private final BorisProperties properties;
    private final com.boris.librixsoft.server.service.llama.BorisLLamaServerWrapper llamaServer;
    private final com.boris.librixsoft.server.service.StatusEventService statusEventService;

    @GetMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter streamStatus() {
        return statusEventService.createEmitter();
    }

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus() {
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        try {
            boolean ready = llamaServer.isReady();

            body.put("status", ready ? "UP" : "DOWN");
            body.put("version", properties.getVersion());
            body.put("llama_port", properties.getPort());
            body.put("models_dir", properties.getModelsDir());
            body.put("models_max", properties.getModelsMax());

            return ResponseEntity.ok(ApiResponse.success(body));
        } catch (Exception e) {
            body.put("status", "error");
            body.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(ApiResponse.error(e.getMessage(), body));
        }
    }
}
