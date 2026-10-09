package com.boris.cli.ui;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

public class TokenCounter {

    private final int contextWindowLimit;
    private final Encoding encoding;
    private volatile int inputTokens;
    private volatile int outputTokens;

    public TokenCounter(int contextWindowLimit) {
        this.contextWindowLimit = contextWindowLimit;
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        this.encoding = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    public int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return encoding.countTokens(text);
    }

    public void addInputTokens(String text) {
        inputTokens += countTokens(text);
    }

    public void addOutputTokens(String text) {
        outputTokens += countTokens(text);
    }

    public void resetSession() {
        inputTokens = 0;
        outputTokens = 0;
    }

    public int inputTokens() {
        return inputTokens;
    }

    public int outputTokens() {
        return outputTokens;
    }

    public int totalTokens() {
        return inputTokens + outputTokens;
    }

    public int limit() {
        return contextWindowLimit;
    }

    public boolean limitReached() {
        return totalTokens() >= contextWindowLimit;
    }

    public String formatTokens(int tokens) {
        if (tokens >= 1000) {
            return String.format("%.1fk", tokens / 1000.0);
        }
        return String.valueOf(tokens);
    }

    public String plainStatus() {
        return "tokens: " + formatTokens(totalTokens()) + "/" + formatTokens(contextWindowLimit);
    }

    public String statusText() {
        if (limitReached()) {
            return " " + plainStatus() + " (límite alcanzado)";
        }
        return " " + plainStatus();
    }

    public String limitMessage() {
        return "[x] limite de tokens alcanzado (" + formatTokens(contextWindowLimit)
                + "). No se pueden enviar mas mensajes en esta sesion.";
    }
}
