package com.boris.librixsoft.server.controller;

import com.boris.librixsoft.server.service.llama.BorisLLamaServerWrapper;
import com.boris.librixsoft.server.dto.ApiResponse;
import com.boris.librixsoft.server.service.HardwareService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/hardware")
@RequiredArgsConstructor
public class HardwareController {

    private final HardwareService hardwareService;
    private final BorisLLamaServerWrapper llamaServer;

    @GetMapping
    public ResponseEntity<ApiResponse<Map<String, Object>>> getHardware() {
        return ResponseEntity.ok(ApiResponse.success(hardwareService.getGpuInfo()));
    }

    @GetMapping(value = "/memory", produces = "application/json")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getGpuMemoryUsage() {
        try {
            // Get total VRAM from system info
            Map<String, Object> gpuInfo = hardwareService.getGpuInfo();
            String vramStr = (String) gpuInfo.get("vram");
            Long totalVram = 0L;
            
            if (vramStr != null && !vramStr.equals("N/A") && !vramStr.equals("Shared/Unknown")) {
                try {
                    double vramGb = Double.parseDouble(vramStr.replace(" GB", "").trim());
                    totalVram = (long) (vramGb * 1024 * 1024 * 1024);
                } catch (Exception e) {
                    // Ignore parse errors
                }
            }
            
            // Get loaded models and sum their sizes
            String modelsJson = llamaServer.listModels();
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> modelsMap = mapper.readValue(modelsJson, Map.class);
            Object data = modelsMap.get("data");
            
            long loadedModelsSize = 0L;
            if (data instanceof List<?>) {
                List<?> dataList = (List<?>) data;
                for (Object rawItem : dataList) {
                    if (!(rawItem instanceof Map<?, ?> rawMap)) continue;
                    
                    Map<String, Object> item = (Map<String, Object>) rawMap;
                    Object status = item.get("status");
                    boolean isLoaded;
                    if (status instanceof Map<?, ?> statusMap) {
                        Object value = statusMap.get("value");
                        isLoaded = value != null && "loaded".equalsIgnoreCase(String.valueOf(value));
                    } else {
                        isLoaded = status != null && "loaded".equalsIgnoreCase(String.valueOf(status));
                    }
                    
                    if (isLoaded) {
                        String modelId = String.valueOf(item.getOrDefault("id", item.getOrDefault("name", "unknown-model")));
                        Map<String, Object> sizeInfo = llamaServer.getModelSize(modelId);
                        Long sizeBytes = (Long) sizeInfo.get("sizeBytes");
                        if (sizeBytes != null && sizeBytes > 0) {
                            loadedModelsSize += sizeBytes;
                        }
                    }
                }
            }
            
            // Calculate remaining VRAM
            long remainingVram = totalVram - loadedModelsSize;
            
            Map<String, Object> memoryData = Map.of(
                "totalVramBytes", totalVram,
                "usedVramBytes", loadedModelsSize,
                "remainingVramBytes", remainingVram,
                "totalVram", formatBytes(totalVram),
                "usedVram", formatBytes(loadedModelsSize),
                "remainingVram", formatBytes(remainingVram)
            );
            return ResponseEntity.ok(ApiResponse.success(memoryData));
        } catch (Exception e) {
            Map<String, Object> errorData = Map.of(
                "totalVramBytes", 0L,
                "usedVramBytes", 0L,
                "remainingVramBytes", 0L,
                "totalVram", "N/A",
                "usedVram", "N/A",
                "remainingVram", "N/A"
            );
            return ResponseEntity.ok(ApiResponse.success(errorData));
        }
    }
    
    private String formatBytes(long bytes) {
        if (bytes == 0) return "N/A";
        double gb = bytes / (1024.0 * 1024.0 * 1024.0);
        if (gb >= 1.0) {
            return String.format("%.2f GB", gb);
        }
        double mb = bytes / (1024.0 * 1024.0);
        return String.format("%.2f MB", mb);
    }
}
