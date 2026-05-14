package com.boris.librixsoft.server.service.llama.jna;

import com.sun.jna.Pointer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import java.util.concurrent.atomic.AtomicBoolean;

@Slf4j
public class LlamaInstance implements AutoCloseable {
    @Getter
    private final Pointer model;
    @Getter
    private final Pointer context;
    @Getter
    private final String modelPath;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    public LlamaInstance(Pointer model, Pointer context, String modelPath) {
        this.model = model;
        this.context = context;
        this.modelPath = modelPath;
    }

    public boolean isAlive() {
        return model != null && context != null && !closed.get();
    }

    /**
     * Clears the conversation state (KV cache) for this model instance.
     * This removes all tokens from the KV cache without unloading the model.
     */
    public void clearKvCache() {
        if (context != null && !closed.get()) {
            try {
                log.info("[*] Clearing KV cache for: {}", modelPath);
                
                // Try modern API first
                try {
                    LlamaLibrary.get().llama_kv_cache_clear(context);
                    log.info("[✔] KV cache cleared (modern API) for: {}", modelPath);
                    return;
                } catch (Throwable t) {
                    log.debug("Modern llama_kv_cache_clear not available, trying legacy...");
                }

                // Fallback to legacy/custom API
                Pointer mem = LlamaLibrary.get().llama_get_memory(context);
                if (mem != null) {
                    LlamaLibrary.get().llama_memory_clear(mem, true);
                    log.info("[✔] KV cache cleared (legacy API) for: {}", modelPath);
                }
            } catch (Exception e) {
                log.warn("[!] Could not clear KV cache: {}", e.getMessage());
            }
        }
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            log.info("[*] Releasing native llama resources for: {}", modelPath);
            if (context != null) {
                LlamaLibrary.get().llama_free(context);
            }
            if (model != null) {
                LlamaLibrary.get().llama_model_free(model);
            }
            log.info("[✔] Native resources released.");
        }
    }
}
