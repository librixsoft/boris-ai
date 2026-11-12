package com.boris.context;

import java.security.MessageDigest;
import java.util.UUID;

import com.boris.exceptions.BorisException;

/**
 * Contexto de sesión de búsqueda. Almacena el sessionId en un ThreadLocal
 * para que esté disponible durante todo el turno del chat, con un fallback
 * global (volatile) para hilos que no heredan el ThreadLocal (pools, GUI).
 */
public final class SearchSessionContext {

    private SearchSessionContext() {
    }

    private static final ThreadLocal<String> sessionId = new ThreadLocal<>();

    // Fuente única de verdad para hilos que no heredan ThreadLocal
    private static volatile String globalSessionId;

    public static void setSessionId(String id) {
        sessionId.set(id);
        globalSessionId = id;
    }

    public static String getSessionId() {
        String id = sessionId.get();
        if (id == null || id.isBlank()) {
            id = globalSessionId;
        }
        return id;
    }

    /**
     * Devuelve el sessionId o lanza {@link BorisException} si no hay sesión
     * seteada. Usado por componentes que requieren obligatoriamente una sesión.
     */
    public static String requireSessionId() {
        String id = getSessionId();
        if (id == null || id.isBlank()) {
            throw new BorisException("No session ID set for search context");
        }
        return id;
    }

    public static void clear() {
        sessionId.remove();
        globalSessionId = null;
    }

    private static final int SESSION_ID_HASH_LENGTH = 16;

    public static String generateSessionId() {
        String seed = UUID.randomUUID().toString() + System.nanoTime();
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(seed.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString().substring(0, SESSION_ID_HASH_LENGTH);
        } catch (Exception e) {
            throw new BorisException("No se pudo generar sessionId", e);
        }
    }
}
