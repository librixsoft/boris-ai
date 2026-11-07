package com.boris.llm.think;

import java.util.Map;

import com.boris.settings.Settings;

/**
 * Resolvedor de modo de razonamiento (thinking mode) a partir de la configuración.
 * Extrae el valor de "think" de las opciones del modelo o del campo thinkingMode.
 */
public final class ThinkModeResolver {

    private ThinkModeResolver() {
    }

    public static String resolveThinkMode(Settings settings) {
        if (settings == null) {
            return null;
        }
        if (settings.getOptions() != null) {
            Object thinkValue = settings.getOptions().get("think");
            if (thinkValue instanceof String thinkStr && !thinkStr.isBlank()) {
                return thinkStr;
            }
        }
        if (settings.getModel() != null && settings.getModel().getOptions() != null) {
            Object thinkValue = settings.getModel().getOptions().get("think");
            if (thinkValue instanceof String thinkStr && !thinkStr.isBlank()) {
                return thinkStr;
            }
        }
        if (settings.getThinkingMode() != null && !settings.getThinkingMode().isBlank()) {
            return settings.getThinkingMode();
        }
        return null;
    }
}
