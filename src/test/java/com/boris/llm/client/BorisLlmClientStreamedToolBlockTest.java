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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pruebas de streaming para tool calls nativos.
 * Verifica que los deltas de tool call se acumulen y se ejecuten completos,
 * y que el path streaming funcione con tool calls nativos.
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
        private final Queue<List<Object>> streamedResponses = new ArrayDeque<>();
        private final List<ChatRequest> recordedRequests = new ArrayList<>();

        public MockStreamClient() {
            super("http://localhost:11434");
        }

        public void enqueueStreamedResponse(List<Object> pieces) {
            streamedResponses.add(pieces);
        }

        @Override
        public void chatStream(ChatRequest request,
                               Consumer<ChatChunk> onChunk,
                               Runnable onComplete,
                               Consumer<Throwable> onError,
                               Supplier<Boolean> isAborted) {
            recordRequest(request);
            List<Object> pieces = streamedResponses.poll();
            if (pieces == null) {
                pieces = List.of("Respuesta por defecto");
            }
            for (Object piece : pieces) {
                if (piece instanceof ChatChunk chunk) {
                    onChunk.accept(chunk);
                } else if (piece instanceof String str) {
                    ChatChunk chunk = new ChatChunk("id", List.of(new ChatChunk.ChunkChoice(
                            0, new ChatChunk.Delta("assistant", str, null, null), null)));
                    onChunk.accept(chunk);
                }
            }
            if (onComplete != null) {
                onComplete.run();
            }
        }

        private void recordRequest(ChatRequest request) {
            recordedRequests.add(request);
        }

        public List<ChatRequest> getRecordedRequests() {
            return recordedRequests;
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
    void streamedNativeToolCall_executesAndCompletesTurn() throws Exception {
        CountingBrowserTools tools = new CountingBrowserTools();
        ToolRegistry registry = ToolRegistry.from(tools);
        MockStreamClient client = new MockStreamClient();

        // Chunk con tool call delta (id, name, arguments)
        ChatChunk.ToolCallChunk tc = new ChatChunk.ToolCallChunk(
                0, "call_1", "function",
                new ToolCallFunction("browser_ui_action", "{\"target_domain\":\"google.com\"}")
        );
        ChatChunk.Delta deltaWithTool = new ChatChunk.Delta(
                "assistant", null, null, List.of(tc)
        );
        ChatChunk.ChunkChoice choiceWithTool = new ChatChunk.ChunkChoice(0, deltaWithTool, null);
        ChatChunk chunkWithTool = new ChatChunk("id", List.of(choiceWithTool));

        client.enqueueStreamedResponse(List.<Object>of(
                "Voy a abrir el navegador...",
                chunkWithTool
        ));
        client.enqueueStreamedResponse(List.of("Ventana del navegador abierta."));

        List<ChatMessage> conv = List.of(ChatMessage.user("abre una ventana de browser"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", registry, null, 0.7, chunk -> {
                }, () -> false);

        assertEquals(1, tools.browserCallCount(),
                "El tool call nativo en streaming debe ejecutarse");
        assertEquals("Ventana del navegador abierta.", turn.response());

        // El request siguiente debe contener el tool result con el call_id correcto
        assertFalse(client.getRecordedRequests().isEmpty());
        ChatRequest secondReq = client.getRecordedRequests().get(1);
        assertNotNull(secondReq.messages());
        boolean foundToolResult = secondReq.messages().stream()
                .anyMatch(m -> "tool".equals(m.role()) && m.toolCallId() != null && m.toolCallId().equals("call_1"));
        assertTrue(foundToolResult, "El request siguiente debe contener el tool result con call_id call_1");
    }

    @Test
    void streamedResponseWithoutToolCalls_endsAsNormalChat() throws Exception {
        CountingBrowserTools tools = new CountingBrowserTools();
        ToolRegistry registry = ToolRegistry.from(tools);
        MockStreamClient client = new MockStreamClient();
        client.enqueueStreamedResponse(List.of("¡Hola! ¿Cómo estás?"));

        List<ChatMessage> conv = List.of(ChatMessage.user("Hola"));
        BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                conv, "test-model", "system", registry, null, 0.7, chunk -> {
                }, () -> false);

        assertEquals("¡Hola! ¿Cómo estás?", turn.response());
        assertEquals(TaskExecutionState.IDLE, client.getTaskState());
        assertEquals(0, tools.browserCallCount(),
                "No debe ejecutarse ninguna herramienta si el modelo no emite tool calls");
    }
}
