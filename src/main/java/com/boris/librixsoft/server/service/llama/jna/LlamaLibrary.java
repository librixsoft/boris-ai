package com.boris.librixsoft.server.service.llama.jna;

import com.boris.librixsoft.util.PathResolver;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.NativeLong;
import com.sun.jna.Pointer;
import com.sun.jna.Structure;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

/**
 * JNA bindings for llama.cpp native library.
 *
 * IMPORTANT: Structs returned by-value from C (like llama_model_params) are
 * wrapped with the ByValue interface. The field layout MUST match the compiled
 * llama.h exactly. If the layout is wrong the JVM will crash; adjust to the
 * exact version of llama.dll present in vendor/llama.cpp.
 *
 * Loading is deferred — call LlamaLibrary.load() explicitly so startup
 * does NOT crash if the DLL is missing.
 */
public interface LlamaLibrary extends Library {

    /** Library path configured from application.yml */
    AtomicReference<String> CONFIGURED_LIBRARY_PATH = new AtomicReference<>();

    /** Lazy holder — avoids static crash if the library is not on the path. */
    final class Holder {
        private static volatile LlamaLibrary INSTANCE;

        static LlamaLibrary load() {
            if (INSTANCE == null) {
                synchronized (Holder.class) {
                    if (INSTANCE == null) {
                        // Ensure JNA can find our DLLs
                        // Use configured path from application.yml, or fallback to default
                        String configuredPath = CONFIGURED_LIBRARY_PATH.get();
                        String libraryPath;
                        if (configuredPath != null && !configuredPath.isEmpty()) {
                            // Resolve path relative to user.home if it starts with /
                            if (configuredPath.startsWith("/")) {
                                libraryPath = PathResolver.getUserHome() + configuredPath;
                            } else {
                                libraryPath = configuredPath;
                            }
                        } else {
                            libraryPath = PathResolver.getUserHome() + "/.boris/vendor/llama.cpp";
                        }
                        System.setProperty("jna.library.path", libraryPath);
                        
                        // Windows specific: help llama.dll find its dependencies (ggml-cpu.dll, etc.)
                        if (System.getProperty("os.name").toLowerCase().contains("win")) {
                            try {
                                // Use WString to ensure UTF-16 on Windows
                                com.sun.jna.Function f = com.sun.jna.Function.getFunction("kernel32", "SetDllDirectoryW");
                                f.invoke(new Object[]{new com.sun.jna.WString(libraryPath)});
                                
                                com.sun.jna.Function f2 = com.sun.jna.Function.getFunction("kernel32", "SetEnvironmentVariableW");
                                f2.invoke(new Object[]{new com.sun.jna.WString("GGML_BACKEND_PATH"), new com.sun.jna.WString(libraryPath)});
                                
                                // Also update PATH just in case
                                String currentPath = System.getenv("PATH");
                                f2.invoke(new Object[]{new com.sun.jna.WString("PATH"), new com.sun.jna.WString(libraryPath + ";" + currentPath)});
                                
                                // FORCE current directory to the DLL folder so backends are found
                                com.sun.jna.Function f3 = com.sun.jna.Function.getFunction("kernel32", "SetCurrentDirectoryW");
                                f3.invoke(new Object[]{new com.sun.jna.WString(libraryPath)});
                                
                                System.out.println("[*] Windows: Search paths and CWD set to: " + libraryPath);
                            } catch (Throwable t) {
                                System.err.println("[!] Warning: Could not set DLL directory: " + t.getMessage());
                            }
                        }
                        
                        System.out.println("[*] JNA Library Path set to: " + libraryPath);
                        INSTANCE = Native.load("llama", LlamaLibrary.class);
                    }
                }
            }
            return INSTANCE;
        }
    }

    static LlamaLibrary get() {
        return Holder.load();
    }

    /**
     * Sets the library path from configuration before loading.
     * Must be called before first get() invocation.
     * @param path Path from boris.llama-server-path configuration
     */
    static void setLibraryPath(String path) {
        CONFIGURED_LIBRARY_PATH.set(path);
    }

