package com.boris.tooling.integration;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;

import static org.junit.jupiter.api.Assertions.*;

class ResilientToolCallbackTest {

    private static ToolCallback throwingDelegate(Throwable failure) {
        return new StubToolCallback("write_file", failure);
    }

    @Test
    void call_returnsDelegateResult_onSuccess() {
        ToolCallback delegate = new StubToolCallback("write_file", null);
        ResilientToolCallback resilient = new ResilientToolCallback(delegate);

        assertEquals("ok", resilient.call("{\"path\":\"a.txt\"}"));
    }

    @Test
    void call_returnsErrorResult_whenJsonConversionFails() {
        ResilientToolCallback resilient = new ResilientToolCallback(throwingDelegate(
                new IllegalStateException("Conversion from JSON to Map<String,Object> failed")));

        String result = resilient.call("{invalid json");

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("write_file"));
        assertTrue(result.contains("valid JSON"));
    }

    @Test
    void callWithToolContext_returnsErrorResult_whenJsonConversionFails() {
        ResilientToolCallback resilient = new ResilientToolCallback(throwingDelegate(
                new IllegalStateException("Conversion from JSON to Map<String,Object> failed",
                        new com.fasterxml.jackson.core.JsonProcessingException("bad") {})));

        String result = resilient.call("{invalid json", new ToolContext(java.util.Map.of()));

        assertTrue(result.contains("\"success\":false"));
    }

    @Test
    void call_rethrows_otherExceptions() {
        ResilientToolCallback resilient = new ResilientToolCallback(throwingDelegate(
                new RuntimeException("boom")));

        assertThrows(RuntimeException.class, () -> resilient.call("{}"));
    }

    @Test
    void metadata_delegatesToWrappedCallback() {
        ToolCallback delegate = new StubToolCallback("read_file", null);
        ResilientToolCallback resilient = new ResilientToolCallback(delegate);

        assertEquals("read_file", resilient.getName());
        assertEquals(delegate.getToolDefinition(), resilient.getToolDefinition());
        assertEquals(delegate.getInputTypeSchema(), resilient.getInputTypeSchema());
    }

    private static final class StubToolCallback implements ToolCallback {

        private final String name;
        private final Throwable failure;

        private StubToolCallback(String name, Throwable failure) {
            this.name = name;
            this.failure = failure;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                    .name(name)
                    .description("stub")
                    .inputSchema("{\"type\":\"object\"}")
                    .build();
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "stub";
        }

        @Override
        public String getInputTypeSchema() {
            return "{\"type\":\"object\"}";
        }

        @Override
        public String call(String toolInput) {
            if (failure != null) {
                throwAs(failure);
            }
            return "ok";
        }

        private void throwAs(Throwable t) {
            if (t instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw new IllegalStateException(t);
        }
    }
}
