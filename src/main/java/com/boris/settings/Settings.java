package com.boris.settings;

import java.util.Map;

public class Settings {
    private ModelConfig model;
    private Map<String, String> env;
    private String systemPrompt;
    private Integer maxHistorySize;
    private Boolean enforceSequentialExecution;
    private Double temperature;
    private Integer contextWindow;

    public Settings() {}

    public Settings(ModelConfig model, Map<String, String> env) {
        this.model = model;
        this.env = env;
    }

    public ModelConfig getModel() { return model; }
    public void setModel(ModelConfig model) { this.model = model; }
    public Map<String, String> getEnv() { return env; }
    public void setEnv(Map<String, String> env) { this.env = env; }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    
    public Integer getMaxHistorySize() { return maxHistorySize; }
    public void setMaxHistorySize(Integer maxHistorySize) { this.maxHistorySize = maxHistorySize; }
    
    public Boolean getEnforceSequentialExecution() { return enforceSequentialExecution; }
    public void setEnforceSequentialExecution(Boolean enforceSequentialExecution) { this.enforceSequentialExecution = enforceSequentialExecution; }
    
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    
    public Integer getContextWindow() { return contextWindow; }
    public void setContextWindow(Integer contextWindow) { this.contextWindow = contextWindow; }
}
