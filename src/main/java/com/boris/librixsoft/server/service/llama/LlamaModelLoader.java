package com.boris.librixsoft.server.service.llama;

import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.server.service.llama.jna.LlamaInstance;
import com.boris.librixsoft.server.service.llama.jna.LlamaLibrary;
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
    // ── FIX: referencia al chat model para poder lanzar y ESPERAR el warmup ─
    private final JnaLlamaChatModel chatModel;

    private static final String GREEN  = "\u001B[32m";
    private static final String RESET  = "\u001B[0m";

    public LlamaInstance loadModelWithParams(String id, String modelName, Integer contextSize, Integer threads,
                                             Integer gpuLayers, Integer batchSize, Double temperature,
                                             Integer maxTokens, Integer parallel) throws IOException {

        log.info("[*] Loading model {} via Pure JNA...", modelName);

        String modelsDir = PathResolver.resolveAndCreate(properties.getModelsDir(), true);
        String nameWithExt = modelName.endsWith(".gguf") ? modelName : modelName + ".gguf";
        String modelPath = java.nio.file.Paths.get(modelsDir, nameWithExt)
                .toAbsolutePath()
                .normalize()
                .toString()
                .replace('\\', '/');

        try {
            // Model params
            LlamaLibrary.llama_model_params.ByValue mparams = LlamaLibrary.get().llama_model_default_params();

            log.info("[DEBUG] Default params → mmap={}, mlock={}, gpu_layers={}",
                    mparams.use_mmap, mparams.use_mlock, mparams.n_gpu_layers);

            mparams.n_gpu_layers = gpuLayers != null ? gpuLayers : 0;
            mparams.use_mmap     = (byte) 1;
            mparams.use_mlock    = (byte) 0;

            log.info("[DEBUG] Calling llama_model_load_from_file with: path={}, gpu_layers={}, mmap={}, mlock={}",
                    modelPath, mparams.n_gpu_layers, mparams.use_mmap, mparams.use_mlock);

            Pointer modelPtr = LlamaLibrary.get().llama_model_load_from_file(modelPath, mparams);
            if (modelPtr == null) {
                throw new IOException("Failed to load model from " + modelPath);
            }

            // Context params
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

            Pointer ctxPtr = LlamaLibrary.get().llama_init_from_model(modelPtr, cparams);
            if (ctxPtr == null) {
                LlamaLibrary.get().llama_model_free(modelPtr);
                throw new IOException("Failed to create context for model " + modelName);
            }

            log.info("[✔] Model " + GREEN + id + RESET + " successfully loaded via JNA (mmap=1, flash_attn=1, offload_kqv=1)");

            LlamaInstance instance = new LlamaInstance(modelPtr, ctxPtr, modelPath);

            // NOTA: El warmup ahora se maneja en BorisLLamaServerWrapper.loadModelWithParams
            // para asegurar la limpieza correcta de sessionTokens via setActiveModel.
            // Aquí solo devolvemos la instancia cargada.
            return instance;

        } catch (Exception e) {
            log.error("[!] Error loading model natively: {}", e.getMessage());
            throw new IOException("Native model load failed", e);
        }
    }
}