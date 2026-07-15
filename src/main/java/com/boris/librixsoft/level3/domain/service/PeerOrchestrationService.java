package com.boris.librixsoft.level3.domain.service;

import com.boris.librixsoft.ai.Message;
import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.level4.wrapper.llama.LlamaChatService;
import com.boris.librixsoft.level4.wrapper.llama.LlamaWorkerPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.IntStream;

/**
 * Chain orchestration proof of concept.
 *
 * The Java code implements a chain interaction where each model receives
 * the output of the previous model as input, creating a conversation flow
 * between the 4 models.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PeerOrchestrationService {

    private final BorisProperties properties;
    private final LlamaChatService primaryChat;
    private final LlamaWorkerPool workerPool;
    private static final String CHAIN_SYSTEM_PROMPT = """
            Si recibes un número solo (ej: 5), responde: 5 + 1 = 6
            Si recibes una ecuación (ej: 5 + 1 = 6), extrae el resultado (6) y responde: 6 + 1 = 7
            Siempre responde en formato: X + 1 = Y
            """;

    public record PeerContribution(int number, String text) { }

    public boolean isReady() {
        int totalPeers = properties.getOrchestration().getWorkers();
        return properties.getOrchestration().isEnabled()
                && totalPeers >= 2
                && workerPool.size() == totalPeers - 1;
    }

    public String execute(String modelId, String instruction, Double temperature, Integer maxTokens,
                          List<Message> history, AtomicBoolean cancelled) {
        if (!isReady()) {
            throw new IllegalStateException("Chain orchestration requires the configured number of initialized contexts, but they are not ready");
        }

        int totalPeers = properties.getOrchestration().getWorkers();
        List<String> chainResponses = stream(modelId, instruction, temperature, maxTokens, history, cancelled)
                .collectList()
                .block()
                .stream()
                .sorted(java.util.Comparator.comparingInt(PeerContribution::number))
                .map(PeerContribution::text)
                .toList();

        log.info("[CHAIN-ORCHESTRATION] {} chain responses completed", totalPeers);
        return formatChainResponses(chainResponses);
    }

    /** Emits chain responses where each model receives the previous model's output. */
    public Flux<PeerContribution> stream(String modelId, String instruction, Double temperature, Integer maxTokens,
                                         List<Message> history, AtomicBoolean cancelled) {
        if (!isReady()) {
            return Flux.error(new IllegalStateException("Chain orchestration contexts are not ready"));
        }

        int totalPeers = properties.getOrchestration().getWorkers();
        
        // Create a sequential chain using Reactor
        return Flux.defer(() -> {
            String[] currentInput = {instruction};
            
            // Model 1 (primary) receives the original instruction
            log.info("[CHAIN-ORCHESTRATION] Modelo 1 recibe: {}", currentInput[0]);
            return Mono.fromCallable(() -> primaryChat.executePrompt(modelId, CHAIN_SYSTEM_PROMPT, currentInput[0],
                            temperature, cancelled, List.of(), maxTokens))
                    .subscribeOn(Schedulers.boundedElastic())
                    .doOnNext(response -> {
                        log.info("[CHAIN-ORCHESTRATION] Modelo 1 responde: {}", response);
                        currentInput[0] = response;
                    })
                    .map(response -> new PeerContribution(1, response))
                    // Chain subsequent models
                    .expand(contribution -> {
                        int currentIndex = contribution.number();
                        if (currentIndex >= totalPeers) {
                            return Mono.empty();
                        }
                        
                        int workerIndex = currentIndex - 1;
                        log.info("[CHAIN-ORCHESTRATION] Modelo {} recibe: {}", currentIndex + 1, currentInput[0]);
                        return Mono.fromCallable(() -> workerPool.executeOnWorker(workerIndex, modelId, CHAIN_SYSTEM_PROMPT,
                                        currentInput[0], temperature, maxTokens, List.of(), cancelled))
                                .subscribeOn(Schedulers.boundedElastic())
                                .doOnNext(response -> {
                                    log.info("[CHAIN-ORCHESTRATION] Modelo {} responde: {}", currentIndex + 1, response);
                                    currentInput[0] = response;
                                })
                                .map(response -> new PeerContribution(currentIndex + 1, response));
                    })
                    .take(totalPeers);
        });
    }

    private String formatChainResponses(List<String> responses) {
        StringBuilder response = new StringBuilder("## Suma Incremental en Cadena (POC)\n\n");
        for (int i = 0; i < responses.size(); i++) {
            response.append("### Modelo ").append(i + 1).append("\n");
            if (i == 0) {
                response.append("Usuario envía: [número inicial]\n");
            } else {
                response.append("Recibe: ").append(responses.get(i - 1).trim()).append("\n");
            }
            response.append("Calcula: ").append(responses.get(i).trim()).append("\n\n");
        }
        return response.toString().trim();
    }

    public String formatContribution(PeerContribution contribution) {
        return "### Modelo " + contribution.number() + "\n" + contribution.text().trim() + "\n\n";
    }
}
