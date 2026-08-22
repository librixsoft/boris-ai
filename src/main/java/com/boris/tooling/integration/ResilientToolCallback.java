package com.boris.tooling.integration;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

public class ResilientToolCallback implements ToolCallback {

    private static final String JSON_CONVERSION_ERROR_PREFIX = "Conversion from JSON to";
    private static final String RETRY_INSTRUCTION = " Re-emit the tool call with valid JSON"
            + " and properly escaped string content.";

    private final ToolCallback delegate;

    public ResilientToolCallback(ToolCallback delegate) {
        this.delegate = delegate;
    }

    @Override
    public ToolDefinition getToolDefinition() {
        return delegate.getToolDefinition();
    }

    @Override
    public ToolMetadata getToolMetadata() {
        return delegate.getToolMetadata();
    }

    @Override
    public String getName() {
        return delegate.getName();
    }

    @Override
    public String getDescription() {
        return delegate.getDescription();
    }

    @Override
    public String getInputTypeSchema() {
        return delegate.getInputTypeSchema();
    }

    @Override
    public String call(String toolInput) {
        try {
            return delegate.call(toolInput);
        } catch (Exception e) {
            if (isJsonConversionFailure(e)) {
                return formatError();
            }
            throw e;
        }
    }

    @Override
    public String call(String toolInput, ToolContext toolContext) {
        try {
            return delegate.call(toolInput, toolContext);
        } catch (Exception e) {
            if (isJsonConversionFailure(e)) {
                return formatError();
            }
            throw e;
        }
    }

    private boolean isJsonConversionFailure(Throwable failure) {
        Throwable current = failure;
        while (current != null) {
            if (current instanceof IllegalStateException && current.getMessage() != null
                    && current.getMessage().startsWith(JSON_CONVERSION_ERROR_PREFIX)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    private String formatError() {
        return "{\"success\":false,\"message\":\"" + delegate.getName()
                + " rejected the call: arguments were not valid JSON." + RETRY_INSTRUCTION + "\"}";
    }
}
