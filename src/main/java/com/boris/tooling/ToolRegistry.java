package com.boris.tooling;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class ToolRegistry {

    private final Map<String, ToolDefinition> tools = new ConcurrentHashMap<>();
    private final Map<String, ToolExecutor> executors = new ConcurrentHashMap<>();

    @FunctionalInterface
    public interface ToolExecutor {
        String execute(Map<String, Object> arguments);
    }

    public void register(ToolDefinition definition, ToolExecutor executor) {
        tools.put(definition.name(), definition);
        executors.put(definition.name(), executor);
    }

    public void unregister(String toolName) {
        tools.remove(toolName);
        executors.remove(toolName);
    }

    public boolean contains(String toolName) {
        return tools.containsKey(toolName);
    }

    public Collection<ToolDefinition> getAll() {
        return Collections.unmodifiableCollection(tools.values());
    }

    public ToolDefinition getByName(String name) {
        return tools.get(name);
    }

    public String execute(String toolName, Map<String, Object> arguments) {
        ToolExecutor executor = executors.get(toolName);
        if (executor == null) {
            throw new IllegalArgumentException("Unknown tool: " + toolName);
        }
        return executor.execute(arguments != null ? arguments : Map.of());
    }

    public int size() {
        return tools.size();
    }
}
