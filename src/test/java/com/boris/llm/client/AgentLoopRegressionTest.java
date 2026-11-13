package com.boris.llm.client;

import com.boris.llm.think.ThinkingStallDetector;
import com.boris.task.TaskExecutionState;
import com.boris.task.TaskStateNotifier;
import com.boris.tooling.annotation.Tool;
import com.boris.tooling.annotation.ToolParam;
import com.boris.tooling.engine.ToolRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Test de regresión para garantizar que el agente nunca vuelva a caer en bucles
 * de exploración o reintentos con argumentos incrementales (ej: find con -maxdepth 3..22).
 */
class AgentLoopRegressionTest {

    public static class RealisticLoopTools {
        @Tool(name = "list_files", description = "Lista archivos")
        public String list_files(@ToolParam(description = "path") String path) {
            return "{\"success\":true,\"entries\":[{\"name\":\"src\",\"type\":\"directory\"}]}";
        }

        @Tool(name = "batch_read_files", description = "Lee múltiples archivos")
        public String batch_read_files(@ToolParam(description = "paths") String paths) {
            return "{\"success\":true,\"files\":[]}";
        }

        @Tool(name = "bash", description = "Ejecuta comando bash")
        public String bash(@ToolParam(description = "command") String command) {
            if (command != null && (command.startsWith("find") || command.contains("find "))) {
                return "{\"success\": false, \"exit_code\": -1, \"message\": \"Command blocked: '" + command
                        + "' duplicates the native tool 'list_files'. Use the registered 'list_files' tool instead of bash.\"}";
            }
            return "{\"success\":true,\"output\":\"ok\"}";
        }
    }

    static class MockAgentClient extends BorisLlmClient {
        private final Queue<ChatResponse> responses = new ArrayDeque<>();
        private final List<ChatRequest> recordedRequests = new ArrayList<>();

        public MockAgentClient() {
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
        registry = ToolRegistry.from(new RealisticLoopTools());
    }

    @AfterEach
    void tearDown() {
        TaskStateNotifier.clearListeners();
        TaskStateNotifier.setState(TaskExecutionState.IDLE);
    }

