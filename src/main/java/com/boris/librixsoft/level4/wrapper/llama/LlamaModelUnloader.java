package com.boris.librixsoft.level4.wrapper.llama;

import com.boris.librixsoft.level5.nativeCpp.jna.LlamaInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlamaModelUnloader {

    private final LlamaServerState serverState;
    private final LlamaChatService llamaChatService;

    public void unloadModel(String modelName) throws IOException {
        log.info("[*] Native model unload requested for: {}.", modelName);

        String activeModelId = serverState.getActiveModelId();
        LlamaInstance defaultInstance = serverState.getDefaultInstance();

        String normalizedName = modelName != null ? modelName.replaceFirst("\\.gguf$", "") : null;
        boolean matchesActiveId = activeModelId != null && (activeModelId.equals(modelName) || activeModelId.equals(normalizedName));
        boolean matchesDefaultPath = defaultInstance != null && defaultInstance.getModelPath() != null &&
                new java.io.File(defaultInstance.getModelPath()).getName().equalsIgnoreCase(modelName);

        if (matchesActiveId || matchesDefaultPath) {
            log.info("📤 Unloading active model: {}", modelName);
            if (defaultInstance != null) {
                defaultInstance.clearKvCache();
                defaultInstance.close();
                serverState.setDefaultInstance(null);
            }
            serverState.setActiveModelId(null);
            this.llamaChatService.setActiveModel(null);
        }
    }

    /**
     * Extreme cleaning: unloads everything from VRAM and resets all native pointers.
     */
    public void forceUnloadAll() {
        log.info("🚨 [EXTREME CLEAN] Unloading all models and releasing all VRAM...");
        LlamaInstance defaultInstance = serverState.getDefaultInstance();
        if (defaultInstance != null) {
            defaultInstance.close();
            serverState.setDefaultInstance(null);
        }
        serverState.setActiveModelId(null);
        serverState.setReady(false);

        // Clear cached resources in inference engine (batch, samplers)
        llamaChatService.freeCachedResources();
        llamaChatService.setActiveModel(null);

        log.info("✅ [EXTREME CLEAN] VRAM cleared and native instances destroyed.");
    }
}
