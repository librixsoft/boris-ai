package com.boris.librixsoft.server.controller;

import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.server.dto.ApiResponse;
import com.boris.librixsoft.util.PathResolver;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/boris/v1/config")
@RequiredArgsConstructor
public class SettingsController {

    private final BorisProperties borisProperties;

    @GetMapping("/settings")
    public ApiResponse<Map<String, String>> getSettings() {
        Map<String, String> settings = new HashMap<>();
        // Return raw relative paths (e.g., "/vendor/models") - frontend will resolve them
        settings.put("llamaServerPath", borisProperties.getLlamaServerPath());
        settings.put("llamaServerCore", borisProperties.getLlamaServerCore());
        settings.put("modelsDir", borisProperties.getModelsDir());
        settings.put("workspacePrefix", borisProperties.getWorkspacePrefix());
        settings.put("userHome", PathResolver.getUserHome());
        return ApiResponse.success(settings);
    }

    @PostMapping("/settings")
    public ApiResponse<Map<String, String>> updateSettings(@RequestBody Map<String, String> newSettings) {
        String userHome = PathResolver.getUserHome();
        
        if (newSettings.containsKey("llamaServerPath")) {
            borisProperties.setLlamaServerPath(relativizePath(newSettings.get("llamaServerPath"), userHome));
        }
        if (newSettings.containsKey("llamaServerCore")) {
            borisProperties.setLlamaServerCore(newSettings.get("llamaServerCore"));
        }
        if (newSettings.containsKey("modelsDir")) {
            borisProperties.setModelsDir(relativizePath(newSettings.get("modelsDir"), userHome));
        }
        if (newSettings.containsKey("workspacePrefix")) {
            borisProperties.setWorkspacePrefix(relativizePath(newSettings.get("workspacePrefix"), userHome));
        }
        return ApiResponse.success("Settings updated successfully");
    }
    
    /**
     * Convierte una ruta absoluta a formato relativo con prefijo '/' si está bajo user.home.
     * Ej: C:\Users\name\vendor\models -> /vendor/models
     */
    private String relativizePath(String path, String userHome) {
        if (path == null || path.isBlank()) return path;
        // Normalize paths for comparison (handle both \ and /)
        String normalizedPath = path.replace("\\", "/");
        String normalizedHome = userHome.replace("\\", "/");
        
        if (normalizedPath.startsWith(normalizedHome)) {
            // Strip user.home and add / prefix
            String relative = normalizedPath.substring(normalizedHome.length());
            if (relative.startsWith("/")) {
                return relative;
            } else {
                return "/" + relative;
            }
        }
        // If not under user.home, return as-is
        return path;
    }

    @GetMapping("/browse-folder")
    public ApiResponse<Map<String, String>> browseFolder(@RequestParam(required = false) String initialPath) {
        String path = PathResolver.browse(true, initialPath);
        return ApiResponse.success(Map.of("path", path));
    }

    @GetMapping("/browse-file")
    public ApiResponse<Map<String, String>> browseFile(@RequestParam(required = false) String initialPath) {
        String path = PathResolver.browse(false, initialPath);
        return ApiResponse.success(Map.of("path", path));
    }
}
