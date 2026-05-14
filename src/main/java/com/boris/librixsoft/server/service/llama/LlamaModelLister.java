package com.boris.librixsoft.server.service.llama;

import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.util.PathResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class LlamaModelLister {

    private final BorisProperties properties;

    public String listModels(String loadedModelPath) throws IOException {
        // Uso de PathResolver para asegurar la ruta en .boris y crearla si no existe
        String dirPath = PathResolver.resolveAndCreate(properties.getModelsDir(), true);
        
        // Si sigue vacío por alguna razón, usamos fallback (pero PathResolver debería manejarlo)
        if (dirPath == null || dirPath.isBlank()) {
            dirPath = "vendor/llama.cpp/models"; 
        }
        
        // Clean up quotes or weird characters if present
        dirPath = dirPath.replace("\"", "").replace("'", "").trim();
        
        File dir = new File(dirPath);

        System.out.println("[DEBUG] listModels Cleaned Dir: " + dir.getAbsolutePath().replace('\\', '/') + " exists? " + dir.exists() + " isDir? " + dir.isDirectory());
        if (!dir.exists() || !dir.isDirectory()) {
            return "{\"data\":[]}";
        }
        File[] files = dir.listFiles((d, name) -> name.toLowerCase().endsWith(".gguf"));
        if (files == null || files.length == 0) return "{\"data\":[]}";

        String jsonArray = Arrays.stream(files)
                .map(f -> {
                    String normalizedPath = f.getAbsolutePath().replace('\\', '/');
                    boolean isLoaded = loadedModelPath != null && (loadedModelPath.equals(normalizedPath) || loadedModelPath.endsWith(f.getName()));
                    String status = isLoaded ? "loaded" : "available";
                    return String.format("{\"id\":\"%s\", \"name\":\"%s\", \"status\":\"%s\", \"owned_by\":\"local\", \"object\":\"model\"}", 
                            f.getName(), f.getName(), status);
                })
                .collect(Collectors.joining(",", "[", "]"));
        return "{\"data\":" + jsonArray + "}";
    }

    public String listModels() throws IOException {
        return listModels(null);
    }
}
