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
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class BorisLlmClientPendingTest {

    public static class TestTools {
        @Tool(name = "read_file", description = "Lee archivo")
        public String read_file(@ToolParam(description = "path") String path) {
            return "{\"content\":\"public class Foo {}\"}";
        }

        @Tool(name = "apply_edit", description = "Aplica edicion")
        public String apply_edit(@ToolParam(description = "path") String path,
                                 @ToolParam(description = "old_text") String old_text,
                                 @ToolParam(description = "new_text") String new_text) {
            return "{\"success\":true,\"diff\":\"+ new code\"}";
        }
    }

    static class MockBorisLlmClient extends BorisLlmClient {
        private final Queue<ChatResponse> responses = new ArrayDeque<>();
        private final List<ChatRequest> recordedRequests = new ArrayList<>();

        public MockBorisLlmClient() {
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
                return createTextResponse("Respuesta por defecto");
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
        ToolCall call = new ToolCall("call_1", "function", fn);
        ChatMessage msg = ChatMessage.assistant(text, List.of(call), null);
        ChatResponse.Choice choice = new ChatResponse.Choice(0, msg, "tool_calls");
        return new ChatResponse("id", List.of(choice));
    }

    private ToolRegistry mockRegistry;

    @BeforeEach
    void setUp() {
        TaskStateNotifier.clearListeners();
        TaskStateNotifier.setState(TaskExecutionState.IDLE);
        mockRegistry = ToolRegistry.from(new TestTools());
    }

    @AfterEach
    void tearDown() {
        TaskStateNotifier.clearListeners();
        TaskStateNotifier.setState(TaskExecutionState.IDLE);
    }

    @Test
    void normalChat_exitsImmediatelyAsIdle() throws Exception {
        MockBorisLlmClient client = new MockBorisLlmClient();
        client.enqueueResponse(createTextResponse("¡Hola! ¿Cómo estás?"));

        List<ChatMessage> conv = List.of(ChatMessage.user("Hola"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", mockRegistry, null, 0.7, null, () -> false
        );

        assertNotNull(turn);
        assertEquals("¡Hola! ¿Cómo estás?", turn.response());
        assertEquals(TaskExecutionState.IDLE, client.getTaskState());
        assertEquals(1, client.getRecordedRequests().size());
    }

    @Test
    void taskWithEdit_completesSuccessfully() throws Exception {
        MockBorisLlmClient client = new MockBorisLlmClient();
        // Turno 1: read_file
        client.enqueueResponse(createToolResponse("Leyendo archivo...", "read_file", "{\"path\":\"Foo.java\"}"));
        // Turno 2: apply_edit (genera diff)
        client.enqueueResponse(createToolResponse("Modificando archivo...", "apply_edit", "{\"path\":\"Foo.java\",\"old_text\":\"a\",\"new_text\":\"b\"}"));
        // Turno 3: texto final confirmando
        client.enqueueResponse(createTextResponse("El bug fue corregido exitosamente."));

        List<ChatMessage> conv = List.of(ChatMessage.user("Corrige el bug"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", mockRegistry, null, 0.7, null, () -> false
        );

        assertNotNull(turn);
        assertEquals("El bug fue corregido exitosamente.", turn.response());
        assertEquals(TaskExecutionState.COMPLETED, client.getTaskState());
    }

    @Test
    void prematureStopAfterRead_nudged3TimesThenExitsAsIncomplete() throws Exception {
        MockBorisLlmClient client = new MockBorisLlmClient();
        // Turno 1: el modelo llama a read_file
        client.enqueueResponse(createToolResponse("Inspeccionando archivo...", "read_file", "{\"path\":\"Foo.java\"}"));
        // Turno 2 (Intento 1 prematuro): responde solo texto
        client.enqueueResponse(createTextResponse("Ya vi el archivo. El bug está en la línea 15."));
        // Turno 3 (Intento 2 prematuro): responde solo texto
        client.enqueueResponse(createTextResponse("Sigo pensando que está en la línea 15."));
        // Turno 4 (Intento 3 prematuro): responde solo texto
        client.enqueueResponse(createTextResponse("No sé qué más hacer."));
        // Turno 5 (4to intento - salida forzada): responde texto
        client.enqueueResponse(createTextResponse("Ya terminé."));

        List<ChatMessage> conv = List.of(ChatMessage.user("Arregla el bug"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", mockRegistry, null, 0.7, null, () -> false
        );

        assertNotNull(turn);
        assertTrue(turn.response().contains("⚠️ Tarea no completada"));
        assertEquals(TaskExecutionState.INCOMPLETE, client.getTaskState());
    }

    @Test
    void taskRescuedByNudge_completesWhenEditApplied() throws Exception {
        MockBorisLlmClient client = new MockBorisLlmClient();
        // Turno 1: read_file
        client.enqueueResponse(createToolResponse("Revisando...", "read_file", "{\"path\":\"Foo.java\"}"));
        // Turno 2 (Intento 1 prematuro): el modelo dice "ya encontré el bug" y para
        client.enqueueResponse(createTextResponse("Ya encontré el bug en la línea 10."));
        // Turno 3 (tras el nudge): el modelo ejecuta el apply_edit
        client.enqueueResponse(createToolResponse("Aplicando solución...", "apply_edit", "{\"path\":\"Foo.java\",\"old_text\":\"x\",\"new_text\":\"y\"}"));
        // Turno 4: el modelo confirma
        client.enqueueResponse(createTextResponse("Cambio aplicado con éxito."));

        List<ChatMessage> conv = List.of(ChatMessage.user("Arregla el bug"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", mockRegistry, null, 0.7, null, () -> false
        );

        assertNotNull(turn);
        assertEquals("Cambio aplicado con éxito.", turn.response());
        assertEquals(TaskExecutionState.COMPLETED, client.getTaskState());

        // Verificar que se le envió el mensaje de nudge
        boolean foundNudge = client.getRecordedRequests().stream().anyMatch(req ->
                req.messages().stream().anyMatch(m ->
                        m.content() != null && m.content().contains("Has analizado o investigado el problema pero no has aplicado los cambios")
                )
        );
        assertTrue(foundNudge);
    }
}
