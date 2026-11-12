package com.boris.llm.think;

import java.util.HashMap;
import java.util.Map;

/**
 * Deduplicador inteligente de tool calls por turno.
 * Reemplaza la política punitiva de {@link RepeatedToolCallGuard} (que mataba el proceso
 * tras 2 llamadas) por un deduplicador que:
 * <ul>
 *   <li>Normaliza rutas y comandos para generar una clave canónica {@code tool|args}.</li>
 *   <li>Si la clave ya fue ejecutada en el turno: no se vuelve a ejecutar; se responde al
 *     modelo con el resultado previo.</li>
 *   <li>Si insiste 3 veces idénticas consecutivas, se detiene con mensaje controlado.</li>
 *   <li>Si ocurre una herramienta de modificación ({@code write_file}, {@code apply_edit},
 *     {@code delete_file}), se invalida el caché de lecturas previas.</li>
 * </ul>
 */
public class ToolCallDeduplicator {

    private static final int MAX_IDENTICAL_CONSECUTIVE = 3;

    private final Map<String, String> executedResults = new HashMap<>(); // clave -> último resultado
    private final Map<String, Integer> consecutiveCount = new HashMap<>(); // clave -> conteo consecutivo
    private String lastKey;
    private int consecutiveForCurrentKey;

    /**
     * Registra una ejecución de herramienta y devuelve el resultado a devolver al modelo.
     * @param toolName nombre de la herramienta
     * @param arguments argumentos JSON
     * @param previousResult resultado previo si la clave ya existe (null si no existe)
     * @return {@code null} si se debe ejecutar la herramienta normalmente;
     *         {@code "[DUPLICADO] ..."} si la clave ya fue ejecutada y se debe devolver el resultado previo;
     *         {@code "[STOPPED] ..."} si se alcanzó el límite de duplicados consecutivos.
     */
    public String recordExecution(String toolName, String arguments, String previousResult) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName es requerido");
        }
        String key = canonicalKey(toolName, arguments);

        boolean isModification = isModificationTool(toolName);
        if (isModification) {
            // Invalidar caché de lecturas previas tras modificación
            executedResults.clear();
            consecutiveCount.clear();
            lastKey = null;
            consecutiveForCurrentKey = 0;
        }

        if (key.equals(lastKey)) {
            consecutiveForCurrentKey++;
        } else {
            lastKey = key;
            consecutiveForCurrentKey = 1;
        }

        if (executedResults.containsKey(key)) {
            // Ya ejecutada en este turno: devolver resultado previo
            int count = consecutiveCount.merge(key, 1, Integer::sum);
            if (count >= MAX_IDENTICAL_CONSECUTIVE) {
                return buildStoppedMessage(toolName, key);
            }
            return buildDuplicateMessage(toolName, previousResult != null ? previousResult : "");
        }

        // Primera ejecución: guardar resultado
        executedResults.put(key, previousResult != null ? previousResult : "");
        consecutiveCount.put(key, 1);
        return null;
    }

    /**
     * Pre-check: verifica si la próxima ejecución sería un duplicado sin incrementar contadores.
     * @return true si la clave ya fue ejecutada (duplicado), false si es nueva.
     */
    public boolean isDuplicate(String toolName, String arguments) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName es requerido");
        }
        String key = canonicalKey(toolName, arguments);
        return executedResults.containsKey(key);
    }

    public void reset() {
        executedResults.clear();
        consecutiveCount.clear();
        lastKey = null;
        consecutiveForCurrentKey = 0;
    }

    private String canonicalKey(String toolName, String arguments) {
        String normalizedArgs = normalizeArguments(arguments);
        return toolName + "|" + normalizedArgs;
    }

    private String normalizeArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return "{}";
        }
        try {
            return com.boris.llm.think.ToolCallCanonicalizer.canonicalize(arguments);
        } catch (Exception e) {
            return arguments.trim();
        }
    }

    private boolean isModificationTool(String toolName) {
        return "write_file".equals(toolName)
                || "apply_edit".equals(toolName)
                || "revert_edit".equals(toolName)
                || "delete_file".equals(toolName)
                || "delete_directory".equals(toolName)
                || "create_directory".equals(toolName);
    }

    private String buildDuplicateMessage(String toolName, String previousResult) {
        StringBuilder sb = new StringBuilder();
        sb.append("[DUPLICADO] Ya ejecutaste esta llamada con los mismos argumentos.\n");
        sb.append("Resultado anterior:\n");
        sb.append(previousResult);
        sb.append("\nNo la repitas: utiliza esta información para continuar.");
        return sb.toString();
    }

    private String buildStoppedMessage(String toolName, String key) {
        StringBuilder sb = new StringBuilder();
        sb.append("[STOPPED] La herramienta '").append(toolName).append("' se repitió ");
        sb.append(MAX_IDENTICAL_CONSECUTIVE).append(" veces idénticas consecutivas. ");
        sb.append("Deteniendo el turno para evitar un bucle infinito.");
        return sb.toString();
    }
}
