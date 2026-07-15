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
 * First horizontal orchestration proof of concept.
 *
 * The Java code only assigns and collects work.  It does not ask a model to
 * supervise, rank, or synthesize the other models: all peers return one
 * equally sized contribution and the response is assembled mechanically.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PeerOrchestrationService {

    private static final List<String> WORK_AREAS = List.of(
            "Entiende el objetivo, requisitos y archivos o componentes que probablemente intervienen.",
            "Propón una solución técnica concreta, con cambios pequeños y ordenados.",
            "Busca riesgos, errores, seguridad, compatibilidad y casos límite.",
            "Define cómo integrar y comprobar el resultado: pruebas, pasos manuales y criterios de aceptación.",
            "Identifica dependencias, contratos entre módulos y el orden seguro de los cambios.",
            "Evalúa rendimiento, consumo de recursos y alternativas más simples.",
            "Revisa la experiencia de uso, mensajes de error y documentación necesaria.",
            "Examina compatibilidad hacia atrás, migración de datos y despliegue.",
            "Busca supuestos no comprobados y formula preguntas o verificaciones necesarias.",
            "Propón criterios objetivos para considerar la tarea terminada.",
            "Explora una solución alternativa y compara sus ventajas y desventajas.",
            "Haz una revisión final independiente de coherencia y mantenibilidad."
    );

    private final BorisProperties properties;
    private final LlamaChatService primaryChat;
    private final LlamaWorkerPool workerPool;
    private static final String PEER_SYSTEM_PROMPT = """
            Eres un integrante igual de un equipo. Responde únicamente a la parte asignada.
            No uses herramientas, JSON, scripts, pasos genéricos ni explicaciones de relleno.
            Responde con una sola frase concreta de máximo 25 palabras. Si tu parte no aporta,
            responde exactamente: No aplica.
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
            throw new IllegalStateException("Peer orchestration requires the configured number of initialized contexts, but they are not ready");
        }

        int totalPeers = properties.getOrchestration().getWorkers();
        List<String> contributions = stream(modelId, instruction, temperature, maxTokens, history, cancelled)
                .collectList()
                .block()
                .stream()
                .sorted(java.util.Comparator.comparingInt(PeerContribution::number))
                .map(PeerContribution::text)
                .toList();

        log.info("[PEER-ORCHESTRATION] {} equal peer contributions completed", totalPeers);
        return formatContributions(contributions);
    }

    /** Emits one complete, short contribution as soon as that peer finishes. */
    public Flux<PeerContribution> stream(String modelId, String instruction, Double temperature, Integer maxTokens,
                                         List<Message> history, AtomicBoolean cancelled) {
        if (!isReady()) {
            return Flux.error(new IllegalStateException("Peer orchestration contexts are not ready"));
        }

        int totalPeers = properties.getOrchestration().getWorkers();
        int peerMaxTokens = maxTokens == null ? 48 : Math.min(maxTokens, 48);
        List<String> prompts = IntStream.range(0, totalPeers)
                .mapToObj(index -> peerPrompt(instruction, workAreaFor(index), index + 1, totalPeers))
                .toList();

        List<Mono<PeerContribution>> calls = new ArrayList<>();
        calls.add(Mono.fromCallable(() -> primaryChat.executePrompt(modelId, PEER_SYSTEM_PROMPT, prompts.get(0),
                        temperature, cancelled, List.of(), peerMaxTokens))
                .subscribeOn(Schedulers.boundedElastic())
                .map(text -> new PeerContribution(1, text)));
        for (int index = 1; index < totalPeers; index++) {
            int peerIndex = index;
            calls.add(Mono.fromCallable(() -> workerPool.executeOnWorker(peerIndex - 1, modelId,
                            PEER_SYSTEM_PROMPT, prompts.get(peerIndex), temperature, peerMaxTokens,
                            List.of(), cancelled))
                    .subscribeOn(Schedulers.boundedElastic())
                    .map(text -> new PeerContribution(peerIndex + 1, text)));
        }
        return Flux.merge(calls);
    }

    private String workAreaFor(int index) {
        if (index < WORK_AREAS.size()) {
            return WORK_AREAS.get(index);
        }
        return "Realiza una revisión independiente número " + (index + 1)
                + ", enfocada en un ángulo no cubierto por las demás aportaciones.";
    }

    private String peerPrompt(String instruction, String workArea, int peerNumber, int totalPeers) {
        return "Tarea: " + instruction + "\n"
                + "Parte " + peerNumber + "/" + totalPeers + ": " + workArea;
    }

    private String formatContributions(List<String> contributions) {
        StringBuilder response = new StringBuilder("## Resultado del equipo (POC)\n\n");
        for (int i = 0; i < contributions.size(); i++) {
            response.append("### Integrante ").append(i + 1).append("\n")
                    .append(contributions.get(i).trim()).append("\n\n");
        }
        return response.toString().trim();
    }

    public String formatContribution(PeerContribution contribution) {
        return "### Integrante " + contribution.number() + "\n" + contribution.text().trim() + "\n\n";
    }
}
