package com.boris.librixsoft.server.service.llama;

import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.util.PathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlamaModelSizeProvider {

    private final BorisProperties properties;

    public Map<String, Object> getModelSize(String modelName) {
        try {
            // Uso de PathResolver para asegurar la ruta en .boris y crearla si no existe
            String modelsDir = PathResolver.resolveAndCreate(properties.getModelsDir(), true);
            
            if (modelsDir == null || modelsDir.isBlank()) {
                modelsDir = "vendor/llama.cpp/models";
            }
            modelsDir = modelsDir.replace("\"", "").replace("'", "").trim();
            
            Path modelPath = Paths.get(modelsDir, modelName);

            if (!Files.exists(modelPath)) {
                java.io.File searchDir = new java.io.File(modelsDir);
                java.io.File[] files = searchDir.listFiles(
                        (d, name) -> name.toLowerCase().contains(modelName.toLowerCase().replace(".gguf", "")) ||
                                 modelName.toLowerCase().contains(name.toLowerCase().replace(".gguf", "")));

                if (files != null && files.length > 0) {
                    modelPath = files[0].toPath();
                } else {
                    log.warn("Model file not found for: {}", modelName);
                    return Map.of("size", "N/A", "sizeBytes", 0L);
                }
            }

            long sizeBytes = Files.size(modelPath);
            double sizeGB = sizeBytes / (1024.0 * 1024.0 * 1024.0);
            double sizeMB = sizeBytes / (1024.0 * 1024.0);
            String sizeStr = sizeGB >= 1.0 ? String.format("%.2f GB", sizeGB) : String.format("%.2f MB", sizeMB);

            return Map.of("size", sizeStr, "sizeBytes", sizeBytes);
        } catch (Exception e) {
            log.error("Error getting model size for {}: {}", modelName, e.getMessage());
            return Map.of("size", "N/A", "sizeBytes", 0L);
        }
    }
}
