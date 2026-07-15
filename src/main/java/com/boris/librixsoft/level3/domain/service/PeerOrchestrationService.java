package com.boris.librixsoft.level3.domain.service;

import com.boris.librixsoft.ai.Message;
import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.level4.wrapper.llama.LlamaChatService;
import com.boris.librixsoft.level4.wrapper.llama.LlamaWorkerPool;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

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
    private final SkillService skillService;

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

        String peerSystemPrompt = skillService.getSystemPrompt() + "\n\n"
                + "MODO EQUIPO HORIZONTAL: eres uno de varios pares. No eres jefe ni delegas. "
                + "No ejecutes herramientas ni generes llamadas de herramientas. Entrega solamente tu análisis "
                + "para que el backend lo comparta literalmente con el resto del equipo.\n"
                + "BREVEDAD OBLIGATORIA: responde en un máximo de 3 viñetas y 120 tokens. "
                + "Incluye solo datos directamente útiles para la tarea. No inventes archivos, scripts, "
                + "riesgos, pruebas o pasos técnicos si la tarea no los requiere. Si tu parte no aplica, "
                + "dilo en una sola frase breve.";
        int peerMaxTokens = maxTokens == null ? 160 : Math.min(maxTokens, 160);

        int totalPeers = properties.getOrchestration().getWorkers();
        List<String> prompts = IntStream.range(0, totalPeers)
                .mapToObj(index -> peerPrompt(instruction, workAreaFor(index), index + 1, totalPeers))
                .toList();

        // The first context is simply peer 1. It has no coordinating authority.
        CompletableFuture<String> firstPeer = CompletableFuture.supplyAsync(() -> primaryChat.executePrompt(
                modelId, peerSystemPrompt, prompts.get(0), temperature, cancelled, history, peerMaxTokens));
        CompletableFuture<List<String>> remainingPeers = CompletableFuture.supplyAsync(() -> workerPool.executeAll(
                modelId, peerSystemPrompt, prompts.subList(1, totalPeers), temperature, peerMaxTokens, history, cancelled));

        CompletableFuture.allOf(firstPeer, remainingPeers).join();
        List<String> contributions = new ArrayList<>();
        contributions.add(firstPeer.join());
        contributions.addAll(remainingPeers.join());

        log.info("[PEER-ORCHESTRATION] {} equal peer contributions completed", totalPeers);
        return formatContributions(contributions);
    }

    private String workAreaFor(int index) {
        if (index < WORK_AREAS.size()) {
            return WORK_AREAS.get(index);
        }
        return "Realiza una revisión independiente número " + (index + 1)
                + ", enfocada en un ángulo no cubierto por las demás aportaciones.";
    }

    private String peerPrompt(String instruction, String workArea, int peerNumber, int totalPeers) {
        return "TAREA COMPARTIDA:\n" + instruction + "\n\n"
                + "TU PARTE DEL TRABAJO (integrante " + peerNumber + " de " + totalPeers + "):\n"
                + workArea + "\n\n"
                + "Haz una aportación concreta y autocontenida. No intentes resolver el trabajo de los demás pares. "
                + "Sé breve y no agregues secciones de relleno.";
    }

    private String formatContributions(List<String> contributions) {
        StringBuilder response = new StringBuilder("## Resultado del equipo (POC)\n\n");
        for (int i = 0; i < contributions.size(); i++) {
            response.append("### Integrante ").append(i + 1).append("\n")
                    .append(contributions.get(i).trim()).append("\n\n");
        }
        return response.toString().trim();
    }
}
