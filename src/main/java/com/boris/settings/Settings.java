package com.boris.settings;

import java.util.Map;

public class Settings {
    private ModelConfig model;
    private Map<String, String> env;
    private String systemPrompt;
    private Integer maxHistorySize;
    private Boolean enableHistory;
    private Boolean enforceSequentialExecution;
    private Double temperature;
    private Integer contextWindow;
    private MemoryConfig memory;

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
    
    public Boolean getEnableHistory() { return enableHistory; }
    public void setEnableHistory(Boolean enableHistory) { this.enableHistory = enableHistory; }
    
    public Boolean getEnforceSequentialExecution() { return enforceSequentialExecution; }
    public void setEnforceSequentialExecution(Boolean enforceSequentialExecution) { this.enforceSequentialExecution = enforceSequentialExecution; }
    
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    
    public Integer getContextWindow() { return contextWindow; }
    public void setContextWindow(Integer contextWindow) { this.contextWindow = contextWindow; }

    public MemoryConfig getMemory() { return memory; }
    public void setMemory(MemoryConfig memory) { this.memory = memory; }

    public static class MemoryConfig {
        private Boolean enabled;
        private Integer maxContextTokens;
        private Integer maxHistoryMessages;
        private String sessionId;

        public MemoryConfig() {}

        public Boolean getEnabled() { return enabled; }
        public void setEnabled(Boolean enabled) { this.enabled = enabled; }
        public Integer getMaxContextTokens() { return maxContextTokens; }
        public void setMaxContextTokens(Integer maxContextTokens) { this.maxContextTokens = maxContextTokens; }
        public Integer getMaxHistoryMessages() { return maxHistoryMessages; }
        public void setMaxHistoryMessages(Integer maxHistoryMessages) { this.maxHistoryMessages = maxHistoryMessages; }
        public String getSessionId() { return sessionId; }
        public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    }
}