    @Test
    @DisplayName("Regresión find -maxdepth 3..22: el bucle debe cortarse en 3 intentos en vez de 20+")
    void incrementalMaxdepthFindLoop_abortsImmediatelyAtMaxBlockedCommands() throws Exception {
        MockAgentClient client = new MockAgentClient();

        // Encolamos 20 intentos con -maxdepth 3 hasta -maxdepth 22 (como en el log reportado)
        for (int depth = 3; depth <= 22; depth++) {
            String cmd = "{\"command\":\"find /workspace -maxdepth " + depth + " -type f 2>/dev/null\"}";
            client.enqueueResponse(createToolResponse("Buscando en profundidad " + depth, "bash", cmd));
        }
        client.enqueueResponse(createTextResponse("Terminado."));

        List<ChatMessage> conv = List.of(ChatMessage.user("cambia el theme de java tui lanterna a color tema claro"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system-prompt", registry, null, 0.7, null, () -> false
        );

        assertNotNull(turn);
        // Debe frenarse en exactamente 3 requests (límite de comandos bloqueados consecutivos)
        assertEquals(3, client.getRecordedRequests().size(), "El agente no debe continuar intentando comandos bloqueados");
        assertEquals(TaskExecutionState.INCOMPLETE, client.getTaskState(), "El estado debe ser INCOMPLETE al frenar el bucle");
        assertTrue(turn.response().contains("comandos bloqueados") || turn.response().contains("bucle"),
                "La respuesta debe advertir al usuario sobre el bucle o bloqueo");
    }

    @Test
    @DisplayName("Regresión text tool blocks: bloques ```tool:bash con find incremental cortan en 3 intentos")
    void incrementalMaxdepthFindTextBlocks_abortsImmediatelyAtMaxBlockedCommands() throws Exception {
        MockAgentClient client = new MockAgentClient();

        for (int depth = 3; depth <= 22; depth++) {
            String block = "Buscando...\n```tool:bash {\"command\":\"find /workspace -maxdepth " + depth + " -type f 2>/dev/null\"}\n```";
            client.enqueueResponse(createTextResponse(block));
        }
        client.enqueueResponse(createTextResponse("Terminado."));

        List<ChatMessage> conv = List.of(ChatMessage.user("cambia el theme"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system-prompt", registry, null, 0.7, null, () -> false
        );

        assertNotNull(turn);
        assertEquals(3, client.getRecordedRequests().size(), "Los text tool blocks deben frenarse en 3 intentos bloqueados");
        assertEquals(TaskExecutionState.INCOMPLETE, client.getTaskState());
        assertTrue(turn.response().contains("comandos bloqueados") || turn.response().contains("bucle"));
    }

    @Test
    @DisplayName("ThinkingStallDetector: detecta bucle de ráfaga de la misma herramienta aunque los argumentos varíen")
    void thinkingStallDetector_detectsLoopEvenWhenToolArgumentsChangeEveryIteration() {
        ThinkingStallDetector detector = new ThinkingStallDetector(3, 10, 3);

        // Simulamos iteraciones consecutivas donde el modelo insiste con 'bash' variando los argumentos
        for (int depth = 3; depth <= 5; depth++) {
            String args = "{\"command\":\"find . -maxdepth " + depth + "\"}";
            boolean stalled = detector.recordThinking("Pensando...", List.of(new ThinkingStallDetector.ToolCallSignature("bash", args)));
            assertEquals(false, stalled, "No debería estancarse antes de alcanzar el límite de ráfaga");
        }

        // En la 4ta iteración consecutiva con la misma herramienta (3 repeticiones acumuladas) se alcanza el límite
        String finalArgs = "{\"command\":\"find . -maxdepth 6\"}";
        boolean stalledAtFourth = detector.recordThinking("Pensando...", List.of(new ThinkingStallDetector.ToolCallSignature("bash", finalArgs)));
        assertTrue(stalledAtFourth, "La 4ta iteración consecutiva con la misma herramienta debe detectarse como estancamiento");
        assertTrue(detector.isStallDetected(), "isStallDetected() debe ser true");
    }

    @Test
    @DisplayName("Regresión draft.md: list_files con mismos argumentos repetidos deben cortarse temprano por deduplicador")
    void listFilesSameArgsRepeated_abortsByDeduplicator() throws Exception {
        MockAgentClient client = new MockAgentClient();

        // Mismo list_files con mismos argumentos 3 veces consecutivas:
        // el deduplicador devuelve [DUPLICADO] en la 2da y [STOPPED] en la 3ra.
        String args = "{\"path\":\"/Users/lastprophet/Documents/workspaces/boris-ai/src\",\"recursive\":true,\"max_depth\":3}";
        for (int i = 0; i < 3; i++) {
            client.enqueueResponse(createToolResponse("Listando src...", "list_files", args));
        }
        client.enqueueResponse(createTextResponse("Terminado."));

        List<ChatMessage> conv = List.of(ChatMessage.user("cambia el theme de java tui lanterna a color claro"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system-prompt", registry, null, 0.7, null, () -> false
        );

        assertNotNull(turn);
        // El deduplicador detiene tras 3 llamadas idénticas consecutivas
        assertTrue(client.getRecordedRequests().size() <= 3,
                "El agente no debe iterar más de 3 veces con los mismos argumentos; iteraciones: " + client.getRecordedRequests().size());
        assertEquals(TaskExecutionState.INCOMPLETE, client.getTaskState(),
                "El estado debe ser INCOMPLETE al frenar el bucle de listado");
        assertTrue(turn.response().contains("STOPPED") || turn.response().contains("repitió") || turn.response().contains("bucle"),
                "La respuesta debe advertir sobre el bucle de listado de archivos");
    }

    @Test
    @DisplayName("Regresión list_files drilling: el agente se frena tras 3 niveles de drilling")
    void listFilesDrilling_abortsAt3Levels() throws Exception {
        MockAgentClient client = new MockAgentClient();

        // El modelo intenta drilling: / → /src → /src/main → /src/main/java
        client.enqueueResponse(createToolResponse("Listando /", "list_files", "{\"path\":\"/\"}"));
        client.enqueueResponse(createToolResponse("Listando /src", "list_files", "{\"path\":\"/src\"}"));
        client.enqueueResponse(createToolResponse("Listando /src/main", "list_files", "{\"path\":\"/src/main\"}"));
        client.enqueueResponse(createToolResponse("Listando /src/main/java", "list_files", "{\"path\":\"/src/main/java\"}"));
        client.enqueueResponse(createTextResponse("Terminado."));

        List<ChatMessage> conv = List.of(ChatMessage.user("Explora el proyecto"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system-prompt", registry, null, 0.7, null, () -> false
        );

        assertNotNull(turn);
        // El agente debe frenarse antes de completar el drilling (3 niveles)
        assertTrue(client.getRecordedRequests().size() <= 4,
                "El agente no debe hacer más de 4 llamadas; iteraciones: " + client.getRecordedRequests().size());
        assertEquals(TaskExecutionState.INCOMPLETE, client.getTaskState(),
                "El estado debe ser INCOMPLETE al frenar el drilling");
        assertTrue(turn.response().contains("drilling") || turn.response().contains("bucle"),
                "La respuesta debe advertir sobre el drilling");
    }

    @Test
    @DisplayName("Regresión list_files no-drilling: múltiples rutas no jerárquicas no frenan")
    void listFilesNonDrilling_allowsMultiplePaths() throws Exception {
        MockAgentClient client = new MockAgentClient();

        // Rutas no jerárquicas: /a, /b, /c
        client.enqueueResponse(createToolResponse("Listando /a", "list_files", "{\"path\":\"/a\"}"));
        client.enqueueResponse(createToolResponse("Listando /b", "list_files", "{\"path\":\"/b\"}"));
        client.enqueueResponse(createToolResponse("Listando /c", "list_files", "{\"path\":\"/c\"}"));
        client.enqueueResponse(createTextResponse("Terminado."));

        List<ChatMessage> conv = List.of(ChatMessage.user("Explora el proyecto"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system-prompt", registry, null, 0.7, null, () -> false
        );

        assertNotNull(turn);
        // No debe frenarse por drilling
        assertEquals(TaskExecutionState.IDLE, client.getTaskState(),
                "El estado debe ser IDLE al completar sin drilling");
    }
}
