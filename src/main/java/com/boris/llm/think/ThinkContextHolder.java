package com.boris.llm.think;

/**
 * Contexto por hilo del think: el modo a enviar ("low"/... o null si va
 * apagado) y el ultimo trace capturado de la respuesta del server.
 */
public final class ThinkContextHolder {

    private static final ThreadLocal<String> THINK_MODE = new ThreadLocal<>();
    private static final ThreadLocal<String> LAST_THINKING = new ThreadLocal<>();

    private ThinkContextHolder() {
    }

    public static void setThinkMode(String thinkMode) {
        if (thinkMode == null || thinkMode.isBlank()) {
            THINK_MODE.remove();
        } else {
            THINK_MODE.set(thinkMode);
        }
    }

    public static String getThinkMode() {
        return THINK_MODE.get();
    }

    public static void setLastThinking(String thinking) {
        if (thinking == null) {
            LAST_THINKING.remove();
        } else {
            LAST_THINKING.set(thinking);
        }
    }

    public static String getLastThinking() {
        return LAST_THINKING.get();
    }

    public static void clearThinkMode() {
        THINK_MODE.remove();
    }

    public static void clearLastThinking() {
        LAST_THINKING.remove();
    }

    public static void clearAll() {
        THINK_MODE.remove();
        LAST_THINKING.remove();
    }
}
