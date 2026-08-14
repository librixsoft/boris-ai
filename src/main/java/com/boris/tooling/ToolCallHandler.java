package com.boris.tooling;

import java.util.Map;
import java.util.function.Function;

public class ToolCallHandler {

    private final Function<String, Map<String, Object>> parser;

    public ToolCallHandler() {
        this.parser = DefaultArgParser::parse;
    }

    public ToolCallHandler(Function<String, Map<String, Object>> argParser) {
        this.parser = argParser;
    }

    public String handle(ToolRegistry registry, String toolName, String argsJson) {
        Map<String, Object> arguments = parser.apply(argsJson);
        return registry.execute(toolName, arguments);
    }

    private static class DefaultArgParser {
        static Map<String, Object> parse(String json) {
            if (json == null || json.isBlank()) {
                return Map.of();
            }
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                return mapper.readValue(json, java.util.Map.class);
            } catch (Exception e) {
                throw new com.boris.exceptions.BorisException("Failed to parse tool arguments JSON: " + json, e);
            }
        }
    }
}
