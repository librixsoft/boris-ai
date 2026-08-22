package com.boris.cli.ui;

public class TokenCounter {

    private final int contextWindowLimit;
    private volatile int generatedTokens;

    public TokenCounter(int contextWindowLimit) {
        this.contextWindowLimit = contextWindowLimit;
    }

    public void addTokens(int amount) {
        generatedTokens += amount;
    }

    public void resetSession() {
        generatedTokens = 0;
    }

    public int generated() {
        return generatedTokens;
    }

    public int limit() {
        return contextWindowLimit;
    }

    public boolean limitReached() {
        return generatedTokens >= contextWindowLimit;
    }

    public boolean wouldExceedLimit(int additionalTokens) {
        return generatedTokens + additionalTokens >= contextWindowLimit;
    }

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / 3.5);
    }

    public String formatTokens(int tokens) {
        if (tokens >= 1000) {
            return (tokens / 1000) + "k";
        }
        return String.valueOf(tokens);
    }

    public String plainStatus() {
        return "tokens: " + formatTokens(generatedTokens) + "/" + formatTokens(contextWindowLimit);
    }

    public String statusText() {
        if (limitReached()) {
            return " " + plainStatus() + " (límite alcanzado)";
        }
        return " " + plainStatus();
    }

    public String limitMessage() {
        return "⚠ límite de contexto alcanzado (" + formatTokens(contextWindowLimit)
                + "). Historial guardado en memoria persistente (H2). Reiniciando contador...";
    }
}
