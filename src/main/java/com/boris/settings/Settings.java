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

    // Agent thinking limits
    private Integer agentThinkingMaxIterations;
    private Integer agentThinkingMaxStallIterations;
    private Boolean agentThinkingEnableStallDetection;
    private Boolean agentThinkingFallbackOnSearchFailure;
    
    // Parallel OS Execution settings
    private Boolean parallelOsExecution;
    private Integer maxConcurrentIoThreads;
    private Integer maxConcurrentCommandExecutions;
    private Long ioTimeoutMs;

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

    public Integer getAgentThinkingMaxIterations() { return agentThinkingMaxIterations; }
    public void setAgentThinkingMaxIterations(Integer agentThinkingMaxIterations) { this.agentThinkingMaxIterations = agentThinkingMaxIterations; }

    public Integer getAgentThinkingMaxStallIterations() { return agentThinkingMaxStallIterations; }
    public void setAgentThinkingMaxStallIterations(Integer agentThinkingMaxStallIterations) { this.agentThinkingMaxStallIterations = agentThinkingMaxStallIterations; }

    public Boolean getAgentThinkingEnableStallDetection() { return agentThinkingEnableStallDetection; }
    public void setAgentThinkingEnableStallDetection(Boolean agentThinkingEnableStallDetection) { this.agentThinkingEnableStallDetection = agentThinkingEnableStallDetection; }

    public Boolean getAgentThinkingFallbackOnSearchFailure() { return agentThinkingFallbackOnSearchFailure; }
    public void setAgentThinkingFallbackOnSearchFailure(Boolean agentThinkingFallbackOnSearchFailure) { this.agentThinkingFallbackOnSearchFailure = agentThinkingFallbackOnSearchFailure; }

    // Parallel OS Execution accessors
    public Boolean getParallelOsExecution() { return parallelOsExecution; }
    public void setParallelOsExecution(Boolean parallelOsExecution) { this.parallelOsExecution = parallelOsExecution; }
    
    public Integer getMaxConcurrentIoThreads() { return maxConcurrentIoThreads; }
    public void setMaxConcurrentIoThreads(Integer maxConcurrentIoThreads) { this.maxConcurrentIoThreads = maxConcurrentIoThreads; }
    
    public Integer getMaxConcurrentCommandExecutions() { return maxConcurrentCommandExecutions; }
    public void setMaxConcurrentCommandExecutions(Integer maxConcurrentCommandExecutions) { this.maxConcurrentCommandExecutions = maxConcurrentCommandExecutions; }
    
    public Long getIoTimeoutMs() { return ioTimeoutMs; }
    public void setIoTimeoutMs(Long ioTimeoutMs) { this.ioTimeoutMs = ioTimeoutMs; }

    @JsonIgnore
    public String getReasoningEffort() {
        if (model != null && model.getReasoningEffort() != null) {
            return model.getReasoningEffort();
        }
        return null;
    }
}
