package com.boris.librixsoft.server.service.llama;

import com.boris.librixsoft.server.dto.TokenInfo;
import com.boris.librixsoft.server.service.llama.jna.LlamaInstance;
import com.boris.librixsoft.server.service.llama.jna.LlamaLibrary;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import reactor.core.publisher.Flux;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Motor de inferencia JNA para Boris.
 *
 * <h2>Optimizaciones de velocidad</h2>
 * <ul>
 *   <li><b>CUDA Graph pre-warmup agresivo</b>: tras cargar un modelo se ejecutan 4 fases
 *       de warmup realista con generación de múltiples tokens (30-50 tokens por fase).
 *       Esto compila completamente el CUDA graph antes del primer mensaje del usuario,
 *       similar a LM Studio. Los requests reales llegan con el graph ya compilado → sin
 *       "warmup reset" en el primer mensaje real ni en mensajes posteriores.</li>
 *   <li><b>Fases de warmup</b>:
 *       <ul>
 *         <li>FASE 1: Prompt corto (primer mensaje) + 30 tokens de generación</li>
 *         <li>FASE 2: Conversación de 2 turnos + 40 tokens de generación</li>
 *         <li>FASE 3: Conversación extendida (3 turnos) + 50 tokens de generación</li>
 *         <li>FASE 4: Prompt muy corto para KV cache reuse + 1 decode</li>
 *       </ul>
 *   </li>
 *   <li><b>Batch pre-allocado</b>: {@code llama_batch_init} se llama una sola vez por
 *       instancia. No se re-aloca en cada request.</li>
 *   <li><b>Sampler pre-creado</b>: la cadena top_k→top_p→temp→dist se construye una
 *       vez por temperatura y se reutiliza entre requests.</li>
 *   <li><b>Buffer fijo de detokenize</b>: evita new byte[] en el hot-path.</li>
 *   <li><b>KV clear consistente</b>: siempre se prefilla desde posición 0 para que
 *       el CUDA graph tenga la misma topología y se reutilice sin re-warmup.</li>
 * </ul>
 *
 * <h2>Correcciones aplicadas</h2>
 * <ul>
 *   <li><b>FIX 1 — pos en Fase 2 del warmup</b>: antes usaba pos=4 (hardcodeado);
 *       ahora usa pos=64, que es la posición siguiente al último token de la Fase 1.</li>
 *   <li><b>FIX 2 — warmupCudaGraph() retorna Thread</b>: el llamador (LlamaModelLoader)
 *       hace join() sobre ese Thread para bloquear hasta que el CUDA graph esté compilado
 *       antes de marcar el modelo como disponible para el usuario.</li>
 *   <li><b>FIX 3 — stream() espera warmup si no completó</b>: capa de seguridad por si
 *       llega un request antes de que el join del loader haya terminado.</li>
 *   <li><b>FIX 4 — stop token im_end para DeepSeek/Qwen</b>: se loguea el token ID real
 *       de &lt;|im_end|&gt; al arrancar el warmup para que puedas verificar/ajustar el valor.</li>
 *   <li><b>FIX 5 — Warmup agresivo con múltiples fases</b>: se agregaron 4 fases de warmup
 *       con generación de múltiples tokens (10-20 por fase) para compilar completamente
 *       el CUDA graph antes del primer mensaje, eliminando delays en mensajes posteriores.</li>
 * </ul>
 */
@Slf4j
@RequiredArgsConstructor
@org.springframework.stereotype.Service
public class JnaLlamaChatModel implements ChatModel, StreamingChatModel {

    private final LlamaWarmupService warmupService;
    private final AtomicReference<LlamaInstance> activeInstance = new AtomicReference<>();
    private final ReentrantLock generateLock = new ReentrantLock();
    private final AtomicReference<TokenInfo> lastTokenInfo = new AtomicReference<>();
    private volatile int kvCachePosition = 0;
    private int[] sessionTokens = new int[32768];

    // ── Batch pre-allocado ───────────────────────────────────────────────────
    LlamaLibrary.llama_batch.ByValue cachedBatch = null;
    int cachedBatchCapacity = 0;

