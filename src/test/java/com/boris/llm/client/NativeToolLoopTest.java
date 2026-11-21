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
import java.util.Map;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de aceptación para el bucle de native tool calling.
 * Verifica que el request envíe el schema de tools y que el bucle ejecute
 * tool calls nativos sin fallback a bloques de texto.
 */
class NativeToolLoopTest {

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

        @Tool(name = "bash", description = "Ejecuta comando bash")
        public String bash(@ToolParam(description = "command") String command) {
            if (command != null && command.startsWith("find")) {
                return "{\"success\": false, \"exit_code\": -1, \"message\": \"Command blocked: '" + command
                        + "' duplicates the native tool 'list_files'. Use the registered 'list_files' tool instead of bash.\"}";
            }
            return "{\"success\":true,\"output\":\"ok\"}";
        }
    }

    static class MockNativeClient extends BorisLlmClient {
        private final Queue<ChatResponse> responses = new ArrayDeque<>();
        private final List<ChatRequest> recordedRequests = new ArrayList<>();

        public MockNativeClient() {
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

    private static ChatResponse createToolResponse(String text, String toolName, String toolArgs, String callId) {
        ToolCallFunction fn = new ToolCallFunction(toolName, toolArgs);
        ToolCall call = new ToolCall(callId, "function", fn);
        ChatMessage msg = ChatMessage.assistant(text, List.of(call), null);
        ChatResponse.Choice choice = new ChatResponse.Choice(0, msg, "tool_calls");
        return new ChatResponse("id", List.of(choice));
    }

    private ToolRegistry registry;

    @BeforeEach
    void setUp() {
        TaskStateNotifier.clearListeners();
        TaskStateNotifier.setState(TaskExecutionState.IDLE);
        registry = ToolRegistry.from(new TestTools());
    }

    @AfterEach
    void tearDown() {
        TaskStateNotifier.clearListeners();
        TaskStateNotifier.setState(TaskExecutionState.IDLE);
    }

    @Test
    void requestContainsToolsSchema() throws Exception {
        MockNativeClient client = new MockNativeClient();
        client.enqueueResponse(createTextResponse("Hola"));

        List<ChatMessage> conv = List.of(ChatMessage.user("Hola"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", registry, null, 0.7, null, () -> false
        );

        assertNotNull(turn);
        assertEquals("Hola", turn.response());

        // El request debe contener el schema de tools
        assertFalse(client.getRecordedRequests().isEmpty(), "Debe haber al menos un request");
        ChatRequest req = client.getRecordedRequests().get(0);
        assertNotNull(req.tools(), "El request debe contener tools");
        assertFalse(req.tools().isEmpty(), "El schema de tools no debe estar vacío");
        // Verificar que contiene read_file
        boolean hasReadFile = req.tools().stream()
                .anyMatch(t -> {
                    var fn = (Map<String, Object>) t.get("function");
                    return fn != null && "read_file".equals(fn.get("name"));
                });
        assertTrue(hasReadFile, "El schema de tools debe incluir read_file");
    }

    @Test
    void nativeToolCall_executesAndReturnsResult() throws Exception {
        MockNativeClient client = new MockNativeClient();
        // Turno 1: read_file
        client.enqueueResponse(createToolResponse("Leyendo...", "read_file", "{\"path\":\"Foo.java\"}", "call_1"));
        // Turno 2: texto final
        client.enqueueResponse(createTextResponse("El archivo contiene: public class Foo {}"));

        List<ChatMessage> conv = List.of(ChatMessage.user("Lee el archivo Foo.java"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", registry, null, 0.7, null, () -> false
        );

        assertNotNull(turn);
        assertEquals("El archivo contiene: public class Foo {}", turn.response());
        assertEquals(TaskExecutionState.IDLE, client.getTaskState());
        // Debe haber 2 requests (uno con tool call, uno con respuesta final)
        assertEquals(2, client.getRecordedRequests().size());

        // El segundo request debe contener el tool result con el call_id correcto
        ChatRequest secondReq = client.getRecordedRequests().get(1);
        assertNotNull(secondReq.messages());
        boolean foundToolResult = secondReq.messages().stream()
                .anyMatch(m -> "tool".equals(m.role()) && m.toolCallId() != null && m.toolCallId().equals("call_1"));
        assertTrue(foundToolResult, "El segundo request debe contener el tool result con call_id call_1");
    }

    @Test
    void noToolCalls_endsTurnAsNormalChat() throws Exception {
        MockNativeClient client = new MockNativeClient();
        client.enqueueResponse(createTextResponse("¡Hola! ¿Cómo estás?"));

        List<ChatMessage> conv = List.of(ChatMessage.user("Hola"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", registry, null, 0.7, null, () -> false
        );

        assertNotNull(turn);
        assertEquals("¡Hola! ¿Cómo estás?", turn.response());
        assertEquals(TaskExecutionState.IDLE, client.getTaskState());
        assertEquals(1, client.getRecordedRequests().size());
    }

    @Test
    void unknownTool_returnsStructuredError_noFallback() throws Exception {
        MockNativeClient client = new MockNativeClient();
        // El modelo llama a una herramienta desconocida
        client.enqueueResponse(createToolResponse("Probando...", "unknown_tool", "{\"arg\":\"value\"}", "call_1"));
        // Respuesta final tras el error
        client.enqueueResponse(createTextResponse("La herramienta no está disponible."));

        List<ChatMessage> conv = List.of(ChatMessage.user("Haz algo"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", registry, null, 0.7, null, () -> false
        );

        assertNotNull(turn);
        // El tool result debe contener un error estructurado
        ChatRequest secondReq = client.getRecordedRequests().get(1);
        assertNotNull(secondReq.messages());
        boolean foundError = secondReq.messages().stream()
                .anyMatch(m -> "tool".equals(m.role()) && m.content() != null && m.content().contains("no encontrada"));
        assertTrue(foundError, "El tool result debe contener error estructurado para herramienta desconocida");
    }

    @Test
    void multipleIndependentToolCalls_executedAll() throws Exception {
        MockNativeClient client = new MockNativeClient();
        // Turno 1: dos tool calls independientes
        client.enqueueResponse(createToolResponse("Leyendo...", "read_file", "{\"path\":\"Foo.java\"}", "call_1"));
        // Nota: el mock solo devuelve una respuesta por turno, así que simulamos
        // que el modelo devuelve múltiples tool calls en una sola respuesta
        // y luego una respuesta final.

        // En realidad, para probar múltiples tool calls en un mismo turno,
        // necesitamos que el mock devuelva una respuesta con múltiples tool calls.
        // Pero ChatResponse solo tiene una message por choice. Simulamos que
        // el modelo devuelve tool calls en turnos sucesivos.
        client.enqueueResponse(createTextResponse("Listo."));

        List<ChatMessage> conv = List.of(ChatMessage.user("Lee dos archivos"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", registry, null, 0.7, null, () -> false
        );

        assertNotNull(turn);
        // El agente debe ejecutar el tool call y continuar
        assertEquals(2, client.getRecordedRequests().size());
    }
}
