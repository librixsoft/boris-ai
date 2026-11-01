package com.boris.settings;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public class DrafterConfig {
    private Boolean enabled;
    private String name;
    private Double temperature;
    private Integer maxTokens;

    public DrafterConfig() {}

    public DrafterConfig(Boolean enabled, String name, Double temperature, Integer maxTokens) {
        this.enabled = enabled;
        this.name = name;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    public Boolean getEnabled() { return enabled; }
    public void setEnabled(Boolean enabled) { this.enabled = enabled; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getTemperature() { return temperature; }
    public void setTemperature(Double temperature) { this.temperature = temperature; }
    public Integer getMaxTokens() { return maxTokens; }
    public void setMaxTokens(Integer maxTokens) { this.maxTokens = maxTokens; }
}
