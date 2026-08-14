package com.boris.tooling;

import java.util.Map;

public record ToolDefinition(
    String name,
    String description,
    Map<String, Object> parameters
) {
    public ToolDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Tool name cannot be null or blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("Tool description cannot be null or blank");
        }
        parameters = Map.copyOf(parameters != null ? parameters : Map.of());
    }

    public static ToolDefinition of(String name, String description) {
        return new ToolDefinition(name, description, Map.of());
    }

    public static ToolDefinition of(String name, String description, Map<String, Object> parameters) {
        return new ToolDefinition(name, description, parameters);
    }
}
