package com.boris.librixsoft.level4.wrapper.llama;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LlamaChatServiceJsonExtractionTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void extractsJsonAfterNaturalLanguagePrefix() throws Exception {
        String response = "¡Claro! ejecutando:\n"
                + "{\"actions\":[{\"tool\":\"readFile\",\"args\":{\"path\":\"/tmp/app.txt\"}}]}";

        JsonNode root = MAPPER.readTree(extractJson(response));

        assertEquals("readFile", root.path("actions").get(0).path("tool").asText());
        assertEquals("/tmp/app.txt", root.path("actions").get(0).path("args").path("path").asText());
    }

    @Test
    void keepsBracesInsideJsonStrings() throws Exception {
        String response = "texto previo "
                + "{\"actions\":[{\"tool\":\"editFile\",\"args\":{\"path\":\"/tmp/{name}.txt\",\"content\":\"valor {x}\"}}]}"
                + " texto final";

        JsonNode root = MAPPER.readTree(extractJson(response));

        JsonNode args = root.path("actions").get(0).path("args");
        assertEquals("/tmp/{name}.txt", args.path("path").asText());
        assertEquals("valor {x}", args.path("content").asText());
    }

    @Test
    void returnsNullWhenThereIsNoJsonObjectOrArray() throws Exception {
        assertNull(extractJson("¡Sin acciones por ejecutar!"));
    }

    private static String extractJson(String response) throws Exception {
        LlamaChatService service = new LlamaChatService(null, null, null, null, null, null, null, null);
        Method method = LlamaChatService.class.getDeclaredMethod("extractJsonFromResponse", String.class);
        method.setAccessible(true);
        return (String) method.invoke(service, response);
    }
}
