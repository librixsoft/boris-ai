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
            Eres un modelo de lenguaje en una cadena de conversación con otros modelos.
            Tu objetivo es continuar la conversación de forma natural y coherente.
            IMPORTANTE: Nunca repitas exactamente la misma respuesta que el modelo anterior.
            Si recibes un saludo, responde con una pregunta diferente o un tema nuevo.
            Si recibes una pregunta, responde y luego haz una pregunta relacionada.
            Sé conciso (máximo 15 palabras) pero natural.
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
        
        // Build the chain sequentially
        List<PeerContribution> contributions = new ArrayList<>();
        String currentInput = instruction;
        
        // Model 1 (primary) receives the original instruction
        log.info("[CHAIN-ORCHESTRATION] Modelo 1 recibe: {}", currentInput);
        String response1 = primaryChat.executePrompt(modelId, CHAIN_SYSTEM_PROMPT, currentInput,
                temperature, cancelled, List.of(), maxTokens);
        log.info("[CHAIN-ORCHESTRATION] Modelo 1 responde: {}", response1);
        contributions.add(new PeerContribution(1, response1));
        
        // Subsequent models receive the previous model's output
        for (int index = 1; index < totalPeers; index++) {
            currentInput = response1; // Each model receives the previous response
            log.info("[CHAIN-ORCHESTRATION] Modelo {} recibe: {}", index + 1, currentInput);
            String response = workerPool.executeOnWorker(index - 1, modelId, CHAIN_SYSTEM_PROMPT, 
                    currentInput, temperature, maxTokens, List.of(), cancelled);
            log.info("[CHAIN-ORCHESTRATION] Modelo {} responde: {}", index + 1, response);
            contributions.add(new PeerContribution(index + 1, response));
            response1 = response; // Update for next iteration
        }
        
        return Flux.fromIterable(contributions);
    }

    private String formatChainResponses(List<String> responses) {
        StringBuilder response = new StringBuilder("## Conversación en Cadena (POC)\n\n");
        for (int i = 0; i < responses.size(); i++) {
            response.append("### Modelo ").append(i + 1).append("\n");
            if (i == 0) {
                response.append("Usuario envía: [mensaje original]\n");
            } else {
                response.append("Recibe: ").append(responses.get(i - 1).trim()).append("\n");
            }
            response.append("Responde: ").append(responses.get(i).trim()).append("\n\n");
        }
        return response.toString().trim();
    }

    public String formatContribution(PeerContribution contribution) {
        return "### Modelo " + contribution.number() + "\n" + contribution.text().trim() + "\n\n";
    }
}
