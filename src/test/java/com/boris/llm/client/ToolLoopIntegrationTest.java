package com.boris.llm.client;

import com.boris.task.TaskExecutionState;
import com.boris.task.TaskStateNotifier;
import com.boris.tooling.annotation.Tool;
import com.boris.tooling.annotation.ToolParam;
import com.boris.tooling.engine.ToolRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de integración: verifica que el agente se detenga cuando detecta bucles
 * de ejecución de herramientas repetitivas o sin progreso informativo.
 */
class ToolLoopIntegrationTest {

    public static class LoopTools {
        @Tool(name = "list_files", description = "Lista archivos")
        public String list_files(@ToolParam(description = "path") String path) {
            return "{\"success\":true,\"entries\":[{\"name\":\"src\",\"type\":\"directory\"}]}";
        }

        @Tool(name = "read_file", description = "Lee archivo")
        public String read_file(@ToolParam(description = "path") String path) {
            return "{\"content\":\"public class Foo {}\"}";
        }
    }

    static class MockLoopClient extends BorisLlmClient {
        private final Queue<ChatResponse> responses = new ArrayDeque<>();
        private final List<ChatRequest> recordedRequests = new java.util.ArrayList<>();

        public MockLoopClient() {
            super("http://localhost:11434");
        }

        public void enqueueResponse(ChatResponse response) {
            responses.add(response);
        }

        @Override
        public ChatResponse chat(ChatRequest request) throws IOException, InterruptedException {
            recordedRequests.add(request);
            ChatResponse next = responses.poll();
            if (next == null) {
                return createTextResponse("Respuesta inesperada");
            }
            return next;
        }

        public List<ChatRequest> getRecordedRequests() {
            return recordedRequests;
        }
    }

    private static ChatResponse createTextResponse(String text) {
        ChatMessage msg = ChatMessage.assistant(text);
        ChatResponse.Choice choice = new ChatResponse.Choice(0, msg, "stop");
        return new ChatResponse("id", List.of(choice));
    }

    private static ChatResponse createToolResponse(String text, String toolName, String toolArgs) {
        ToolCallFunction fn = new ToolCallFunction(toolName, toolArgs);
        ToolCall call = new ToolCall("call_loop_test", "function", fn);
        ChatMessage msg = ChatMessage.assistant(text, List.of(call), null);
        ChatResponse.Choice choice = new ChatResponse.Choice(0, msg, "tool_calls");
        return new ChatResponse("id", List.of(choice));
    }

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        TaskStateNotifier.clearListeners();
        TaskStateNotifier.setState(TaskExecutionState.IDLE);
        registry = ToolRegistry.from(new LoopTools());
    }

    @AfterEach
    void tearDown() {
        TaskStateNotifier.clearListeners();
        TaskStateNotifier.setState(TaskExecutionState.IDLE);
    }

    @Test
    void identicalToolCallsStopAfterLimit() throws Exception {
        MockLoopClient client = new MockLoopClient();

        // El modelo insiste con la misma herramienta y los mismos argumentos
        for (int i = 0; i < 10; i++) {
            client.enqueueResponse(createToolResponse("Listando...", "list_files", "{\"path\":\"/x\"}"));
        }
        client.enqueueResponse(createTextResponse("Fin."));

        List<ChatMessage> conv = List.of(ChatMessage.user("Explora el proyecto"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system-prompt", registry, null, 0.7, null, () -> false
        );

        assertNotNull(turn);
        // Debe detenerse antes de agotar todas las respuestas (límite de repetición)
        assertTrue(client.getRecordedRequests().size() < 10,
                "El agente no debe iterar 10 veces con la misma herramienta; iteraciones: " + client.getRecordedRequests().size());
        assertEquals(TaskExecutionState.INCOMPLETE, client.getTaskState());
        assertTrue(turn.response().contains("bucle") || turn.response().contains("límite") || turn.response().contains("superó"),
                "La respuesta debe advertir sobre el bucle");
    }

    @Test
    void sameResultTriggersProgressGuard() throws Exception {
        MockLoopClient client = new MockLoopClient();

        // El modelo llama a read_file con el mismo resultado 3 veces consecutivas
        for (int i = 0; i < 10; i++) {
            client.enqueueResponse(createToolResponse("Leyendo...", "read_file", "{\"path\":\"Foo.java\"}"));
        }
        client.enqueueResponse(createTextResponse("Fin."));

        List<ChatMessage> conv = List.of(ChatMessage.user("Lee el archivo"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system-prompt", registry, null, 0.7, null, () -> false
        );

        assertNotNull(turn);
        assertTrue(client.getRecordedRequests().size() < 10,
                "El agente no debe iterar 10 veces con el mismo resultado; iteraciones: " + client.getRecordedRequests().size());
        assertEquals(TaskExecutionState.INCOMPLETE, client.getTaskState());
        assertTrue(turn.response().contains("bucle") || turn.response().contains("progreso") || turn.response().contains("mismo resultado"),
                "La respuesta debe advertir sobre la falta de progreso");
    }
}
