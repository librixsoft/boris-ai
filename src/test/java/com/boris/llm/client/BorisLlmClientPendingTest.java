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

        @Tool(name = "bash", description = "Ejecuta comando bash")
        public String bash(@ToolParam(description = "command") String command) {
            if (command != null && command.startsWith("find")) {
                return "{\"success\": false, \"exit_code\": -1, \"message\": \"Command blocked: '" + command
                        + "' duplicates the native tool 'list_files'. Use the registered 'list_files' tool instead of bash.\"}";
            }
            return "{\"success\":true,\"output\":\"ok\"}";
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
    void prematureStopAfterRead_exitsImmediatelyWithoutNudges() throws Exception {
        MockBorisLlmClient client = new MockBorisLlmClient();
        // Turno 1: el modelo llama a read_file
        client.enqueueResponse(createToolResponse("Inspeccionando archivo...", "read_file", "{\"path\":\"Foo.java\"}"));
        // Turno 2: el modelo responde solo texto y decide finalizar
        client.enqueueResponse(createTextResponse("Ya vi el archivo. El bug está en la línea 15."));

        List<ChatMessage> conv = List.of(ChatMessage.user("Arregla el bug"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", mockRegistry, null, 0.7, null, () -> false
        );

        assertNotNull(turn);
        assertEquals("Ya vi el archivo. El bug está en la línea 15.", turn.response());
        assertEquals(TaskExecutionState.IDLE, client.getTaskState());
        assertEquals(2, client.getRecordedRequests().size());
    }

    @Test
    void prematureStopAfterRead_doesNotInjectNudgeMessages() throws Exception {
        MockBorisLlmClient client = new MockBorisLlmClient();
        // Turno 1: read_file
        client.enqueueResponse(createToolResponse("Revisando...", "read_file", "{\"path\":\"Foo.java\"}"));
        // Turno 2: el modelo dice "ya encontré el bug" y para
        client.enqueueResponse(createTextResponse("Ya encontré el bug en la línea 10."));

        List<ChatMessage> conv = List.of(ChatMessage.user("Arregla el bug"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", mockRegistry, null, 0.7, null, () -> false
        );

        assertNotNull(turn);
        assertEquals("Ya encontré el bug en la línea 10.", turn.response());

        boolean foundNudge = client.getRecordedRequests().stream().anyMatch(req ->
                req.messages().stream().anyMatch(m ->
                        m.content() != null && m.content().contains("Has analizado o investigado el problema pero no has aplicado los cambios")
                )
        );
        assertFalse(foundNudge);
        assertEquals(2, client.getRecordedRequests().size());
    }

    @Test
    void textToolBlockPayload_isNotCorruptedWithGarbageText() throws Exception {
        MockBorisLlmClient client = new MockBorisLlmClient();
        // Turno 1: el modelo emite un tool block en texto (protocolo ```tool:)
        client.enqueueResponse(createTextResponse("Explorando...\n```tool:list_files {\"path\":\"/x\"}\n```"));
        // Turno 2: texto final
        client.enqueueResponse(createTextResponse("Listo."));

        List<ChatMessage> conv = List.of(ChatMessage.user("Explora el proyecto"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", mockRegistry, null, 0.7, null, () -> false
        );

        assertEquals("Listo.", turn.response());

        boolean payloadSent = client.getRecordedRequests().stream().anyMatch(req ->
                req.messages().stream().anyMatch(m ->
                        m.content() != null && m.content().contains("[TOOL_RESULTS]"))
        );
        assertTrue(payloadSent);

        boolean corruptedPayloadFound = client.getRecordedRequests().stream().anyMatch(req ->
                req.messages().stream().anyMatch(m ->
                        m.content() != null && m.content().contains("emporada"))
        );
        assertFalse(corruptedPayloadFound);
    }

    @Test
    void repeatedIdenticalToolCallsAcrossTurn_stopsWithIncompleteState() throws Exception {
        MockBorisLlmClient client = new MockBorisLlmClient();
        // Mismo tool con mismos argumentos 3 veces consecutivas: el deduplicador
        // devuelve [DUPLICADO] en la 2da y [STOPPED] en la 3ra.
        client.enqueueResponse(createToolResponse("Leyendo...", "read_file", "{\"path\":\"Foo.java\"}"));
        client.enqueueResponse(createToolResponse("Leyendo...", "read_file", "{\"path\":\"Foo.java\"}"));
        client.enqueueResponse(createToolResponse("Leyendo...", "read_file", "{\"path\":\"Foo.java\"}"));
        // Respuesta final que nunca debería consumirse si el deduplicador corta antes
        client.enqueueResponse(createTextResponse("Fin."));

        List<ChatMessage> conv = List.of(ChatMessage.user("Explora el proyecto"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", mockRegistry, null, 0.7, null, () -> false
        );

        // El deduplicador detiene el turno tras 3 llamadas idénticas consecutivas
        assertEquals(TaskExecutionState.INCOMPLETE, client.getTaskState());
        assertTrue(turn.response().contains("STOPPED") || turn.response().contains("repitió"));

        // 2 requests: la 3ra es detectada como STOPPED y no se envía al modelo
        assertEquals(2, client.getRecordedRequests().size());
    }

    @Test
    void textToolBlockLoopWithEmptyReasoning_stopsWithIncompleteState() throws Exception {
        MockBorisLlmClient client = new MockBorisLlmClient();
        // Mismo tool block de texto 3 veces consecutivas: el deduplicador
        // devuelve [DUPLICADO] en la 2da y [STOPPED] en la 3ra.
        client.enqueueResponse(createTextResponse("Explorando...\n```tool:list_files {\"path\":\"/x\"}\n```"));
        client.enqueueResponse(createTextResponse("Explorando...\n```tool:list_files {\"path\":\"/x\"}\n```"));
        client.enqueueResponse(createTextResponse("Explorando...\n```tool:list_files {\"path\":\"/x\"}\n```"));
        // Respuesta final que nunca debería consumirse si el deduplicador corta antes
        client.enqueueResponse(createTextResponse("Fin."));

        List<ChatMessage> conv = List.of(ChatMessage.user("Crea un plan"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", mockRegistry, null, 0.7, null, () -> false
        );

        // El deduplicador detiene el turno tras 3 llamadas idénticas consecutivas
        assertEquals(TaskExecutionState.INCOMPLETE, client.getTaskState());
        assertTrue(turn.response().contains("STOPPED") || turn.response().contains("repitió"));

        // 2 requests: la 3ra es detectada como STOPPED y no se envía al modelo
        assertEquals(2, client.getRecordedRequests().size());
    }

    @Test
    void consecutiveBlockedCommands_stopsWithIncompleteState() throws Exception {
        MockBorisLlmClient client = new MockBorisLlmClient();
        client.enqueueResponse(createToolResponse("Buscando...", "bash", "{\"command\":\"find . -maxdepth 3\"}"));
        client.enqueueResponse(createToolResponse("Buscando...", "bash", "{\"command\":\"find . -maxdepth 4\"}"));
        client.enqueueResponse(createToolResponse("Buscando...", "bash", "{\"command\":\"find . -maxdepth 5\"}"));
        client.enqueueResponse(createTextResponse("Fin."));

        List<ChatMessage> conv = List.of(ChatMessage.user("Busca archivos"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", mockRegistry, null, 0.7, null, () -> false
        );

        assertEquals(TaskExecutionState.INCOMPLETE, client.getTaskState());
        assertTrue(turn.response().contains("bucle") || turn.response().contains("bloqueado"));
        assertEquals(3, client.getRecordedRequests().size());
    }

    @Test
    void textToolBlockBlockedCommands_stopsWithIncompleteState() throws Exception {
        MockBorisLlmClient client = new MockBorisLlmClient();
        client.enqueueResponse(createTextResponse("Buscando...\n```tool:bash {\"command\":\"find . -maxdepth 3\"}\n```"));
        client.enqueueResponse(createTextResponse("Buscando...\n```tool:bash {\"command\":\"find . -maxdepth 4\"}\n```"));
        client.enqueueResponse(createTextResponse("Buscando...\n```tool:bash {\"command\":\"find . -maxdepth 5\"}\n```"));
        client.enqueueResponse(createTextResponse("Fin."));

        List<ChatMessage> conv = List.of(ChatMessage.user("Busca archivos"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", mockRegistry, null, 0.7, null, () -> false
        );

        assertEquals(TaskExecutionState.INCOMPLETE, client.getTaskState());
        assertTrue(turn.response().contains("bucle") || turn.response().contains("bloqueado"));
        assertEquals(3, client.getRecordedRequests().size());
    }
}
