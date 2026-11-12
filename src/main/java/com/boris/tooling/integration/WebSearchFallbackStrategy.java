package com.boris.tooling.integration;

import com.boris.exceptions.WebSearchFallbackException;

/**
 * Estrategia de fallback cuando web_search falla o devuelve resultados irrelevantes.
 * Detecta resultados vacíos o genéricos y ofrece alternativas:
 * - Usar contenido previamente fetchado (web_fetch)
 * - Pedir al usuario que proporcione la URL directamente
 */
public class WebSearchFallbackStrategy {

    private static final String[] IRRELEVANT_PATTERNS = {
        "google chrome support",
        "no se encontraron resultados",
        "sin resultados",
        "no results found",
        "no se pudo encontrar",
        "error en búsqueda"
    };

    private final boolean enabled;

    public WebSearchFallbackStrategy(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Valida si los resultados de búsqueda son relevantes.
     * @param resultJson JSON del resultado de web_search
     * @param query query original
     * @throws WebSearchFallbackException si los resultados son irrelevantes
     */
    public void validateResults(String resultJson, String query) {
        if (!enabled) {
            return;
        }

        if (resultJson == null || resultJson.isBlank()) {
            throw new WebSearchFallbackException(
                "Búsqueda web falló sin resultados para: '" + query + "'. "
                + "Usá contenido previamente fetchado o proporcioná la URL directamente.");
        }

        // Detectar resultados vacíos
        if (resultJson.contains("\"results\":[]") || resultJson.contains("\"results\": []")) {
            throw new WebSearchFallbackException(
                "Búsqueda web sin resultados para: '" + query + "'. "
                + "Usá contenido previamente fetchado o proporcioná la URL directamente.");
        }

        // Detectar patrones de error genérico
        String lower = resultJson.toLowerCase();
        for (String pattern : IRRELEVANT_PATTERNS) {
            if (lower.contains(pattern)) {
                throw new WebSearchFallbackException(
                    "Búsqueda web devolvió resultados irrelevantes para: '" + query + "'. "
                    + "Usá contenido previamente fetchado o proporcioná la URL directamente.");
            }
        }
    }

    /**
     * Extrae el contenido previamente fetchado de una lista de mensajes de conversación.
     * Busca mensajes de tool con nombre "web_fetch" que tengan éxito.
     */
    public static String extractPreviousFetchContent(java.util.List<String> conversationMessages) {
        StringBuilder fetchedContent = new StringBuilder();
        boolean inFetch = false;
        for (String msg : conversationMessages) {
            if (msg.contains("\"name\":\"web_fetch\"") || msg.contains("web_fetch")) {
                inFetch = true;
                continue;
            }
            if (inFetch && msg.contains("\"success\":true")) {
                // Extraer el campo content del resultado de web_fetch
                int contentIdx = msg.indexOf("\"content\"");
                if (contentIdx >= 0) {
                    int valueStart = msg.indexOf(':', contentIdx) + 1;
                    int valueEnd = msg.indexOf('}', valueStart);
                    if (valueEnd < 0) valueEnd = msg.length();
                    String contentValue = msg.substring(valueStart, valueEnd).trim();
                    if (contentValue.startsWith("\"") && contentValue.endsWith("\"")) {
                        contentValue = contentValue.substring(1, contentValue.length() - 1);
                    }
                    fetchedContent.append(contentValue).append("\n");
                }
                inFetch = false;
            }
        }
        return fetchedContent.toString().trim();
    }
}
