package com.boris.settings;

import java.util.Map;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class Settings {
    private ModelConfig model;
    private Map<String, String> env;
    private String systemPrompt;
    private Integer maxHistorySize;
    private Boolean enableHistory;
    private Boolean enforceSequentialExecution;
    private Double temperature;
    private Integer contextWindow;
    private Boolean thinkingEnabled;
    private String thinkingMode;
    private Map<String, Object> options;

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

    public Boolean getThinkingEnabled() { return thinkingEnabled; }
    public void setThinkingEnabled(Boolean thinkingEnabled) { this.thinkingEnabled = thinkingEnabled; }

    public String getThinkingMode() { return thinkingMode; }
    public void setThinkingMode(String thinkingMode) { this.thinkingMode = thinkingMode; }

    public Map<String, Object> getOptions() { return options; }
    public void setOptions(Map<String, Object> options) { this.options = options; }

    @JsonIgnore
    public String getReasoningEffort() {
        // Check options map for "think" parameter from Ollama granite4.2
        if (options != null && options.containsKey("think")) {
            Object thinkValue = options.get("think");
            if (thinkValue instanceof String) {
                String thinkStr = ((String) thinkValue).toLowerCase();
                // Map the values to match Spring AI expectations
                return switch (thinkStr) {
                    case "high" -> "high";
                    case "medium" -> "medium";
                    case "low" -> "low";
                    default -> "medium";
                };
            }
        }
        // Fallback to model-specific reasoning effort
        if (model != null && model.getReasoningEffort() != null) {
            return model.getReasoningEffort();
        }
        return null;
    }
}
