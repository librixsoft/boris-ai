package com.boris.llm.think;

import java.util.List;
import java.util.Set;
import java.util.HashSet;

/**
 * Detecta estancamiento del agente en thinking repetitivo sin progreso.
 * Trackea el hash del thinking content y los nombres de tool calls por iteración.
 * Se resetea al inicio de cada turno de chat.
 */
public class ThinkingStallDetector {

    private final int maxStallIterations;
    private final int maxThinkingIterations;
    private int consecutiveThinkingOnlyIterations;
    private int consecutiveNonProgressiveIterations;
    private String lastThinkingHash;
    private Set<String> lastToolCallNames;

    public ThinkingStallDetector(int maxStallIterations, int maxThinkingIterations) {
        this.maxStallIterations = maxStallIterations;
        this.maxThinkingIterations = maxThinkingIterations;
        reset();
    }

    /**
     * Registra el thinking y tool calls de una iteración.
     * @return true si se detectó estancamiento
     */
    public boolean recordThinking(String thinkingContent, List<String> toolCallNames) {
        boolean isThinkingOnly = (toolCallNames == null || toolCallNames.isEmpty());
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
            lastToolCallNames = new HashSet<>(toolCallNames != null ? toolCallNames : List.of());
        } else if (!isThinkingOnly) {
            // Hubo tool calls: resetear thinking-only counter
            consecutiveThinkingOnlyIterations = 0;
            // Verificar si las tool calls son las mismas que la iteración anterior
            if (lastToolCallNames != null && lastToolCallNames.equals(new HashSet<>(toolCallNames))) {
                consecutiveNonProgressiveIterations++;
            } else {
                consecutiveNonProgressiveIterations = 0;
                lastToolCallNames = new HashSet<>(toolCallNames != null ? toolCallNames : List.of());
            }
            lastThinkingHash = null;
        } else {
            // Sin thinking ni tool calls: turno vacío
            consecutiveThinkingOnlyIterations = 0;
            consecutiveNonProgressiveIterations = 0;
            lastThinkingHash = null;
            lastToolCallNames = null;
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
        lastToolCallNames = null;
    }

    private static String hashContent(String content) {
        if (content == null) {
            return "null";
        }
        return Integer.toString(content.hashCode());
    }
}
