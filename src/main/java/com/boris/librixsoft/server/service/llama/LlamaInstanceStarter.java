package com.boris.librixsoft.server.service.llama;

import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.server.service.LlamaServerDownloadService;
import com.boris.librixsoft.server.service.llama.jna.LlamaInstance;
import com.boris.librixsoft.server.service.llama.jna.LlamaLibrary;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlamaInstanceStarter {

    private final BorisProperties properties;
    private final LlamaServerDownloadService downloadService;

    public CompletableFuture<LlamaInstance> startInstance(int port, ExecutorService executor, boolean enableLlamaCppLogs) {
        return CompletableFuture.supplyAsync(() -> {
            if (!downloadService.isInstalled()) {
                log.info("⏸️ Llama.cpp libraries not detected. Backend not loaded (download from settings to install).");
                return new LlamaInstance(null, null, "disabled");
            }
            log.info("🚀 Initializing native llama.cpp backend (Pure JNA)...");
            LlamaLibrary.get().llama_backend_init();
            LlamaLibrary.GgmlLibrary.get().ggml_backend_load_all();
            return new LlamaInstance(null, null, "native-backend");
        }, executor);
    }
}
