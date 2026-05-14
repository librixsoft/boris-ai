package com.boris.librixsoft.server.service.llama;

import com.boris.librixsoft.server.service.llama.jna.LlamaInstance;
import com.boris.librixsoft.server.service.llama.jna.LlamaLibrary;
import com.sun.jna.Pointer;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Servicio dedicado a realizar el calentamiento exhaustivo de grafos CUDA.
 * Utiliza los mismos recursos (batch/sampler) que el motor de producción para garantizar
 * la reutilización perfecta de los grafos.
 */
@Slf4j
@Service
public class LlamaWarmupService {

    @Getter
    private volatile boolean warmupComplete = false;
    @Getter
    private volatile String warmupStatusMessage = "No model loaded";
    
    private final AtomicReference<Thread> currentWarmupThread = new AtomicReference<>();
    private final AtomicBoolean interrupted = new AtomicBoolean(false);

    public void interruptCurrentWarmup() {
        Thread t = currentWarmupThread.get();
        if (t != null && t.isAlive()) {
            log.info("[WARMUP] Interrupting previous warmup thread...");
            interrupted.set(true);
            t.interrupt();
        }
    }

    public void setWarmupStatus(boolean complete, String message) {
        this.warmupComplete = complete;
        this.warmupStatusMessage = message;
        this.interrupted.set(false);
    }