    // ────────────────────────────────────────────────────────────────────────
    // Structs
    // ────────────────────────────────────────────────────────────────────────

    /**
     * llama_model_params — maps to the C struct returned by llama_model_default_params().
     * Field order and types must match llama.h exactly.
     */
    @Structure.FieldOrder({
        "devices", "tensor_buft_overrides", "n_gpu_layers", "split_mode", "main_gpu", "tensor_split",
        "progress_callback", "progress_callback_user_data", "kv_overrides",
        "vocab_only", "use_mmap", "use_direct_io", "use_mlock", "check_tensors",
        "use_extra_bufts", "no_host", "no_alloc"
    })
    class llama_model_params extends Structure {
        public Pointer      devices;
        public Pointer      tensor_buft_overrides;
        public int          n_gpu_layers;
        public int          split_mode;
        public int          main_gpu;
        public Pointer      tensor_split;
        public Pointer      progress_callback;
        public Pointer      progress_callback_user_data;
        public Pointer      kv_overrides;
        public byte         vocab_only;
        public byte         use_mmap;
        public byte         use_direct_io;
        public byte         use_mlock;
        public byte         check_tensors;
        public byte         use_extra_bufts;
        public byte         no_host;
        public byte         no_alloc;

        public static class ByValue extends llama_model_params implements Structure.ByValue {}
    }

    /**
     * llama_context_params — maps to the C struct returned by llama_context_default_params().
     */
    @Structure.FieldOrder({
        "n_ctx", "n_batch", "n_ubatch", "n_seq_max",
        "n_threads", "n_threads_batch",
        "rope_scaling_type", "pooling_type", "attention_type", "flash_attn_type",
        "rope_freq_base", "rope_freq_scale",
        "yarn_ext_factor", "yarn_attn_factor", "yarn_beta_fast", "yarn_beta_slow",
        "yarn_orig_ctx", "defrag_thold",
        "cb_eval", "cb_eval_user_data",
        "type_k", "type_v",
        "abort_callback", "abort_callback_data",
        "embeddings", "offload_kqv", "no_perf", "op_offload", "swa_full", "kv_unified",
        "samplers", "n_samplers"
    })
    class llama_context_params extends Structure {
        public int     n_ctx;
        public int     n_batch;
        public int     n_ubatch;
        public int     n_seq_max;
        public int     n_threads;
        public int     n_threads_batch;
        public int     rope_scaling_type;
        public int     pooling_type;
        public int     attention_type;
        public int     flash_attn_type;
        public float   rope_freq_base;
        public float   rope_freq_scale;
        public float   yarn_ext_factor;
        public float   yarn_attn_factor;
        public float   yarn_beta_fast;
        public float   yarn_beta_slow;
        public int     yarn_orig_ctx;
        public float   defrag_thold;
        public Pointer cb_eval;
        public Pointer cb_eval_user_data;
        public int     type_k;
        public int     type_v;
        public Pointer abort_callback;
        public Pointer abort_callback_data;
        public byte    embeddings;
        public byte    offload_kqv;
        public byte    no_perf;
        public byte    op_offload;
        public byte    swa_full;
        public byte    kv_unified;
        public Pointer samplers;
        public long    n_samplers; // size_t

        public static class ByValue extends llama_context_params implements Structure.ByValue {}
    }

    /** llama_batch — used to submit tokens for decoding. */
    @Structure.FieldOrder({"n_tokens", "token", "embd", "pos", "n_seq_id", "seq_id", "logits"})
    class llama_batch extends Structure {
        public int     n_tokens;
        public Pointer token;
        public Pointer embd;
        public Pointer pos;
        public Pointer n_seq_id;
        public Pointer seq_id;
        public Pointer logits;

        public static class ByValue extends llama_batch implements Structure.ByValue {}
    }

