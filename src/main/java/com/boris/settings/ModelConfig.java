package com.boris.settings;

public class ModelConfig {
    private String baseUrl;
    private String name;

    public ModelConfig() {}

    public ModelConfig(String baseUrl, String name) {
        this.baseUrl = baseUrl;
        this.name = name;
    }

    public String getBaseUrl() { return baseUrl; }
    public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
}
