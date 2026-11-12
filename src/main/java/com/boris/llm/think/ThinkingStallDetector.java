package com.boris.llm.think;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Detecta estancamiento del agente en thinking repetitivo sin progreso.
 * Trackea el hash del thinking content y el hash combinado (nombre + argumentos)
 * de las tool calls por iteración. Se resetea al inicio de cada turno de chat.
 */
public class ThinkingStallDetector {

    private final int maxStallIterations;
    private final int maxThinkingIterations;
    private int consecutiveThinkingOnlyIterations;
    private int consecutiveNonProgressiveIterations;
    private String lastThinkingHash;
    private String lastToolCallHash;

    public ThinkingStallDetector(int maxStallIterations, int maxThinkingIterations) {
        this.maxStallIterations = maxStallIterations;
        this.maxThinkingIterations = maxThinkingIterations;
        reset();
    }

    /**
     * Registra el thinking y tool calls de una iteración.
     * @return true si se detectó estancamiento
     */
    public boolean recordThinking(String thinkingContent, List<ToolCallSignature> toolCalls) {
        boolean isThinkingOnly = (toolCalls == null || toolCalls.isEmpty());
        boolean hasThinking = thinkingContent != null && !thinkingContent.isBlank();

        if (isThinkingOnly && hasThinking) {
            consecutiveThinkingOnlyIterations++;
            String currentHash = hashContent(thinkingContent);

            // Detectar thinking repetitivo (mismo contenido consecutivo)
            if (lastThinkingHash != null && lastThinkingHash.equals(currentHash)) {
                consecutiveNonProgressiveIterations++;
            } else {
                consecutiveNonProgressiveIterations = 0;
            }
            lastThinkingHash = currentHash;
            lastToolCallHash = null;
        } else if (!isThinkingOnly) {
            // Hubo tool calls: resetear thinking-only counter
            consecutiveThinkingOnlyIterations = 0;
            // Verificar si las tool calls son las mismas (nombre + argumentos) que la iteración anterior
            String currentToolHash = hashToolCalls(toolCalls);
            if (lastToolCallHash != null && lastToolCallHash.equals(currentToolHash)) {
                consecutiveNonProgressiveIterations++;
            } else {
                consecutiveNonProgressiveIterations = 0;
            }
            lastToolCallHash = currentToolHash;
            lastThinkingHash = null;
        } else {
            // Sin thinking ni tool calls: turno vacío
            consecutiveThinkingOnlyIterations = 0;
            consecutiveNonProgressiveIterations = 0;
            lastThinkingHash = null;
            lastToolCallHash = null;
        }

        return isStallDetected();
    }

    public boolean isStallDetected() {
        return consecutiveNonProgressiveIterations >= maxStallIterations;
    }

    public boolean isThinkingLimitReached() {
        return consecutiveThinkingOnlyIterations >= maxThinkingIterations;
    }

    public int getConsecutiveThinkingOnlyIterations() {
        return consecutiveThinkingOnlyIterations;
    }

    public int getConsecutiveNonProgressiveIterations() {
        return consecutiveNonProgressiveIterations;
    }

    public void reset() {
        consecutiveThinkingOnlyIterations = 0;
        consecutiveNonProgressiveIterations = 0;
        lastThinkingHash = null;
        lastToolCallHash = null;
    }

    private static String hashContent(String content) {
        if (content == null) {
            return "null";
        }
        return Integer.toString(content.hashCode());
    }

    private static String hashToolCalls(List<ToolCallSignature> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return "null";
        }
        String combined = toolCalls.stream()
                .map(tc -> tc.name() + ":" + (tc.arguments() != null ? tc.arguments() : "{}"))
                .sorted()
                .collect(Collectors.joining("||"));
        return Integer.toString(combined.hashCode());
    }

    /**
     * Firma de una tool call: nombre + argumentos. Se usa para detectar repeticiones
     * exactas (mismo tool con mismos argumentos) vs progreso real (distintos argumentos).
     */
    public record ToolCallSignature(String name, String arguments) {
        public ToolCallSignature {
            if (name == null || name.isBlank()) {
                throw new IllegalArgumentException("tool name es requerido");
            }
        }
    }
}