    @Structure.FieldOrder({"no_perf"})
    class llama_sampler_chain_params extends Structure {
        public byte no_perf;
        public static class ByValue extends llama_sampler_chain_params implements Structure.ByValue {}
    }

    // ────────────────────────────────────────────────────────────────────────
    // Backend
    // ────────────────────────────────────────────────────────────────────────
    void llama_backend_init();
    void llama_backend_free();

    // ────────────────────────────────────────────────────────────────────────
    // Model
    // ────────────────────────────────────────────────────────────────────────
    llama_model_params.ByValue llama_model_default_params();
    Pointer llama_model_load_from_file(String path_model, llama_model_params.ByValue params);
    void llama_model_free(Pointer model);

    // ────────────────────────────────────────────────────────────────────────
    // Context
    // ────────────────────────────────────────────────────────────────────────
    llama_context_params.ByValue llama_context_default_params();
    Pointer llama_init_from_model(Pointer model, llama_context_params.ByValue params);
    @Deprecated
    Pointer llama_new_context_with_model(Pointer model, llama_context_params.ByValue params);
    void llama_free(Pointer ctx);
    int llama_n_ctx(Pointer ctx);
    int llama_n_batch(Pointer ctx);
    Pointer llama_get_memory(Pointer ctx);

    // ────────────────────────────────────────────────────────────────────────
    // Batch
    // ────────────────────────────────────────────────────────────────────────
    llama_batch.ByValue llama_batch_init(int n_tokens_alloc, int embd, int n_seq_max);
    void llama_batch_free(llama_batch.ByValue batch);

    // ────────────────────────────────────────────────────────────────────────
    // Tokenization
    // ────────────────────────────────────────────────────────────────────────
    Pointer llama_model_get_vocab(Pointer model);
    int llama_tokenize(Pointer vocab, Pointer text, int text_len,
                   Pointer tokens, int n_tokens_max,
                   byte add_special, byte parse_special);
    int llama_token_to_piece(Pointer vocab, int token, byte[] buf, int length, int lstrip, byte special);
    int llama_vocab_eos(Pointer vocab);
    int llama_vocab_bos(Pointer vocab);

    // ────────────────────────────────────────────────────────────────────────
    // Decoding
    // ────────────────────────────────────────────────────────────────────────
    int llama_decode(Pointer ctx, llama_batch.ByValue batch);

    // ────────────────────────────────────────────────────────────────────────
    // Memory / KV Cache management
    // ────────────────────────────────────────────────────────────────────────
    void llama_memory_clear(Pointer mem, boolean data);
    boolean llama_memory_seq_rm(Pointer mem, int seq_id, int p0, int p1);
    void llama_kv_cache_clear(Pointer ctx);

    // ────────────────────────────────────────────────────────────────────────
    // Sampler Chain (llama.cpp v3+)
    // ────────────────────────────────────────────────────────────────────────
    llama_sampler_chain_params.ByValue llama_sampler_chain_default_params();
    Pointer llama_sampler_chain_init(llama_sampler_chain_params.ByValue params);
    void    llama_sampler_chain_add(Pointer chain, Pointer sampler);
    Pointer llama_sampler_init_top_k(int k);
    Pointer llama_sampler_init_top_p(float p, NativeLong min_keep);
    Pointer llama_sampler_init_temp(float t);
    Pointer llama_sampler_init_dist(int seed);
    Pointer llama_sampler_init_penalties(int last_n, float repeat, float freq, float presence);
    int     llama_sampler_sample(Pointer sampler, Pointer ctx, int idx);
    void    llama_sampler_accept(Pointer sampler, int token);
    void    llama_sampler_free(Pointer sampler);


    /** Helper to load GGML backends separately if needed. */
    interface GgmlLibrary extends Library {
        static GgmlLibrary get() {
            return Native.load("ggml", GgmlLibrary.class);
        }
        void ggml_backend_load_all();
        Pointer ggml_backend_load(String name);
    }
}
