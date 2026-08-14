package com.boris.settings;

import java.util.Map;

public class Settings {
    private ModelConfig model;
    private Map<String, String> env;
    private String systemPrompt;

    public Settings() {}

    public Settings(ModelConfig model, Map<String, String> env) {
        this.model = model;
        this.env = env;
    }

    public static Settings defaultSettings() {
        return new Settings(
            new ModelConfig("http://localhost:11434", "qwen3.6-35b-64k"),
            Map.of("OLLAMA_API_KEY", "ollama")
        );
    }

    public ModelConfig getModel() { return model; }
    public void setModel(ModelConfig model) { this.model = model; }
    public Map<String, String> getEnv() { return env; }
    public void setEnv(Map<String, String> env) { this.env = env; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
}
