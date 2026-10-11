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

    public static String iterateIfEnabled(String content, String filePath) {
        if (instance != null && instance.isEnabled()) {
            return instance.iterate(content, filePath);
        }
        return content;
    }

    public static void setEnabled(boolean enabled) {
        if (instance != null) {
            instance.setEnabled(enabled);
        }
    }
}
