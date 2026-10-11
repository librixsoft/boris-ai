package com.boris.tooling.quality;

import org.springframework.ai.chat.client.ChatClient;

/**
 * Holder singleton para el QualityIterator.
 * Permite acceso desde las tools sin inyección de dependencias.
 */
public class QualityIteratorHolder {

    private static volatile QualityIterator instance;

    private QualityIteratorHolder() {}

    public static void initialize(ChatClient chatClient) {
        if (instance == null) {
            synchronized (QualityIteratorHolder.class) {
                if (instance == null) {
                    instance = new QualityIterator(chatClient);
                }
            }
        }
    }

    public static QualityIterator get() {
        return instance;
    }

    /**
     * Passthrough neutral - no ejecuta ninguna revisión LLM.
     * La verificación de calidad se realiza en la fase de planificación del chat.
     */
    public static String iterateIfEnabled(String content, String filePath) {
        return content;
    }

    public static void setEnabled(boolean enabled) {
        if (instance != null) {
            instance.setEnabled(enabled);
        }
    }
}
