package com.boris.llm.think;

/**
 * Guardia de progreso de herramientas: detecta falta de progreso informativo
 * entre iteraciones consecutivas del bucle de tools. Mantiene un hash del
 * resultado de la última herramienta ejecutada (nombre + argumentos
 * canonicalizados + resultado) y, si el resultado es idéntico en iteraciones
 * consecutivas, incrementa un contador. Al alcanzar el umbral, se considera
 * que hay un bucle y se debe detener el agente.
 */
public class ToolProgressGuard {

    private static final int MAX_NO_PROGRESS_ITERATIONS = 3;

    private String lastSignature;
    private int noProgressCount;

    /**
     * Registra el resultado de una herramienta ejecutada.
     * @return true si se detectó un bucle (mismo resultado consecutivo >= umbral)
     */
    public boolean recordResult(String toolName, String arguments, String result) {
        String currentSignature = toolName + ":" + ToolCallCanonicalizer.canonicalize(arguments) + ":" + hashResult(result);
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
