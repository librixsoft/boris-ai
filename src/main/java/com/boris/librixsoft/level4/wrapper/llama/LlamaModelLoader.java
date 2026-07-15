package com.boris.librixsoft.level4.wrapper.llama;

import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.level5.nativeCpp.jna.LlamaInstance;
import com.boris.librixsoft.level5.nativeCpp.jna.LlamaLibrary;
import com.boris.librixsoft.util.PathResolver;
import com.sun.jna.Pointer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlamaModelLoader {

    private final BorisProperties properties;
    private final LlamaServerState serverState;
    private final LlamaChatService llamaChatService;
    private final LlamaWorkerPool workerPool;

    private static final String GREEN  = "\u001B[32m";
    private static final String RESET  = "\u001B[0m";

    public void loadModelWithParams(String id, String modelName, Integer contextSize, Integer threads,
                                    Integer gpuLayers, Integer batchSize, Double temperature,
                                    Integer maxTokens, Integer parallel) throws IOException {

        System.out.println("🔄 [Loader] loadModelWithParams START: id=" + id + ", model=" + modelName);

        LlamaInstance existingInstance = serverState.getDefaultInstance();
        if (existingInstance != null) {
            log.info("📤 Unloading current model before loading new one: {}", serverState.getActiveModelId());
            System.out.println("🔄 [Loader] Unloading existing model...");
            // Desvincular primero del motor de inferencia para evitar uso concurrente
            workerPool.close();
            this.llamaChatService.setActiveModel(null);
            System.out.println("🔄 [Loader] setActiveModel(null) done");

            existingInstance.clearKvCache();
            System.out.println("🔄 [Loader] clearKvCache done");

            existingInstance.close();
            System.out.println("🔄 [Loader] close() done");

            serverState.setDefaultInstance(null);
            serverState.setActiveModelId(null);
            System.out.println("🔄 [Loader] Previous model unloaded completely");
        }

        log.info("[*] Loading model {} via Pure JNA...", modelName);
        System.out.println("📥 [Loader] Loading model: " + modelName + " with context=" + contextSize + ", threads=" + threads + ", gpuLayers=" + gpuLayers);

        String modelsDir = PathResolver.resolveAndCreate(properties.getModelsDir(), true);
        String nameWithExt = modelName.endsWith(".gguf") ? modelName : modelName + ".gguf";
        String modelPath = java.nio.file.Paths.get(modelsDir, nameWithExt)
                .toAbsolutePath()
                .normalize()
                .toString()
                .replace('\\', '/');

        System.out.println("📥 [Loader] Model path resolved: " + modelPath);
        System.out.println("📥 [Loader] Checking file exists...");
        java.io.File f = new java.io.File(modelPath);
        if (!f.exists()) {
            System.out.println("❌ [Loader] Model file NOT found: " + modelPath);
            throw new IOException("Model file not found: " + modelPath);
        }
        System.out.println("✅ [Loader] Model file exists, size: " + f.length() + " bytes");

        try {
            // Model params
            System.out.println("📥 [Loader] Getting default model params...");
            LlamaLibrary.llama_model_params.ByValue mparams = LlamaLibrary.get().llama_model_default_params();

            log.info("[DEBUG] Default params → mmap={}, mlock={}, gpu_layers={}",
                    mparams.use_mmap, mparams.use_mlock, mparams.n_gpu_layers);

            mparams.n_gpu_layers = gpuLayers != null ? gpuLayers : 0;
            
            // Safety check: if GPU layers requested but no GPU backend available, force CPU-only
            if (mparams.n_gpu_layers > 0) {
                try {
                    // Try Vulkan first (most common for Windows), fallback to CUDA
                    try {
                        LlamaLibrary.GgmlLibrary.get().ggml_backend_load("vulkan");
                        log.info("[✔] Vulkan backend loaded successfully");
                    } catch (Throwable vulkanErr) {
                        log.debug("[!] Vulkan backend not available, trying CUDA...");
                        try {
                            LlamaLibrary.GgmlLibrary.get().ggml_backend_load("cuda");
                            log.info("[✔] CUDA backend loaded successfully");
                        } catch (Throwable cudaErr) {
                            log.warn("[!] No GPU backend available (Vulkan/CUDA failed), forcing CPU-only mode (gpu_layers=0)");
                            mparams.n_gpu_layers = 0;
                        }
                    }
                } catch (Throwable t) {
                    log.warn("[!] GPU backend check failed, forcing CPU-only mode (gpu_layers=0). Error: {}", t.getMessage());
                    mparams.n_gpu_layers = 0;
                }
            }
            mparams.use_mmap     = (byte) 1;
            mparams.use_mlock    = (byte) 0;

            log.info("[DEBUG] Calling llama_model_load_from_file with: path={}, gpu_layers={}, mmap={}, mlock={}",
                    modelPath, mparams.n_gpu_layers, mparams.use_mmap, mparams.use_mlock);

            System.out.println("📥 [Loader] About to call llama_model_load_from_file (may take several seconds)...");
            long t0 = System.currentTimeMillis();
            Pointer modelPtr = LlamaLibrary.get().llama_model_load_from_file(modelPath, mparams);
            long t1 = System.currentTimeMillis();
            System.out.println("✅ [Loader] llama_model_load_from_file returned after " + (t1 - t0) + " ms. modelPtr=" + (modelPtr != null ? "non-null" : "null"));
            if (modelPtr == null) {
                throw new IOException("Failed to load model from " + modelPath);
            }

            // Context params
            System.out.println("📥 [Loader] Getting default context params...");
            LlamaLibrary.llama_context_params.ByValue cparams = LlamaLibrary.get().llama_context_default_params();
            int resolvedBatch = batchSize != null ? batchSize : 512;
            cparams.n_ctx           = contextSize != null ? contextSize : properties.getContextSize();
            cparams.n_batch         = resolvedBatch;
            cparams.n_ubatch        = Math.min(resolvedBatch, 2048);
            cparams.n_threads       = threads     != null ? threads     : properties.getThreads();
            cparams.n_threads_batch = threads     != null ? threads     : properties.getThreads();
            cparams.flash_attn_type = 1;
            cparams.offload_kqv     = (byte) 1;
            cparams.type_k          = 8; // GGML_TYPE_Q8_0
            cparams.type_v          = 8; // GGML_TYPE_Q8_0

            log.info("[DEBUG] Calling llama_init_from_model with: ctx={}, batch={}, threads={}, flash_attn={}, offload_kqv={}",
                    cparams.n_ctx, cparams.n_batch, cparams.n_threads, cparams.flash_attn_type, cparams.offload_kqv);

            System.out.println("📥 [Loader] About to call llama_init_from_model...");
            long t2 = System.currentTimeMillis();
            Pointer ctxPtr = LlamaLibrary.get().llama_init_from_model(modelPtr, cparams);
            long t3 = System.currentTimeMillis();
            System.out.println("✅ [Loader] llama_init_from_model returned after " + (t3 - t2) + " ms. ctxPtr=" + (ctxPtr != null ? "non-null" : "null"));
            if (ctxPtr == null) {
                System.out.println("❌ [Loader] llama_init_from_model returned null, freeing model ptr");
                LlamaLibrary.get().llama_model_free(modelPtr);
                throw new IOException("Failed to create context for model " + modelName);
            }

            log.info("[✔] Model " + id + " successfully loaded via JNA (mmap=1, flash_attn=1, offload_kqv=1)");

            LlamaInstance instance = new LlamaInstance(modelPtr, ctxPtr, modelPath);
            System.out.println("✅ [Loader] LlamaInstance created.");

            serverState.setDefaultInstance(instance);
            serverState.setActiveModelId(id);

            // setActiveModel resetea kvCachePosition=0 y limpia sessionTokens
            // para que no queden tokens sucios del modelo anterior
            System.out.println("🔄 [Loader] About to call llamaChatService.setActiveModel(instance)");
            this.llamaChatService.setActiveModel(instance);
            int workerCount = Math.max(1, properties.getOrchestration().getWorkers());
            workerPool.initialize(modelPtr, cparams, modelPath, workerCount - 1);
            System.out.println("✅ [Loader] setActiveModel(instance) done. Model loaded and active: " + id);

        } catch (Exception e) {
            workerPool.close();
            log.error("[!] Failed to load model {}: {}", modelName, e.getMessage(), e);
            serverState.setDefaultInstance(null);
            serverState.setActiveModelId(null);
            throw new IOException("Failed to load model from " + modelName, e);
        }
    }
}
