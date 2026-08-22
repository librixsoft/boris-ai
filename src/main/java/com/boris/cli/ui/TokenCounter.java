package com.boris.cli.ui;

public class TokenCounter {

    private final int contextWindowLimit;
    private volatile int generatedTokens;
    private volatile RamGauge ramGauge;

    public TokenCounter(int contextWindowLimit) {
        this.contextWindowLimit = contextWindowLimit;
    }

    public void attachRamGauge(RamGauge ramGauge) {
        this.ramGauge = ramGauge;
    }

    public void addTokens(int amount) {
        generatedTokens += amount;
    }

    public void resetSession() {
        generatedTokens = 0;
    }

    public void resetTokensOnly() {
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
        StringBuilder sb = new StringBuilder();
        sb.append(" t/gpu: ").append(formatTokens(generatedTokens)).append("/").append(formatTokens(contextWindowLimit));
        if (ramGauge != null) {
            sb.append(" | t/ram left: ").append(ramGauge.render());
        }
        return sb.toString();
    }

    public String statusText() {
        if (limitReached()) {
            return " " + plainStatus() + " (historial guardado)";
        }
        return " " + plainStatus();
    }

    public String limitMessage() {
        return "⚠ límite de contexto alcanzado (" + formatTokens(contextWindowLimit)
                + "). Historial guardado en memoria persistente (H2). Reiniciando contador...";
    }
}
