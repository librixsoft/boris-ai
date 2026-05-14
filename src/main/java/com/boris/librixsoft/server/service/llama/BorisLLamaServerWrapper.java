package com.boris.librixsoft.server.service.llama;

import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.server.service.BorisAppBrandingPrinter;
import com.boris.librixsoft.server.service.LlamaServerDownloadService;
import com.boris.librixsoft.server.service.llama.jna.LlamaInstance;
import com.boris.librixsoft.server.service.llama.jna.LlamaLibrary;
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
 * Solo existe un modelo activo a la vez. Al cargar otro modelo, el anterior se descarga,
 * luego el nuevo se carga y completa su warmup antes de quedar disponible para chat.
 * </p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BorisLLamaServerWrapper {

    private final BorisProperties properties;
    private final BorisAppBrandingPrinter appBrandingPrinter;
    private final LlamaServerDownloadService downloadService;
    
    private final LlamaInstanceStarter instanceStarter;
    private final LlamaModelLoader modelLoader;
    private final LlamaModelUnloader modelUnloader;
    private final LlamaModelClearer modelClearer;
    private final LlamaModelLister modelLister;
    private final LlamaServerPropsProvider propsProvider;
    private final LlamaModelSizeProvider sizeProvider;
    private final LlamaModelConfigResolver configResolver;
    private final JnaLlamaChatModel jnaChatModel;

    private final ExecutorService executor = Executors.newCachedThreadPool();

    /** Instancia nativa actualmente activa. */
    private LlamaInstance defaultInstance;

    /** ID del modelo actualmente cargado en {@code defaultInstance}. */
    private String activeModelId;

    private boolean isReady = false;
    private boolean enableLlamaCppLogs = false;

     public boolean isReady() {
         return isReady && defaultInstance != null;
     }

     /**
      * Returns true if the CUDA graph warmup has completed for the current active model.
      * If no model is loaded, returns true (no warmup needed).
      */
     public boolean isWarmupComplete() {
         return jnaChatModel.isWarmupComplete();
     }

     public String getWarmupStatusMessage() {
         return jnaChatModel.getWarmupStatusMessage();
     }

    /**
     * Blocks until the server is ready (preloaded models finished loading).
     * Returns immediately if already ready.
     */
    public void waitForReady() throws InterruptedException {
        while (!isReady) {
            Thread.sleep(100);
        }
    }

    public String getActiveModelId() {
        return activeModelId;
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
            this.isReady = true;
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
            this.defaultInstance = defaultFuture.join();
            this.isReady = true;
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
        return instanceStarter.startInstance(port, executor, enableLlamaCppLogs);
    }

    public String listModels() throws IOException {
        String loadedPath = null;
        if (defaultInstance != null && defaultInstance.isAlive()) {
            loadedPath = defaultInstance.getModelPath();
        }
        return modelLister.listModels(loadedPath);
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
     * El modelo cargado debe completar su warmup antes de quedar listo para chat.
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

        if (this.defaultInstance != null) {
            log.info("📤 Unloading current model before loading new one: {}", activeModelId);
            
            // Desvincular primero del motor de inferencia para evitar uso concurrente
            // setActiveModel internamente: interrumpe warmup anterior, limpia KV,
            // sessionTokens, batch y sampler cacheados
            this.jnaChatModel.setActiveModel(null);
            
            this.defaultInstance.clearKvCache();
            this.defaultInstance.close();
            this.defaultInstance = null;
            this.activeModelId = null;
        }

        var model = modelLoader.loadModelWithParams(id, modelName, contextSize, threads,
                gpuLayers, batchSize, temperature, maxTokens, parallel);

        this.defaultInstance = model;
        this.activeModelId = id;

        // CRÍTICO: registrar la nueva instancia ANTES del warmup
        // setActiveModel resetea kvCachePosition=0 y limpia sessionTokens
        // para que no queden tokens sucios del modelo anterior
        log.info("[WARMUP] Llamando setActiveModel(model) para modelo {}...", id);
        this.jnaChatModel.setActiveModel(model);
        log.info("[WARMUP] setActiveModel completado. warmupComplete={}", this.jnaChatModel.isWarmupComplete());

        // Lanzar warmup y hacer join() para que el CUDA graph esté compilado
        // ANTES de que cualquier mensaje del usuario llegue.
        // Sin este join, el primer mensaje real dispara el warmup interno
        // de llama.cpp (el "CUDA graph warmup complete" que ves en logs) → delay.
        try {
            log.info("[WARMUP] Iniciando warmupCudaGraph() para modelo {}...", id);
            Thread warmupThread = this.jnaChatModel.warmupCudaGraph();
            log.info("[WARMUP] Warmup thread iniciado. Thread={}, isAlive={}, Esperando compilación del CUDA graph para modelo {}...", warmupThread, warmupThread.isAlive(), id);
            
            // Esperar a que el warmup termine
            warmupThread.join(120_000); // 2 min timeout
            
            log.info("[WARMUP] Join completado. Thread isAlive={}, warmupComplete={}", 
                warmupThread.isAlive(), this.jnaChatModel.isWarmupComplete());
            
            if (warmupThread.isAlive()) {
                log.warn("[WARMUP] ⚠ Timeout esperando warmup del modelo {}, continuando de todas formas", id);
            } else if (this.jnaChatModel.isWarmupComplete()) {
                log.info("[WARMUP] ✅ CUDA graph compilado OK para modelo {}", id);
            } else {
                log.warn("[WARMUP] ⚠ Warmup no completó exitosamente para modelo {}. warmupComplete={}", 
                    id, this.jnaChatModel.isWarmupComplete());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[WARMUP] ⚠ Join del warmup interrumpido para modelo {}", id);
        }

        // En este punto sessionTokens está limpio (seteado a 0 por setActiveModel
        // y el warmup hizo clearKvCache al finalizar), así que el primer mensaje
        // real no encuentra tokens sucios y el KV reuse arranca desde cero limpio.
        log.info("[WARMUP] Modelo {} listo y calentado antes de activarlo para chat. warmupComplete={}", 
            id, this.jnaChatModel.isWarmupComplete());
    }

    public void unloadModel(String modelName) throws IOException {
        modelUnloader.unloadModel(modelName);

        String normalizedName = modelName != null ? modelName.replaceFirst("\\.gguf$", "") : null;
        boolean matchesActiveId = activeModelId != null && (activeModelId.equals(modelName) || activeModelId.equals(normalizedName));
        boolean matchesDefaultPath = defaultInstance != null && defaultInstance.getModelPath() != null &&
                new java.io.File(defaultInstance.getModelPath()).getName().equalsIgnoreCase(modelName);

        if (matchesActiveId || matchesDefaultPath) {
            log.info("📤 Unloading active model: {}", modelName);
            if (defaultInstance != null) {
                defaultInstance.clearKvCache();
                defaultInstance.close();
                defaultInstance = null;
            }
            activeModelId = null;
            this.jnaChatModel.setActiveModel(null);
        }
    }

    public void clearModel(String modelId) throws IOException {
        log.info("🧹 Clearing active chat state without touching warmup/model lifecycle");
        jnaChatModel.resetKvCache();
        if (defaultInstance != null) {
            defaultInstance.clearKvCache();
        }
        modelClearer.clearModel(modelId);
    }

    public void clearModel() throws IOException {
        log.info("🧹 Clearing all chat state for loaded model(s) without unloading");
        jnaChatModel.resetKvCache();
        if (defaultInstance != null) {
            defaultInstance.clearKvCache();
        }
        modelClearer.clearModel();
    }

    /**
     * Extreme cleaning: unloads everything from VRAM and resets all native pointers.
     */
    public void forceUnloadAll() {
        log.info("🚨 [EXTREME CLEAN] Unloading all models and releasing all VRAM...");
        if (defaultInstance != null) {
            defaultInstance.close();
            defaultInstance = null;
        }
        activeModelId = null;
        isReady = false;
        
        // Clear cached resources in inference engine (batch, samplers)
        jnaChatModel.freeCachedResources();
        jnaChatModel.setActiveModel(null);
        
        log.info("✅ [EXTREME CLEAN] VRAM cleared and native instances destroyed.");
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