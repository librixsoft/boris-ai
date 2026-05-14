package com.boris.librixsoft.server.controller;

import com.boris.librixsoft.server.dto.ApiResponse;
import com.boris.librixsoft.server.service.llama.BorisLLamaServerWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/models")
@RequiredArgsConstructor
public class ModelsController {

    private final BorisLLamaServerWrapper llamaServer;

    // /api/models removed, use /loaded or /with-sizes instead

    @GetMapping(value = "/loaded", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> getLoadedModels() throws IOException {
        String raw = llamaServer.listModels();
        List<Map<String, Object>> models = extractLoadedModels(raw);
        // Add size information to loaded models
        for (Map<String, Object> model : models) {
            String modelId = String.valueOf(model.getOrDefault("id", model.getOrDefault("name", "unknown-model")));
            Map<String, Object> sizeInfo = llamaServer.getModelSize(modelId);
            model.put("size", sizeInfo.get("size"));
            model.put("sizeBytes", sizeInfo.get("sizeBytes"));
        }
        return ApiResponse.success(Map.of("data", models));
    }

    @GetMapping(value = "/with-sizes", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> getModelsWithSizes() throws IOException {
        String raw = llamaServer.listModels();
        List<Map<String, Object>> models = extractModelsWithSizes(raw);
        return ApiResponse.success(Map.of("data", models));
    }

    @GetMapping(value = "/warmup-status", produces = MediaType.APPLICATION_JSON_VALUE)
    public ApiResponse<Map<String, Object>> getWarmupStatus() {
        boolean warmupComplete = llamaServer.isWarmupComplete();
        String statusMessage = llamaServer.getWarmupStatusMessage();
        String state = warmupComplete ? "ready" : "warming";
        return ApiResponse.success(Map.of(
                "warmupComplete", warmupComplete,
                "state", state,
                "statusMessage", statusMessage
        ));
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractLoadedModels(String rawJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> parsed = mapper.readValue(rawJson, Map.class);
            Object data = parsed.get("data");
            if (!(data instanceof List<?> dataList)) {
                return List.of();
            }

            List<Map<String, Object>> loaded = new ArrayList<>();
            Map<String, Integer> counters = new LinkedHashMap<>();

            for (Object rawItem : dataList) {
                if (!(rawItem instanceof Map<?, ?> rawMap)) {
                    continue;
                }

                Map<String, Object> item = (Map<String, Object>) rawMap;
                Object status = item.get("status");
                boolean isLoaded;
                if (status instanceof Map<?, ?> statusMap) {
                    Object value = statusMap.get("value");
                    isLoaded = value != null && "loaded".equalsIgnoreCase(String.valueOf(value));
                } else {
                    isLoaded = status != null && "loaded".equalsIgnoreCase(String.valueOf(status));
                }

                if (!isLoaded) {
                    continue;
                }

                HashMap<String, Object> copy = new HashMap<>(item);
                String baseId = String.valueOf(copy.getOrDefault("id", copy.getOrDefault("name", "unknown-model")));
                int count = counters.getOrDefault(baseId, 0) + 1;
                counters.put(baseId, count);
                copy.put("baseId", baseId);
                copy.put("instance", count);
                copy.put("id", count == 1 ? baseId : baseId + ":" + count);
                loaded.add(copy);
            }

            return loaded;
        } catch (Exception e) {
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> extractModelsWithSizes(String rawJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> parsed = mapper.readValue(rawJson, Map.class);
            Object data = parsed.get("data");
            if (!(data instanceof List<?> dataList)) {
                return List.of();
            }

            List<Map<String, Object>> models = new ArrayList<>();

            for (Object rawItem : dataList) {
                if (!(rawItem instanceof Map<?, ?> rawMap)) {
                    continue;
                }

                Map<String, Object> item = (Map<String, Object>) rawMap;
                HashMap<String, Object> copy = new HashMap<>(item);

                // Add model size
                String modelId = String.valueOf(copy.getOrDefault("id", copy.getOrDefault("name", "unknown-model")));
                Map<String, Object> sizeInfo = llamaServer.getModelSize(modelId);
                copy.put("size", sizeInfo.get("size"));
                copy.put("sizeBytes", sizeInfo.get("sizeBytes"));

                models.add(copy);
            }

            return models;
        } catch (Exception e) {
            return List.of();
        }
    }
}
