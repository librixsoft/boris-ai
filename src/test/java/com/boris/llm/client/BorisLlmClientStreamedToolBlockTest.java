package com.boris.llm.client;

import com.boris.task.TaskExecutionState;
import com.boris.task.TaskStateNotifier;
import com.boris.tooling.annotation.Tool;
import com.boris.tooling.annotation.ToolParam;
import com.boris.tooling.engine.ToolRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * El modelo puede responder solo con un bloque tool en texto (sin prosa, sin
 * reasoning, sin tool calls nativas). El bucle con streaming debe ejecutarlo
 * en lugar de clasificar el turno como vacio y descartarlo.
 */
class BorisLlmClientStreamedToolBlockTest {

    public static class CountingBrowserTools {
        private final AtomicInteger browserCalls = new AtomicInteger(0);

        @Tool(name = "browser_ui_action", description = "Accion visual de browser")
        public String browser_ui_action(@ToolParam(description = "target_domain", required = false) String target_domain) {
            int seq = browserCalls.incrementAndGet();
            return "{\"status\":\"success\",\"action\":\"open\",\"url_before\":\"about:blank\",\"url_after\":\"https://www.google.com/\",\"changed\":true,\"exec\":\"" + seq + "\"}";
        }

        public int browserCallCount() {
            return browserCalls.get();
        }
    }

    static class MockStreamClient extends BorisLlmClient {
        private final Queue<List<String>> streamedResponses = new ArrayDeque<>();
        private final List<String> recordedUserContents = new ArrayList<>();

        MockStreamClient() {
            super("http://localhost:11434");
        }

        public void enqueueStreamedResponse(List<String> contentPieces) {
            streamedResponses.add(contentPieces);
        }

        @Override
        public void chatStream(ChatRequest request,
                               Consumer<ChatChunk> onChunk,
                               Runnable onComplete,
                               Consumer<Throwable> onError,
                               Supplier<Boolean> isAborted) {
            recordRequest(request);
            List<String> pieces = streamedResponses.poll();
            if (pieces == null) {
                pieces = List.of("Respuesta por defecto");
            }
            for (String piece : pieces) {
                ChatChunk chunk = new ChatChunk("id", List.of(new ChatChunk.ChunkChoice(
                        0, new ChatChunk.Delta("assistant", piece, null, null), null)));
                onChunk.accept(chunk);
            }
            if (onComplete != null) {
                onComplete.run();
            }
        }

        private void recordRequest(ChatRequest request) {
            List<ChatMessage> messages = request.messages();
            if (messages != null && !messages.isEmpty()) {
                ChatMessage last = messages.getLast();
                if (last != null && last.content() != null) {
                    recordedUserContents.add(last.content());
                }
            }
        }

        public List<String> getRecordedUserContents() {
            return recordedUserContents;
        }
    }

    @BeforeEach
    void setUp() {
        TaskStateNotifier.clearListeners();
        TaskStateNotifier.setState(TaskExecutionState.IDLE);
    }

    @AfterEach
    void tearDown() {
        TaskStateNotifier.clearListeners();
        TaskStateNotifier.setState(TaskExecutionState.IDLE);
    }

    @Test
    void streamedResponseWithOnlyToolBlock_executesToolAndCompletesTurn() throws Exception {
        CountingBrowserTools tools = new CountingBrowserTools();
        ToolRegistry registry = ToolRegistry.from(tools);
        MockStreamClient client = new MockStreamClient();
        client.enqueueStreamedResponse(List.of(
                "```tool:browser_ui_action\n",
                "{\"target_domain\": \"google.com\"}\n",
                "```\n"));
        client.enqueueStreamedResponse(List.of("Ventana del navegador abierta."));

        List<ChatMessage> conv = List.of(ChatMessage.user("abre una ventana de browser"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", registry, null, 0.7, chunk -> {
                }, () -> false);

        assertEquals(1, tools.browserCallCount(),
                "El bloque tool de texto debe ejecutarse aunque el turno no tenga prosa");
        assertEquals("Ventana del navegador abierta.", turn.response());

        boolean toolResultsSent = client.getRecordedUserContents().stream()
                .anyMatch(c -> c.contains("[TOOL_RESULTS]"));
        assertTrue(toolResultsSent, "El resultado de la tool debe enviarse al modelo en la siguiente iteración");
    }

    @Test
    void streamedResponseWithProseAndToolBlock_executesToolAndCompletesTurn() throws Exception {
        CountingBrowserTools tools = new CountingBrowserTools();
        ToolRegistry registry = ToolRegistry.from(tools);
        MockStreamClient client = new MockStreamClient();
        client.enqueueStreamedResponse(List.of(
                "Abriendo el navegador...\n",
                "```tool:browser_ui_action\n",
                "{\"target_domain\": \"google.com\"}\n",
                "```\n"));
        client.enqueueStreamedResponse(List.of("Listo."));

        List<ChatMessage> conv = List.of(ChatMessage.user("abre una ventana de browser"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", registry, null, 0.7, chunk -> {
                }, () -> false);

        assertEquals(1, tools.browserCallCount(),
                "El bloque tool de texto debe ejecutarse en el path streaming");
        assertEquals("Listo.", turn.response());
    }
}
