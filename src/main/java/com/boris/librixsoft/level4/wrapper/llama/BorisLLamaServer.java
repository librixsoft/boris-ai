package com.boris.librixsoft.level4.wrapper.llama;

import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.level3.domain.service.BorisAppBrandingPrinter;
import com.boris.librixsoft.level3.domain.service.LlamaServerDownloadService;
import com.boris.librixsoft.level5.nativeCpp.jna.LlamaInstance;
import com.boris.librixsoft.level5.nativeCpp.jna.LlamaLibrary;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Wrapper principal del motor nativo llama.cpp para Boris.
 *
 * <h2>Gestión manual de memoria nativa</h2>
 * <p>
 * Esta clase cruza la frontera Java → C++ mediante JNA. El garbage collector de Java
 * <b>no puede liberar</b> los recursos nativos (model_ptr, ctx_ptr) que llama.cpp
 * aloca en heap y VRAM. Por eso toda liberación debe hacerse explícitamente
 * llamando a {@link LlamaInstance#close()}.
 * </p>
 *
 * <h2>Ciclo de vida del modelo activo</h2>
 * <p>
 * Solo existe un modelo activo a la vez. Al cargar otro modelo, el anterior se descarga.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BorisLLamaServer {

    private final BorisProperties properties;
    private final BorisAppBrandingPrinter appBrandingPrinter;
    private final LlamaServerDownloadService downloadService;
    
    private final LlamaServerState serverState;
    private final LlamaInstanceStarter instanceStarter;
    private final LlamaModelLoader modelLoader;
    private final LlamaModelUnloader modelUnloader;
    private final LlamaModelClearer modelClearer;
    private final LlamaModelLister modelLister;
    private final LlamaServerPropsProvider propsProvider;
    private final LlamaModelSizeProvider sizeProvider;
    private final LlamaModelConfigResolver configResolver;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    public boolean isReady() {
        LlamaInstance defaultInstance = serverState.getDefaultInstance();
        boolean readyState = serverState.isReady() && defaultInstance != null;
        System.out.println("🔍 [Wrapper] isReady() -> " + readyState + " (isReady flag: " + serverState.isReady() + ", defaultInstance: " + (defaultInstance != null ? "alive" : "null") + ")");
        return readyState;
    }

    /**
     * Blocks until the server is ready (preloaded models finished loading).
     * Returns immediately if already ready.
     */
    public void waitForReady() throws InterruptedException {
        System.out.println("⏳ [Wrapper] waitForReady() called. isReady=" + serverState.isReady());
        while (!serverState.isReady()) {
            Thread.sleep(100);
        }
        System.out.println("✅ [Wrapper] waitForReady() completed. isReady=" + serverState.isReady());
    }

    public String getActiveModelId() {
        return serverState.getActiveModelId();
    }

    public String getActiveModelName() {
        String activeModelId = serverState.getActiveModelId();
        if (activeModelId == null) return null;
        var config = configResolver.getModelConfig(activeModelId);
        return config != null ? config.getName() : activeModelId;
    }

    public BorisProperties.ModelConfig resolveConfig(String modelId, int indexFallback) {
        String targetId = (modelId == null || modelId.isBlank()) ? serverState.getActiveModelId() : modelId;

        if (targetId != null && !targetId.isBlank()) {
            BorisProperties.ModelConfig cfg = configResolver.getModelConfig(targetId);
            if (cfg != null) return cfg;

            BorisProperties.ModelConfig dynamicCfg = new BorisProperties.ModelConfig();
            dynamicCfg.setId(targetId);
            dynamicCfg.setName(targetId);
            return dynamicCfg;
        }

        var preloadModels = properties.getPreloadModels();
        if (preloadModels == null || preloadModels.size() <= indexFallback) {
            if (preloadModels != null && !preloadModels.isEmpty()) return preloadModels.get(0);
            BorisProperties.ModelConfig fallback = new BorisProperties.ModelConfig();
            fallback.setId("default-model");
            fallback.setName("default-model.gguf");
            return fallback;
        }
        return preloadModels.get(indexFallback);
    }

    @PostConstruct
    public void init() {
        startServer();
        registerShutdownHook();
    }

    /**
     * Registra un shutdown hook de JVM para liberar recursos nativos al cerrar.
     * <p>
     * Crítico: si la JVM termina sin llamar a {@link LlamaInstance#close()},
     * los punteros nativos quedan huérfanos y la VRAM no se libera hasta
     * que el proceso muere completamente.
     * </p>
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("[⚠] Shutdown hook triggered - releasing native resources...");
            try {
                forceUnloadAll();
                LlamaLibrary.get().llama_backend_free();
            } catch (Throwable t) {
                log.warn("Error in shutdown hook: {}", t.getMessage());
            }
            executor.shutdownNow();
            log.info("[✔] Shutdown complete");
        }, "llama-shutdown-hook"));
    }

    public CompletableFuture<Void> startServer() {
        // Check if llama.cpp libraries are installed
        if (!downloadService.isInstalled()) {
            log.info("[⏸️] Llama.cpp libraries not detected. Server starting without native backend.");
            log.info("[💡] Download libraries from settings to install llama.cpp environment.");
            serverState.setReady(true);
            appBrandingPrinter.printSplash();
            appBrandingPrinter.printSuccessCrates();
            return CompletableFuture.completedFuture(null);
        }

        // Configure library path from application.yml before loading native libraries
        String llamaServerPath = properties.getLlamaServerPath();
        if (llamaServerPath != null && !llamaServerPath.isEmpty()) {
            LlamaLibrary.setLibraryPath(llamaServerPath);
            log.info("[*] Library path configured from application.yml: {}", llamaServerPath);
        }

        appBrandingPrinter.printSplash();

        CompletableFuture<LlamaInstance> defaultFuture = startInstance(properties.getPort());
        log.info("[*] Instance starter triggered, waiting for completion...");

        return CompletableFuture.allOf(defaultFuture).thenRun(() -> {
            log.info("[*] Instance starter completed, joining result...");
            serverState.setDefaultInstance(defaultFuture.join());
            serverState.setReady(true);
            log.info("[✔] Boris is ready. Checking for models to pre-load...");
            appBrandingPrinter.printSuccessCrates();

            reloadPreloadedModels();
        }).exceptionally(ex -> {
            log.error("[!] FATAL: Failed to start native instance: {}", ex.getMessage(), ex);
            return null;
        });
    }

    public void reloadPreloadedModels() {
        if (properties.getPreloadModels() != null && !properties.getPreloadModels().isEmpty()) {
            BorisProperties.ModelConfig modelConfig = properties.getPreloadModels().get(0);
            try {
                log.info("[*] Pre-loading configured model (Index 1): {} (ID: {})",
                        modelConfig.getName(), modelConfig.getId());
                loadModelWithParams(modelConfig.getId(), modelConfig.getName(),
                        modelConfig.getContextSize(), modelConfig.getThreads(),
                        modelConfig.getGpuLayers(), modelConfig.getBatchSize(),
                        modelConfig.getTemperature(), modelConfig.getMaxTokens(),
                        modelConfig.getParallel());
                log.info("[✔] Model {} (ID: {}) loaded successfully.", modelConfig.getName(), modelConfig.getId());
            } catch (Exception e) {
                log.error("[!] CRITICAL: Could not pre-load model {}: {}", modelConfig.getName(), e.getMessage(), e);
            }
        } else {
            log.info("[*] No models to pre-load configured in application.yml.");
        }
    }

    public BorisProperties.ModelConfig getModelConfig(String id) {
        return configResolver.getModelConfig(id);
    }

    public CompletableFuture<LlamaInstance> startInstance(int port) {
        return instanceStarter.startInstance(port, executor, serverState.isEnableLlamaCppLogs());
    }

    public String listModels() throws IOException {
        return modelLister.listModels();
    }

    public String getServerProps() throws IOException {
        return propsProvider.getServerProps();
    }

    public Map<String, Object> getModelSize(String modelName) {
        return sizeProvider.getModelSize(modelName);
    }

    /**
     * Carga un modelo nativo con los parámetros dados y lo establece como activo.
     *
     * Si ya hay un modelo activo, se descarga por completo antes de cargar el nuevo.
     *
     * @param id          ID lógico del modelo activo
     * @param modelName   nombre del archivo .gguf
     * @param contextSize tamaño del contexto KV
     * @param threads     hilos CPU para inferencia
     * @param gpuLayers   capas a offloadear a GPU
     * @param batchSize   tamaño de batch
     * @param temperature temperatura de sampling
     * @param maxTokens   máximo de tokens a generar
     * @param parallel    secuencias paralelas
     * @throws IOException si llama.cpp no puede cargar el modelo
     */
    public void loadModelWithParams(String id, String modelName, Integer contextSize, Integer threads,
                                    Integer gpuLayers, Integer batchSize, Double temperature, Integer maxTokens, Integer parallel) throws IOException {
        modelLoader.loadModelWithParams(id, modelName, contextSize, threads, gpuLayers, batchSize, temperature, maxTokens, parallel);
    }

    public void unloadModel(String modelName) throws IOException {
        modelUnloader.unloadModel(modelName);
    }

    public void clearModel() throws IOException {
        modelClearer.clearModel();
    }

    /**
     * Extreme cleaning: unloads everything from VRAM and resets all native pointers.
     */
    public void forceUnloadAll() {
        modelUnloader.forceUnloadAll();
    }

    /**
     * Libera todos los recursos nativos al destruir el bean (shutdown de Spring).
     * <p>
     * Complementa al shutdown hook para el caso de redeployment en caliente.
     * Ambos mecanismos son necesarios porque ninguno está garantizado en todos
     * los escenarios de cierre.
     * </p>
     */
    @PreDestroy
    public void stopServer() {
        log.info("[*] Stopping native llama engine (PreDestroy)...");
        
        forceUnloadAll();
        
        try {
            log.info("[*] Releasing global llama backend...");
            LlamaLibrary.get().llama_backend_free();
        } catch (Throwable t) {
            log.warn("Error releasing llama backend: {}", t.getMessage());
        }

        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
        log.info("[✔] Native engine stopped successfully");
    }
}