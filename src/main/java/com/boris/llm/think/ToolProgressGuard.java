package com.boris.llm.think;

/**
 * Guardia de progreso de herramientas: detecta falta de progreso informativo
 * entre iteraciones consecutivas del bucle de tools. Mantiene un hash del
 * resultado de la última herramienta ejecutada. Para herramientas de browser,
 * la señal de progreso es el progress_token = screen_hash_after + URL + acción,
 * que cambia cuando la pantalla o la URL cambian.
 */
public class ToolProgressGuard {

    private static final int MAX_NO_PROGRESS_ITERATIONS = 3;

    private String lastSignature;
    private int noProgressCount;

    /**
     * Registra el resultado de una herramienta ejecutada.
     * @param toolName nombre de la herramienta
     * @param arguments argumentos JSON
     * @param result resultado JSON de la herramienta
     * @return true si se detectó un bucle (mismo resultado consecutivo >= umbral)
     */
    public boolean recordResult(String toolName, String arguments, String result) {
        String currentSignature = buildSignature(toolName, arguments, result);
        if (lastSignature == null) {
            lastSignature = currentSignature;
            noProgressCount = 1;
            return false;
        }
        if (currentSignature.equals(lastSignature)) {
            noProgressCount++;
            if (noProgressCount >= MAX_NO_PROGRESS_ITERATIONS) {
                return true;
            }
        } else {
            noProgressCount = 1;
            lastSignature = currentSignature;
        }
        return false;
    }

    /**
     * Construye la firma de progreso. Para herramientas de browser, extrae el
     * progress_token = screen_hash_after + URL + acción del resultado JSON,
     * sin ecos de argumentos: así los reintentos con variaciones cosméticas
     * sobre una pantalla sin cambios sí cuentan como falta de progreso.
     * Para el resto, usa el hash del resultado completo.
     */
    private String buildSignature(String toolName, String arguments, String result) {
        String progressToken = extractProgressToken(result);
        if (progressToken != null) {
            return toolName + ":" + progressToken;
        }
        return toolName + ":" + ToolCallCanonicalizer.canonicalize(arguments) + ":" + hashResult(result);
    }

    /**
     * Extrae el progress_token = screen_hash_after + url_after + acción del resultado JSON.
     * Devuelve null si el resultado no es JSON o no contiene los campos requeridos.
     */
    private String extractProgressToken(String result) {
        if (result == null || result.isBlank()) {
            return null;
        }
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result);
            if (!node.isObject()) {
                return null;
            }
            var screenHash = node.get("screen_hash_after");
            var url = node.get("url_after");
            var action = node.get("action");
            if (screenHash == null || url == null || action == null) {
                return null;
            }
            return screenHash.asText() + "|" + url.asText() + "|" + action.asText();
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Limpia el estado entre turnos de chat.
     */
    public void reset() {
        lastSignature = null;
        noProgressCount = 0;
    }

    private String hashResult(String result) {
        if (result == null) {
            return "null";
        }
        return Integer.toString(result.hashCode());
    }
}
