package com.boris.llm.think;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Detecta estancamiento del agente en thinking repetitivo sin progreso.
 * Trackea el hash del thinking content y el hash combinado (nombre + argumentos)
 * de las tool calls por iteración. Se resetea al inicio de cada turno de chat.
 */
public class ThinkingStallDetector {

    public static final int DEFAULT_MAX_SAME_TOOL_ITERATIONS = 4;

    private final int maxStallIterations;
    private final int maxThinkingIterations;
    private final int maxSameToolIterations;
    private int consecutiveThinkingOnlyIterations;
    private int consecutiveNonProgressiveIterations;
    private int consecutiveSameToolIterations;
    private String lastThinkingHash;
    private String lastToolCallHash;
    private String lastToolNames;

    public ThinkingStallDetector(int maxStallIterations, int maxThinkingIterations) {
        this(maxStallIterations, maxThinkingIterations, DEFAULT_MAX_SAME_TOOL_ITERATIONS);
    }

    public ThinkingStallDetector(int maxStallIterations, int maxThinkingIterations, int maxSameToolIterations) {
        if (maxStallIterations < 1) {
            throw new IllegalArgumentException("maxStallIterations debe ser >= 1");
        }
        if (maxThinkingIterations < 1) {
            throw new IllegalArgumentException("maxThinkingIterations debe ser >= 1");
        }
        if (maxSameToolIterations < 1) {
            throw new IllegalArgumentException("maxSameToolIterations debe ser >= 1");
        }
        this.maxStallIterations = maxStallIterations;
        this.maxThinkingIterations = maxThinkingIterations;
        this.maxSameToolIterations = maxSameToolIterations;
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
            lastToolNames = null;
            consecutiveSameToolIterations = 0;
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

            // Detectar iteraciones consecutivas con el mismo conjunto de herramientas.
            // Para herramientas de modificación (apply_edit, write_file) se incluyen los
            // argumentos canonicalizados para que llamadas con distintos argumentos
            // rompan la racha y no se consideren estancamiento.
            String currentToolNames = toolCalls.stream()
                    .map(tc -> {
                        if (isModificationTool(tc.name())) {
                            return tc.name() + ":" + ToolCallCanonicalizer.canonicalize(tc.arguments());
                        }
                        return tc.name();
                    })
                    .sorted()
                    .collect(Collectors.joining(","));
            if (lastToolNames != null && lastToolNames.equals(currentToolNames)) {
                consecutiveSameToolIterations++;
            } else {
                consecutiveSameToolIterations = 0;
            }
            lastToolNames = currentToolNames;
        } else {
            // Sin thinking ni tool calls: turno vacío
            consecutiveThinkingOnlyIterations = 0;
            consecutiveNonProgressiveIterations = 0;
            consecutiveSameToolIterations = 0;
            lastThinkingHash = null;
            lastToolCallHash = null;
            lastToolNames = null;
        }

        return isStallDetected();
    }

    public boolean isStallDetected() {
        return consecutiveNonProgressiveIterations >= maxStallIterations
                || consecutiveSameToolIterations >= maxSameToolIterations;
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

    public int getConsecutiveSameToolIterations() {
        return consecutiveSameToolIterations;
    }

    public void reset() {
        consecutiveThinkingOnlyIterations = 0;
        consecutiveNonProgressiveIterations = 0;
        consecutiveSameToolIterations = 0;
        lastThinkingHash = null;
        lastToolCallHash = null;
        lastToolNames = null;
    }

    private static boolean isModificationTool(String toolName) {
        return "write_file".equals(toolName)
                || "apply_edit".equals(toolName)
                || "revert_edit".equals(toolName)
                || "delete_file".equals(toolName)
                || "delete_directory".equals(toolName)
                || "create_directory".equals(toolName);
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
                .map(tc -> tc.name() + ":" + ToolCallCanonicalizer.canonicalize(tc.arguments()))
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
