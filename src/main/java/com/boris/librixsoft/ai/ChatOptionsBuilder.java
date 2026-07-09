package com.boris.librixsoft.ai;

public class ChatOptionsBuilder {
    private Double temperature;
    private Integer maxTokens;

    public ChatOptionsBuilder temperature(Double temperature) {
        this.temperature = temperature;
        return this;
    }

    public ChatOptionsBuilder maxTokens(Integer maxTokens) {
        this.maxTokens = maxTokens;
        return this;
    }

    public ChatOptions build() {
        return new ChatOptions(temperature, maxTokens);
    }
}