    // ── Sampler pre-creado ───────────────────────────────────────────────────
    Pointer cachedSampler = null;
    float cachedSamplerTemp = Float.NaN;

    // ── Acumulador UTF-8 ─────────────────────────────────────────────────────
    private final ByteArrayOutputStream pendingBytes = new ByteArrayOutputStream();

    // ── Buffer fijo para detokenize ──────────────────────────────────────────
    private final byte[] tokenBuf = new byte[256];

    // ────────────────────────────────────────────────────────────────────────
    // API pública
    // ────────────────────────────────────────────────────────────────────────

    /**
     * Cambia el modelo activo. Libera batch/sampler cacheados del modelo anterior.
     * Después de llamar esto, invoca {@link #warmupCudaGraph()} y haz join() sobre
     * el Thread retornado para garantizar que el CUDA graph esté compilado antes
     * de aceptar el primer mensaje del usuario.
     */
    public void setActiveModel(LlamaInstance instance) {
        generateLock.lock();
        try {
            warmupService.interruptCurrentWarmup();
            kvCachePosition = 0;
            warmupService.setWarmupStatus(instance == null, (instance == null) ? "No model loaded" : "Model selected, preparing warmup...");
            if (sessionTokens != null) Arrays.fill(sessionTokens, 0);
            freeCachedResources();
            activeInstance.set(instance);
        } finally {
            generateLock.unlock();
        }
    }

    public Thread warmupCudaGraph() {
        return warmupService.warmupCudaGraph(activeInstance.get(), this);
    }

    public void onWarmupFinished(LlamaInstance instance, boolean success) {
        if (activeInstance.get() == instance) {
            // El warmup termina con 1 token en pos=0 en el KV cache.
            // Si dejamos kvCachePosition=0, stream() hace prefill desde pos=0
            // y CUDA falla con "Y=0, X+1=1" (posición duplicada).
            // Solución: limpiar el KV aquí para que coincida con kvCachePosition=0,
            // pero SOLO después del warmup — en este punto el grafo ya está compilado
            // y el clearKvCache no lo destruye, solo libera las entradas del KV.
            if (success) {
                instance.clearKvCache();
            }
            kvCachePosition = 0;
            if (sessionTokens != null) Arrays.fill(sessionTokens, 0);
        }
    }

    public boolean isWarmupComplete() {
        return warmupService.isWarmupComplete();
    }

    public boolean awaitWarmupIfNeeded(long timeoutMs) {
        return warmupService.awaitWarmupIfNeeded(timeoutMs);
    }

    public String getWarmupStatusMessage() {
        return warmupService.getWarmupStatusMessage();
    }

    // Warmup methods extracted to LlamaWarmupService

    /**
     * Limpia el KV cache para iniciar una nueva conversación sin descargar el modelo.
     */
    public void resetKvCache() {
        generateLock.lock();
        try {
            LlamaInstance inst = activeInstance.get();
            if (inst != null && inst.isAlive()) {
                inst.clearKvCache();
                kvCachePosition = 0;
                if (sessionTokens != null) Arrays.fill(sessionTokens, 0);
                log.debug("[KV] Cache cleared (resetKvCache)");
            }
        } finally {
            generateLock.unlock();
        }
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        StringBuilder fullResponse = new StringBuilder();
        stream(prompt).toIterable().forEach(r ->
                fullResponse.append(r.getResult().getOutput().getText())
        );
        return new ChatResponse(List.of(new Generation(new AssistantMessage(fullResponse.toString()))));
    }