    public boolean awaitWarmupIfNeeded(long timeoutMs) {
        if (warmupComplete) return true;
        Thread t = currentWarmupThread.get();
        if (t != null && t.isAlive()) {
            try {
                t.join(timeoutMs);
                return warmupComplete;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return warmupComplete;
    }

    public Thread warmupCudaGraph(LlamaInstance instance, JnaLlamaChatModel model) {
        if (instance == null) {
            setWarmupStatus(true, "No model loaded");
            return Thread.ofVirtual().start(() -> {});
        }

        interruptCurrentWarmup();
        
        Thread warmupThread = Thread.ofVirtual().start(() -> {
            currentWarmupThread.set(Thread.currentThread());
            interrupted.set(false);
            warmupComplete = false;
            warmupStatusMessage = "Initializing warmup...";
            
            try {
                Pointer llamaModel = instance.getModel();
                Pointer ctx        = instance.getContext();
                Pointer vocab = LlamaLibrary.get().llama_model_get_vocab(llamaModel);
                int n_batch   = LlamaLibrary.get().llama_n_batch(ctx);

                warmupStatusMessage = "Pre-allocating resources...";
                log.info("[WARMUP] Pre-allocating production resources for model {}...", instance.getModelPath());
                
                // CRITICO: Forzar la creación de los recursos de producción
                model.ensureBatch(n_batch);
                model.ensureSampler(0.1f);
                
                // CRITICO: Usar el batch EXACTO que usará el chat
                LlamaLibrary.llama_batch.ByValue productionBatch = model.cachedBatch;

                warmupStatusMessage = "Running ultra-realistic warmup...";
                log.info("[WARMUP] Iniciando warmup con recursos de producción para {}...", instance.getModelPath());

                if (runRealisticWarmup(instance, ctx, vocab, productionBatch, n_batch, model)) {
                    log.info("[WARMUP] ✅ Warmup completado con éxito para {}", instance.getModelPath());
                    setWarmupStatus(true, "Warmup complete");
                } else {
                    log.warn("[WARMUP] ⚠ Warmup incompleto o fallido para {}", instance.getModelPath());
                    setWarmupStatus(false, "Warmup failed");
                }
            } catch (Exception e) {
                log.error("[WARMUP] Error crítico durante el calentamiento: {}", e.getMessage(), e);
                setWarmupStatus(false, "Warmup error: " + e.getMessage());
            } finally {
                currentWarmupThread.set(null);
                model.onWarmupFinished(instance, warmupComplete);
            }
        });

        currentWarmupThread.set(warmupThread);
        return warmupThread;
    }

    private boolean runRealisticWarmup(LlamaInstance instance,
                                       Pointer ctx,
                                       Pointer vocab,
                                       LlamaLibrary.llama_batch.ByValue batch,
                                       int nBatch,
                                       JnaLlamaChatModel model) {
        // ── Timeout centralizado ──────────────────────────────────────────────
        // Ajustar aquí si el modelo es muy pesado (MoE, >13B, etc).
        // DeepSeek-Coder-V2 Lite (~9B MoE) necesita ~8-15s por primer decode.
        // Modelos >30B MoE pueden necesitar 120_000 o más.
        final long WARMUP_TIMEOUT_MS = 120_000; // 2 minutos por operación

        instance.clearKvCache();

        log.info("[WARMUP] FASE PREFILL PROGRESIVO - Calentando pool CUDA gradualmente... (timeout={}ms)", WARMUP_TIMEOUT_MS);
        int size = 1;
        while (size <= nBatch) {
            instance.clearKvCache();
            int[] progressive = new int[size];
            Arrays.fill(progressive, 1);
            log.info("[WARMUP] PROG prefill size={}", size);
            if (!prefillWarmupPrompt(ctx, batch, progressive, nBatch, WARMUP_TIMEOUT_MS, "PROG-" + size, model)) {
                log.warn("[WARMUP] PROG-{} falló, continuando con siguiente bucket...", size);
            }
            size *= 2;
        }

        instance.clearKvCache();

        log.info("[WARMUP] FASE 0 - Posiciones iniciales...");
        int[] bos = { 1 };
        prefillWarmupPrompt(ctx, batch, bos, nBatch, WARMUP_TIMEOUT_MS, "BOS", model);
        batch.n_tokens = 0;
        model.batch_add(batch, bos[0], 0, List.of(0), true);
        runMultipleDecodes(ctx, batch, vocab, 256, WARMUP_TIMEOUT_MS, "FASE 0", model);

        instance.clearKvCache();
        log.info("[WARMUP] FASE 1 - Conversación Turno 1...");
        int[] t1 = model.tokenize(vocab, model.buildPrompt(
                List.of(new SystemMessage("Hi"), new UserMessage("hi")),
                instance.getModelPath()), true);
        prefillWarmupPrompt(ctx, batch, t1, nBatch, WARMUP_TIMEOUT_MS, "T1", model);
        batch.n_tokens = 0;
        model.batch_add(batch, t1[t1.length - 1], t1.length, List.of(0), true);
        runMultipleDecodes(ctx, batch, vocab, 60, WARMUP_TIMEOUT_MS, "FASE 1", model);

        log.info("[WARMUP] FASE 2 - Incremental Turno 2...");
        int[] t2 = model.tokenize(vocab, " How are you?", false);
        int pos2 = t1.length + 60;
        prefillIncrementalWarmup(ctx, batch, t2, pos2, nBatch, WARMUP_TIMEOUT_MS, "T2-INC", model);
        batch.n_tokens = 0;
        model.batch_add(batch, t2[t2.length - 1], pos2 + t2.length, List.of(0), true);
        runMultipleDecodes(ctx, batch, vocab, 60, WARMUP_TIMEOUT_MS, "FASE 2", model);

        int[] buckets = {1, 2, 4, 8, 16, 32, 64, 128};
        for (int s : buckets) {
            instance.clearKvCache();
            int[] d = new int[s];
            Arrays.fill(d, 1);
            prefillWarmupPrompt(ctx, batch, d, nBatch, WARMUP_TIMEOUT_MS, "B-" + s, model);
        }

        // ── ESTADO FINAL NEUTRO ──
        instance.clearKvCache();
        batch.n_tokens = 0;
        model.batch_add(batch, 1, 0, List.of(0), true);
        LlamaLibrary.get().llama_decode(ctx, batch);
        // ── ESTADO FINAL NEUTRO ──
        // Solo limpiar el KV — onWarmupFinished hará el clearKvCache final.
        // No dejamos ningún token extra para evitar colisión de posiciones
        // en el primer prefill real.
        instance.clearKvCache();
        return true;
    }

    private boolean prefillWarmupPrompt(Pointer ctx, LlamaLibrary.llama_batch.ByValue batch, int[] tokens, int nBatch, long timeoutMs, String label, JnaLlamaChatModel model) {
        for (int i = 0; i < tokens.length; i += nBatch) {
            int cs = Math.min(tokens.length - i, nBatch);
            batch.n_tokens = 0;
            for (int j = 0; j < cs; j++) {
                int ti = i + j;
                model.batch_add(batch, tokens[ti], ti, List.of(0), ti == tokens.length - 1);
            }
            if (!runDecodeWithTimeout(ctx, batch, timeoutMs)) return false;
        }
        return true;
    }

    private boolean prefillIncrementalWarmup(Pointer ctx, LlamaLibrary.llama_batch.ByValue batch, int[] tokens, int startPos, int nBatch, long timeoutMs, String label, JnaLlamaChatModel model) {
        for (int i = 0; i < tokens.length; i += nBatch) {
            int cs = Math.min(tokens.length - i, nBatch);
            batch.n_tokens = 0;
            for (int j = 0; j < cs; j++) {
                int ti = i + j;
                model.batch_add(batch, tokens[ti], startPos + ti, List.of(0), ti == tokens.length - 1);
            }
            if (!runDecodeWithTimeout(ctx, batch, timeoutMs)) return false;
        }
        return true;
    }

    private boolean runMultipleDecodes(Pointer ctx, LlamaLibrary.llama_batch.ByValue batch, Pointer vocab, int numTokens, long timeoutMs, String label, JnaLlamaChatModel model) {
        Pointer sampler = model.cachedSampler; // USAR EL SAMPLER DE PRODUCCION
        int currentPos = batch.pos.getInt(0);
        int lastToken = batch.token.getInt(0);
        for (int i = 0; i < numTokens; i++) {
            if (interrupted.get()) return false;
            batch.n_tokens = 0;
            model.batch_add(batch, lastToken, currentPos + i, List.of(0), true);
            if (!runDecodeWithTimeout(ctx, batch, timeoutMs)) return false;
            int nextToken = LlamaLibrary.get().llama_sampler_sample(sampler, ctx, batch.n_tokens - 1);
            LlamaLibrary.get().llama_sampler_accept(sampler, nextToken);
            lastToken = nextToken;
        }
        return true;
    }

    private boolean runDecodeWithTimeout(Pointer ctx, LlamaLibrary.llama_batch.ByValue batch, long timeoutMs) {
        if (interrupted.get()) return false;
        AtomicReference<Integer> result = new AtomicReference<>(-1);
        Thread t = Thread.ofVirtual().start(() -> {
            try { result.set(LlamaLibrary.get().llama_decode(ctx, batch)); } catch (Exception ignored) {}
        });
        try {
            t.join(timeoutMs);
            return !t.isAlive() && result.get() == 0;
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); return false; }
    }
}
