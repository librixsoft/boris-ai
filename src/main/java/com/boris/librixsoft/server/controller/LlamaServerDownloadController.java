package com.boris.librixsoft.server.controller;

import com.boris.librixsoft.server.dto.ApiResponse;
import com.boris.librixsoft.server.service.LlamaServerDownloadService;
import com.boris.librixsoft.server.service.llama.BorisLLamaServerWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/boris/v1/llama-server")
@RequiredArgsConstructor
public class LlamaServerDownloadController {

    private final LlamaServerDownloadService downloadService;
    private final BorisLLamaServerWrapper serverWrapper;

    @PostMapping("/download")
    public ApiResponse<Map<String, String>> download() {
        try {
            downloadService.downloadAndExtract();
            log.info("Llama libraries downloaded successfully. Auto-starting server...");
            
            // Auto-start the llama server instance after successful download
            serverWrapper.startServer().get(); // Wait for async completion
            
            log.info("Server is ready. Model loaded successfully.");
            return ApiResponse.success("Llama server downloaded and model loaded successfully");
        } catch (Exception e) {
            log.error("Failed to download llama server", e);
            return ApiResponse.error(e.getMessage());
        }
    }

    @GetMapping("/status")
    public ApiResponse<Map<String, Object>> status() {
        return ApiResponse.success(Map.of("installed", downloadService.isInstalled()));
    }

    @GetMapping("/ready")
    public ApiResponse<Map<String, String>> ready() throws InterruptedException {
        serverWrapper.waitForReady();
        return ApiResponse.ok("ready");
    }
}