    // TODO: Se limita el pensamiento de los modelos aqui de momento
    // pero buscar la forma de hacelro en general
    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return Flux.<ChatResponse>create(sink -> {

            // ── FIX 3: capa de seguridad — esperar warmup si aún no terminó ──
            // El LlamaModelLoader ya hace join() sobre el warmup thread, así que
            // normalmente este bloque no se ejecuta. Actúa como red de protección
            // ante cualquier race condition o llamada prematura al modelo.
            if (!warmupService.awaitWarmupIfNeeded(90_000)) {
                sink.error(new IllegalStateException("Model warmup did not complete successfully before inference"));
                return;
            }

            generateLock.lock();
            int inputTokens  = 0;
            int outputTokens = 0;
            int contextSize  = 0;
            try {
                LlamaInstance instance = activeInstance.get();
                if (instance == null || !instance.isAlive()) {
                    sink.error(new IllegalStateException("No active native model loaded"));
                    return;
                }

                Pointer model = instance.getModel();
                Pointer ctx   = instance.getContext();
                Pointer vocab = LlamaLibrary.get().llama_model_get_vocab(model);

                contextSize     = LlamaLibrary.get().llama_n_ctx(ctx);
                int n_batch_max = LlamaLibrary.get().llama_n_batch(ctx);

                pendingBytes.reset();

                // ── Detectar modelo ──────────────────────────────────────────────
                String lp                = instance.getModelPath() != null
                        ? instance.getModelPath().toLowerCase() : "";
                boolean isGlm            = lp.contains("glm");
                boolean isLlama3         = lp.contains("llama-3") || lp.contains("llama3") || lp.contains("gpt-oss");
                boolean isQwen           = lp.contains("qwen");
                boolean isDeepSeek       = lp.contains("deepseek");
                boolean isMistralReason  = lp.contains("reasoning");
                boolean isGemma          = lp.contains("gemma");

                String input  = buildPrompt(prompt.getInstructions(), instance.getModelPath());
                int[]  tokens = tokenize(vocab, input, true);
                int n_tokens  = tokens.length;
                inputTokens   = n_tokens;

                if (n_tokens > contextSize - 4) {
                    sink.error(new IllegalArgumentException(
                            "Prompt demasiado largo (" + n_tokens + " > " + contextSize + ")"));
                    return;
                }

                // ── Batch pre-allocado ───────────────────────────────────────────
                ensureBatch(n_batch_max);

                // ── KV Cache Reuse ───────────────────────────────────────────────
                int prefixLen = 0;
                while (prefixLen < kvCachePosition && prefixLen < n_tokens
                        && sessionTokens[prefixLen] == tokens[prefixLen]) {
                    prefixLen++;
                }

                // Invalidar tokens divergentes si los hay
                if (prefixLen < kvCachePosition) {
                    log.debug("[KV] Invalidating from {}, current pos {}", prefixLen, kvCachePosition);
                    try {
                        Pointer mem = LlamaLibrary.get().llama_get_memory(ctx);
                        LlamaLibrary.get().llama_memory_seq_rm(mem, 0, prefixLen, -1);
                        kvCachePosition = prefixLen;
                    } catch (UnsatisfiedLinkError | Exception e) {
                        log.debug("[KV] llama_memory_seq_rm falló, usando clear completo: {}", e.getMessage());
                        instance.clearKvCache();
                        kvCachePosition = 0;
                        prefixLen = 0;
                    }
                }

                // Resize sessionTokens si hace falta
                if (sessionTokens.length < contextSize) {
                    int[] newTokens = new int[contextSize];
                    System.arraycopy(sessionTokens, 0, newTokens, 0, kvCachePosition);
                    sessionTokens = newTokens;
                }

                // ── Prefill solo de los tokens nuevos ────────────────────────────
                if (prefixLen < n_tokens) {
                    log.debug("[STREAM] promptTokens={}, prefixReuse={}, prefillNew={}, contextSize={}, batchMax={}",
                            n_tokens, prefixLen, n_tokens - prefixLen, contextSize, n_batch_max);
                    log.debug("[KV] Reusing {} tokens, prefilling {} new tokens",
                            prefixLen, n_tokens - prefixLen);
                    for (int i = prefixLen; i < n_tokens; i += n_batch_max) {
                        int chunkSize = Math.min(n_tokens - i, n_batch_max);
                        log.debug("[STREAM] prefill chunk start={}, size={}, finalChunk={}", i, chunkSize, (i + chunkSize) == n_tokens);
                        cachedBatch.n_tokens = 0;
                        for (int j = 0; j < chunkSize; j++) {
                            int tokIdx = i + j;
                            batch_add(cachedBatch, tokens[tokIdx], tokIdx, List.of(0), tokIdx == n_tokens - 1);
                            sessionTokens[tokIdx] = tokens[tokIdx];
                        }
                        if (LlamaLibrary.get().llama_decode(ctx, cachedBatch) != 0) {
                            sink.error(new RuntimeException("llama_decode failed on prefill chunk"));
                            return;
                        }
                    }
                    kvCachePosition = n_tokens;
                } else {
                    log.debug("[KV] Reusing {} tokens, no new tokens to prefill", prefixLen);
                }

                // ── Sampler ──────────────────────────────────────────────────────
                float temperature = 0.1f;
                if (prompt.getOptions() != null && prompt.getOptions().getTemperature() != null) {
                    temperature = prompt.getOptions().getTemperature().floatValue();
                }
                ensureSampler(temperature);

                // ── Límites por modelo ───────────────────────────────────────────
                int max_tokens = 4096;
                if (prompt.getOptions() != null && prompt.getOptions().getMaxTokens() != null) {
                    max_tokens = prompt.getOptions().getMaxTokens();
                }
                if (isGlm)           max_tokens = Math.min(max_tokens, 512);
                if (isGemma)         max_tokens = Math.min(max_tokens, 800);
                if (isMistralReason) max_tokens = Math.min(max_tokens, 1024);

                // ── Control de thinking ──────────────────────────────────────────
                int  thinkingTokens   = 0;
                int  MAX_THINK_TOKENS = isMistralReason ? 256 : isDeepSeek ? 128 : 0;
                boolean inThinkBlock  = false;
                StringBuilder textAcc = new StringBuilder();

                // ── Generación token a token ─────────────────────────────────────
                int n_cur = kvCachePosition;
                log.debug("[STREAM] generation start n_cur={}, max_tokens={}", n_cur, max_tokens);
                while (n_cur < contextSize && outputTokens < max_tokens) {
                    int token = LlamaLibrary.get().llama_sampler_sample(
                            cachedSampler, ctx, cachedBatch.n_tokens - 1);

                    LlamaLibrary.get().llama_sampler_accept(cachedSampler, token);

                    // ── Stops ────────────────────────────────────────────────────
                    if (token == LlamaLibrary.get().llama_vocab_eos(vocab)) break;
                    if (token == 128009 || token == 128008 || token == 128007) break;
                    if (isGlm && (token == 151336 || token == 151337 ||
                            token == 151338 || token == 151339 ||
                            token == 151329 || token == 151330)) break;
                    // FIX 4: 151645 = <|im_end|>, 151643 = <|im_sep|> en Qwen/DeepSeek.
                    // El log del warmup ([WARMUP] Token ID de <|im_end|>) confirma el valor real.
                    // Si el log muestra otro ID, agrégalo aquí.
                    if ((isQwen || isDeepSeek) && (token == 151645 || token == 151643)) break;
                    if (isDeepSeek && token == 128002) break;

                    // ── Thinking cap ─────────────────────────────────────────────
                    if (MAX_THINK_TOKENS > 0) {
                        String piece = detokenize(vocab, token);
                        textAcc.append(piece);
                        String acc = textAcc.toString();

                        if (!inThinkBlock && acc.contains("<|thinking|>")) {
                            inThinkBlock = true;
                            int idx = acc.indexOf("<|thinking|>");
                            textAcc = new StringBuilder(acc.substring(idx));
                        }
                        if (inThinkBlock && acc.contains("</thinking>")) {
                            inThinkBlock  = false;
                            thinkingTokens = 0;
                            textAcc       = new StringBuilder();
                            cachedBatch.n_tokens = 0;
                            batch_add(cachedBatch, token, n_cur, List.of(0), true);
                            if (LlamaLibrary.get().llama_decode(ctx, cachedBatch) != 0) {
                                sink.error(new RuntimeException("llama_decode failed"));
                                return;
                            }
                            sessionTokens[n_cur] = token;
                            n_cur++;
                            kvCachePosition++;
                            outputTokens++;
                            continue;
                        }
                        if (inThinkBlock) {
                            thinkingTokens++;
                            if (thinkingTokens >= MAX_THINK_TOKENS) {
                                log.debug("[THINK-CAP] Límite alcanzado ({} tokens)", MAX_THINK_TOKENS);
                                inThinkBlock   = false;
                                thinkingTokens = 0;
                                textAcc        = new StringBuilder();
                            }
                            cachedBatch.n_tokens = 0;
                            batch_add(cachedBatch, token, n_cur, List.of(0), true);
                            if (LlamaLibrary.get().llama_decode(ctx, cachedBatch) != 0) {
                                sink.error(new RuntimeException("llama_decode failed"));
                                return;
                            }
                            sessionTokens[n_cur] = token;
                            n_cur++;
                            kvCachePosition++;
                            continue;
                        }

                        if (!piece.isEmpty()) {
                            sink.next(new ChatResponse(List.of(new Generation(new AssistantMessage(piece)))));
                        }
                    } else {
                        String piece = detokenize(vocab, token);
                        if (!piece.isEmpty()) {
                            sink.next(new ChatResponse(List.of(new Generation(new AssistantMessage(piece)))));
                        }
                    }

                    outputTokens++;
                    cachedBatch.n_tokens = 0;
                    batch_add(cachedBatch, token, n_cur, List.of(0), true);
                    if (LlamaLibrary.get().llama_decode(ctx, cachedBatch) != 0) {
                        sink.error(new RuntimeException("llama_decode failed"));
                        break;
                    }
                    sessionTokens[n_cur] = token;
                    n_cur++;
                    kvCachePosition++;
                }

                // ── Vaciar bytes UTF-8 residuales ────────────────────────────────
                if (pendingBytes.size() > 0) {
                    String residual = new String(pendingBytes.toByteArray(), StandardCharsets.UTF_8);
                    pendingBytes.reset();
                    if (!residual.isEmpty()) {
                        sink.next(new ChatResponse(List.of(new Generation(new AssistantMessage(residual)))));
                    }
                }

                lastTokenInfo.set(new TokenInfo(inputTokens, outputTokens, contextSize));
                sink.complete();

            } catch (Exception e) {
                sink.error(e);
            } finally {
                generateLock.unlock();
            }
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    public TokenInfo getLastTokenInfo() {
        return lastTokenInfo.get();
    }

    // Warmup methods replaced by LlamaWarmupService delegation

    // ────────────────────────────────────────────────────────────────────────
    // Recursos pre-allocados
    // ────────────────────────────────────────────────────────────────────────

    public void ensureBatch(int requiredCapacity) {
        if (cachedBatch == null || cachedBatchCapacity < requiredCapacity) {
            if (cachedBatch != null) {
                try { LlamaLibrary.get().llama_batch_free(cachedBatch); } catch (Exception ignored) {}
            }
            cachedBatch = LlamaLibrary.get().llama_batch_init(requiredCapacity, 0, 1);
            cachedBatchCapacity = requiredCapacity;
            log.debug("[BATCH] Pre-allocated capacity={}", requiredCapacity);
        }
    }

    public void ensureSampler(float temperature) {
        if (cachedSampler != null && cachedSamplerTemp == temperature) return;
        if (cachedSampler != null) {
            try { LlamaLibrary.get().llama_sampler_free(cachedSampler); } catch (Exception ignored) {}
        }
        LlamaLibrary.llama_sampler_chain_params.ByValue sparams =
                LlamaLibrary.get().llama_sampler_chain_default_params();
        cachedSampler = LlamaLibrary.get().llama_sampler_chain_init(sparams);
        LlamaLibrary.get().llama_sampler_chain_add(cachedSampler,
                LlamaLibrary.get().llama_sampler_init_top_k(40));
        LlamaLibrary.get().llama_sampler_chain_add(cachedSampler,
                LlamaLibrary.get().llama_sampler_init_top_p(0.95f, new com.sun.jna.NativeLong(1)));
        LlamaLibrary.get().llama_sampler_chain_add(cachedSampler,
                LlamaLibrary.get().llama_sampler_init_temp(temperature));
        LlamaLibrary.get().llama_sampler_chain_add(cachedSampler,
                LlamaLibrary.get().llama_sampler_init_dist((int) (Math.random() * Integer.MAX_VALUE)));
        
        // Penalties to prevent infinite loops (like the one reported with GLM)
        LlamaLibrary.get().llama_sampler_chain_add(cachedSampler,
                LlamaLibrary.get().llama_sampler_init_penalties(
                        64,   // last_n_penalty
                        1.1f, // repeat_penalty
                        0.0f, // frequency_penalty
                        0.0f  // presence_penalty
                ));
        cachedSamplerTemp = temperature;
        log.debug("[SAMPLER] Created chain temp={}", temperature);
    }

    public void freeCachedResources() {
        if (cachedBatch != null) {
            try { LlamaLibrary.get().llama_batch_free(cachedBatch); } catch (Exception ignored) {}
            cachedBatch = null;
            cachedBatchCapacity = 0;
        }
        if (cachedSampler != null) {
            try { LlamaLibrary.get().llama_sampler_free(cachedSampler); } catch (Exception ignored) {}
            cachedSampler = null;
            cachedSamplerTemp = Float.NaN;
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // Tokenización
    // ────────────────────────────────────────────────────────────────────────

    public int[] tokenize(Pointer vocab, String text, boolean add_bos) {
        byte[] utf8    = text.getBytes(StandardCharsets.UTF_8);
        Memory textMem = new Memory(utf8.length + 1);
        textMem.write(0, utf8, 0, utf8.length);
        textMem.setByte(utf8.length, (byte) 0);
        byte addBos = add_bos ? (byte) 1 : (byte) 0;

        int n = LlamaLibrary.get().llama_tokenize(vocab, textMem, utf8.length, null, 0, addBos, (byte) 1);
        if (n == 0) return new int[0];
        if (n < 0)  n = -n;

        Memory mem    = new Memory(n * 4L);
        int    actual = LlamaLibrary.get().llama_tokenize(vocab, textMem, utf8.length, mem, n, addBos, (byte) 1);
        int    count  = actual < 0 ? n : actual;
        int[]  result = new int[count];
        mem.read(0, result, 0, count);
        return result;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Detokenización con acumulador UTF-8 y buffer fijo
    // ────────────────────────────────────────────────────────────────────────

    private String detokenize(Pointer vocab, int token) {
        int n = LlamaLibrary.get().llama_token_to_piece(vocab, token, tokenBuf, tokenBuf.length, 0, (byte) 0);
        byte[] buf = tokenBuf;
        if (n < 0) {
            buf = new byte[-n];
            n   = LlamaLibrary.get().llama_token_to_piece(vocab, token, buf, buf.length, 0, (byte) 1);
        }
        if (n <= 0) return "";

        pendingBytes.write(buf, 0, n);
        byte[] all      = pendingBytes.toByteArray();
        int    validLen = countCompleteUtf8Bytes(all);
        if (validLen == 0) return "";

        pendingBytes.reset();
        if (validLen < all.length) pendingBytes.write(all, validLen, all.length - validLen);
        return new String(all, 0, validLen, StandardCharsets.UTF_8);
    }

    private int countCompleteUtf8Bytes(byte[] bytes) {
        int len = bytes.length, i = 0;
        while (i < len) {
            byte b = bytes[i];
            int charLen;
            if      ((b & 0x80) == 0x00) charLen = 1;
            else if ((b & 0xE0) == 0xC0) charLen = 2;
            else if ((b & 0xF0) == 0xE0) charLen = 3;
            else if ((b & 0xF8) == 0xF0) charLen = 4;
            else { i++; continue; }
            if (i + charLen > len) return i;
            i += charLen;
        }
        return len;
    }

    // ────────────────────────────────────────────────────────────────────────
    // Batch helpers
    // ────────────────────────────────────────────────────────────────────────

    public void batch_add(LlamaLibrary.llama_batch batch, int token, int pos,
                           List<Integer> seq_ids, boolean logits) {
        batch.token.setInt(batch.n_tokens * 4L, token);
        batch.pos.setInt(batch.n_tokens * 4L, pos);
        batch.n_seq_id.setInt(batch.n_tokens * 4L, seq_ids.size());
        for (int i = 0; i < seq_ids.size(); i++) {
            batch.seq_id.getPointer(batch.n_tokens * 8L).setInt(i * 4L, seq_ids.get(i));
        }
        batch.logits.setByte(batch.n_tokens, (byte) (logits ? 1 : 0));
        batch.n_tokens++;
    }

    // getWarmupStatusMessage delegation moved to earlier section

    // ────────────────────────────────────────────────────────────────────────
    // Prompt builder
    // ────────────────────────────────────────────────────────────────────────

    public String buildPrompt(List<Message> messages, String modelPath) {
        StringBuilder sb = new StringBuilder();
        String systemContent = null;
        String lp = modelPath != null ? modelPath.toLowerCase() : "";

        boolean isGemma   = lp.contains("gemma");
        boolean isMistral = lp.contains("mistral") || lp.contains("ministral");
        boolean isLlama3  = lp.contains("llama-3") || lp.contains("llama3") || lp.contains("gpt-oss");
        boolean isGlm     = lp.contains("glm");

        for (Message m : messages) {
            String role    = m.getMessageType().getValue();
            String content = m.getText();

            if ("system".equals(role)) {
                systemContent = content;
            } else if ("user".equals(role)) {
                if (isGemma) {
                    sb.append("<start_of_turn>user\n");
                    if (systemContent != null) { sb.append(systemContent).append("\n\n"); systemContent = null; }
                    sb.append(content).append("<end_of_turn>\n");
                } else if (isMistral) {
                    sb.append("[INST] ");
                    if (systemContent != null) { sb.append("System: ").append(systemContent).append("\n\n"); systemContent = null; }
                    sb.append(content).append(" [/INST]");
                } else if (isLlama3) {
                    if (systemContent != null) {
                        sb.append("<|start_header_id|>system<|end_header_id|>\n\n").append(systemContent).append("<|eot_id|>\n");
                        systemContent = null;
                    }
                    sb.append("<|start_header_id|>user<|end_header_id|>\n\n").append(content).append("<|eot_id|>\n");
                } else if (isGlm) {
                    if (systemContent != null) { sb.append("<|system|>\n").append(systemContent).append("\n"); systemContent = null; }
                    sb.append("<|user|>\n").append(content).append("\n");
                } else {
                    // ChatML — Qwen, DeepSeek, gpt-oss
                    if (systemContent != null) { sb.append("<|im_start|>system\n").append(systemContent).append("<|im_end|>\n"); systemContent = null; }
                    sb.append("<|im_start|>user\n").append(content).append("<|im_end|>\n");
                }
            } else if ("assistant".equals(role)) {
                if (isGemma)        sb.append("<start_of_turn>model\n").append(content).append("<end_of_turn>\n");
                else if (isMistral) sb.append(" ").append(content).append("</s> ");
                else if (isLlama3)  sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n").append(content).append("<|eot_id|>\n");
                else if (isGlm)     sb.append("<|assistant|>\n").append(content).append("\n");
                else                sb.append("<|im_start|>assistant\n").append(content).append("<|im_end|>\n");
            }
        }

        // Trigger de generación
        if (isGemma) {
            if (!sb.toString().endsWith("<start_of_turn>model\n")) sb.append("<start_of_turn>model\n");
        } else if (isLlama3) {
            if (!sb.toString().endsWith("<|start_header_id|>assistant<|end_header_id|>\n\n"))
                sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n");
        } else if (isGlm) {
            if (!sb.toString().endsWith("<|assistant|>\n")) sb.append("<|assistant|>\n");
        } else if (!isMistral) {
            if (!sb.toString().endsWith("<|im_start|>assistant\n")) sb.append("<|im_start|>assistant\n");
        }

        return sb.toString();
    }
}