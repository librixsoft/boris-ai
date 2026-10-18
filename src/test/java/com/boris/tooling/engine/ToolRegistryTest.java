package com.boris.tooling.engine;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import com.boris.tooling.annotation.Tool;
import com.boris.tooling.annotation.ToolParam;

class ToolRegistryTest {

    static class SampleToolService {

        @Tool(name = "greet_user", description = "Greets a user by name and age")
        public String greet(
                @ToolParam(description = "User name") String name,
                @ToolParam(description = "User age", required = false) Integer age,
                @ToolParam(description = "Send formal greeting", required = false) Boolean formal) {
            if (Boolean.TRUE.equals(formal)) {
                return "Estimado/a " + name + " (" + age + ")";
            }
            return "Hola " + name + " (" + age + ")";
        }

        @Tool(name = "batch_items", description = "Process a list of items")
        public String processList(@ToolParam(description = "Items list") List<String> items) {
            return "Processed: " + String.join(", ", items);
        }
    }

    @Test
    void testRegisterAndSchemaGeneration() {
        SampleToolService service = new SampleToolService();
        ToolRegistry registry = ToolRegistry.from(service);

        assertTrue(registry.hasTool("greet_user"));
        assertTrue(registry.hasTool("batch_items"));

        List<Map<String, Object>> schemas = registry.getOpenAiToolsSchema();
        assertEquals(2, schemas.size());

        Map<String, Object> greetSchema = schemas.stream()
                .filter(s -> {
                    Map<?, ?> fn = (Map<?, ?>) s.get("function");
                    return "greet_user".equals(fn.get("name"));
                })
                .findFirst().orElseThrow();

        Map<?, ?> fn = (Map<?, ?>) greetSchema.get("function");
        assertEquals("greet_user", fn.get("name"));
        assertEquals("Greets a user by name and age", fn.get("description"));

        Map<?, ?> parameters = (Map<?, ?>) fn.get("parameters");
        assertEquals("object", parameters.get("type"));

        Map<?, ?> properties = (Map<?, ?>) parameters.get("properties");
        assertTrue(properties.containsKey("name"));
        assertTrue(properties.containsKey("age"));
        assertTrue(properties.containsKey("formal"));

        List<?> required = (List<?>) parameters.get("required");
        assertTrue(required.contains("name"));
        assertFalse(required.contains("age"));
    }

    @Test
    void testExecuteTool() throws Exception {
        SampleToolService service = new SampleToolService();
        ToolRegistry registry = ToolRegistry.from(service);

        String jsonArgs = """
                {"name": "Boris", "age": 25, "formal": true}
                """;
        String result = registry.execute("greet_user", jsonArgs);
        assertEquals("Estimado/a Boris (25)", result);

        String listArgs = """
                {"items": ["a", "b", "c"]}
                """;
        String listResult = registry.execute("batch_items", listArgs);
        assertEquals("Processed: a, b, c", listResult);
    }
}
