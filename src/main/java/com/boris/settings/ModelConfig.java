package com.boris.settings;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class ModelConfig {
    private String baseUrl;
    private String name;
    private String reasoningEffort;
    private java.util.Map<String, Object> options;

    public ModelConfig() {}

    public ModelConfig(String baseUrl, String name) {
        this.baseUrl = baseUrl;
        this.name = name;
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    @JsonIgnore
    public String getReasoningEffort() {
        if (reasoningEffort != null && !reasoningEffort.isBlank()) {
            return reasoningEffort;
        }
        if (options != null && options.get("think") != null) {
            return String.valueOf(options.get("think"));
        }
        return null;
    }

    public void setReasoningEffort(String reasoningEffort) {
        this.reasoningEffort = reasoningEffort;
    }

    public java.util.Map<String, Object> getOptions() {
        return options;
    }

    public void setOptions(java.util.Map<String, Object> options) {
        this.options = options;
    }
}
