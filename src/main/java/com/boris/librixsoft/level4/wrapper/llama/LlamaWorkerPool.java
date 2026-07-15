package com.boris.librixsoft.level4.wrapper.llama;

import com.boris.librixsoft.ai.Message;
import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.level3.domain.service.SkillExecutor;
import com.boris.librixsoft.level3.domain.service.SkillService;
import com.boris.librixsoft.level5.nativeCpp.jna.LlamaInstance;
import com.boris.librixsoft.level5.nativeCpp.jna.LlamaLibrary;
import com.sun.jna.Pointer;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Contextos auxiliares sobre el mismo llama_model*. Cada LlamaChatService tiene
 * su propio KV cache, sampler y lock; los pesos del modelo no se duplican.
 */
@Slf4j
@Service
public class LlamaWorkerPool {
    private final SkillService skillService;
    private final SkillExecutor skillExecutor;
    private final LlamaModelTemplateReader templateReader;
    private final LlamaModelStopTokens stopTokens;
    private final List<Worker> workers = new ArrayList<>();
    private final AtomicInteger nextWorker = new AtomicInteger();

    public LlamaWorkerPool(SkillService skillService, SkillExecutor skillExecutor,
                           LlamaModelTemplateReader templateReader, LlamaModelStopTokens stopTokens) {
        this.skillService = skillService;
        this.skillExecutor = skillExecutor;
        this.templateReader = templateReader;
        this.stopTokens = stopTokens;
    }

    /** Creates worker contexts. The primary context is owned by LlamaServerState. */
    public synchronized void initialize(Pointer model, LlamaLibrary.llama_context_params.ByValue params,
                                        String modelPath, int auxiliaryWorkerCount) {
        close();
        for (int i = 0; i < auxiliaryWorkerCount; i++) {
            Pointer context = LlamaLibrary.get().llama_init_from_model(model, params);
            if (context == null) {
                close();
                throw new IllegalStateException("Could not create auxiliary llama context " + (i + 1));
            }
            LlamaInstance instance = new LlamaInstance(model, context, modelPath, false);
            LlamaChatService chat = new LlamaChatService(skillService, skillExecutor, templateReader, stopTokens);
            chat.setActiveModel(instance);
            workers.add(new Worker(instance, chat));
        }
        log.info("[ORCHESTRATION] {} auxiliary contexts ready (plus primary context)", workers.size());
    }

    public boolean isReady() {
        return !workers.isEmpty();
    }

    public int size() {
        return workers.size();
    }

    public String execute(String modelId, String systemPrompt, String instruction, Double temperature,
                          Integer maxTokens, List<Message> history, AtomicBoolean cancelled) {
        if (workers.isEmpty()) {
            throw new IllegalStateException("Auxiliary model contexts are not initialized");
        }
        Worker worker;
        synchronized (this) {
            worker = workers.get(Math.floorMod(nextWorker.getAndIncrement(), workers.size()));
        }
        return worker.chat().executePrompt(modelId, systemPrompt, instruction, temperature, cancelled, history, maxTokens);
    }

    /** Executes a prompt on one specific peer context. */
    public String executeOnWorker(int workerIndex, String modelId, String systemPrompt, String instruction,
                                  Double temperature, Integer maxTokens, List<Message> history,
                                  AtomicBoolean cancelled) {
        Worker worker;
        synchronized (this) {
            if (workerIndex < 0 || workerIndex >= workers.size()) {
                throw new IllegalArgumentException("Unknown peer context: " + workerIndex);
            }
            worker = workers.get(workerIndex);
        }
        return worker.chat().executePrompt(modelId, systemPrompt, instruction, temperature,
                cancelled, history, maxTokens);
    }

    /**
     * Runs exactly one prompt per worker concurrently.  Unlike {@link #execute}, this
     * method pins each prompt to a different context, which is required by the
     * peer-orchestration flow.
     */
    public List<String> executeAll(String modelId, String systemPrompt, List<String> instructions,
                                   Double temperature, Integer maxTokens, List<Message> history,
                                   AtomicBoolean cancelled) {
        List<Worker> assignedWorkers;
        synchronized (this) {
            if (workers.isEmpty()) {
                throw new IllegalStateException("Auxiliary model contexts are not initialized");
            }
            if (instructions.size() != workers.size()) {
                throw new IllegalArgumentException("Expected " + workers.size() + " peer prompts, got " + instructions.size());
            }
            assignedWorkers = new ArrayList<>(workers);
        }

        List<CompletableFuture<String>> results = new ArrayList<>();
        for (int i = 0; i < assignedWorkers.size(); i++) {
            Worker worker = assignedWorkers.get(i);
            String instruction = instructions.get(i);
            results.add(CompletableFuture.supplyAsync(() ->
                    worker.chat().executePrompt(modelId, systemPrompt, instruction, temperature,
                            cancelled, history, maxTokens)));
        }
        CompletableFuture.allOf(results.toArray(CompletableFuture[]::new)).join();
        return results.stream().map(CompletableFuture::join).toList();
    }

    public synchronized void close() {
        for (Worker worker : workers) {
            try {
                worker.chat().setActiveModel(null);
                worker.instance().close(); // closes its context only; it does not own the model
            } catch (Exception e) {
                log.warn("Error closing auxiliary context: {}", e.getMessage());
            }
        }
        workers.clear();
        nextWorker.set(0);
    }

    @PreDestroy
    void destroy() {
        close();
    }

    private record Worker(LlamaInstance instance, LlamaChatService chat) { }
}
