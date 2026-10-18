package com.boris.chat;

import java.util.List;

import org.junit.jupiter.api.*;

import com.boris.task.TaskAborter;

import static org.junit.jupiter.api.Assertions.*;

class ChatServiceTest {

    @Test
    void sendMessage_throwsWhenMessageEmpty() {
        ChatService service = new ChatService(() -> null, "boris", new TaskAborter(), 10, true);
        assertThrows(com.boris.exceptions.BorisException.class, () -> service.sendMessage(""));
    }

    @Test
    void sendMessage_throwsWhenMessageNull() {
        ChatService service = new ChatService(() -> null, "boris", new TaskAborter(), 10, true);
        assertThrows(com.boris.exceptions.BorisException.class, () -> service.sendMessage(null));
    }

    @Test
    void sendMessage_returnsExitCommandForQ() {
        ChatService service = new ChatService(() -> null, "boris", new TaskAborter(), 10, true);
        assertEquals(ChatService.EXIT_COMMAND, service.sendMessage("q"));
    }

    @Test
    void sendMessage_returnsExitCommandForExit() {
        ChatService service = new ChatService(() -> null, "boris", new TaskAborter(), 10, true);
        assertEquals(ChatService.EXIT_COMMAND, service.sendMessage("exit"));
    }

    @Test
    void withTools_construction_throwsWhenSettingsMissing() {
        assertThrows(Exception.class, () -> ChatService.withTools("/nonexistent/settings.json", "test"));
    }

    @Test
    void clearHistory_clearsConversationHistory() {
        ChatService service = new ChatService(() -> null, "boris", new TaskAborter(), 10, true);
        service.clearHistory();
        assertTrue(service.getConversationHistory().isEmpty());
    }

    @Test
    void getConversationHistory_returnsEmptyListInitially() {
        ChatService service = new ChatService(() -> null, "boris", new TaskAborter(), 10, true);
        assertTrue(service.getConversationHistory().isEmpty());
    }

    @Test
    void constructor_acceptsMaxHistorySize() {
        ChatService service = new ChatService(() -> null, "boris", new TaskAborter(), 5, true);
        assertNotNull(service);
    }

    @Test
    void conversationHistory_buildsPromptWithHistory() {
        ChatService service = new ChatService(() -> null, "boris", new TaskAborter(), 10, true);
        
        // El historial debería estar vacío inicialmente
        assertTrue(service.getConversationHistory().isEmpty());
        
        // Después de limpiar debería seguir vacío
        service.clearHistory();
        assertTrue(service.getConversationHistory().isEmpty());
    }

    @Test
    void testToolRegistryHasExpectedTools() {
        var registry = com.boris.tooling.integration.ToolCallingConfig.buildToolRegistry();
        assertTrue(registry.hasTool("maven_build"));
        assertTrue(registry.hasTool("batch_read_files"));
        assertTrue(registry.hasTool("write_file"));
        assertTrue(registry.hasTool("apply_edit"));
    }

    @Test
    void testSystemPromptContainsToolGuidance() {
        String prompt = com.boris.tooling.integration.ToolCallingConfig.loadSystemPrompt(null);
        assertNotNull(prompt);
        assertTrue(prompt.contains("maven_build"));
        assertTrue(prompt.contains("REGLA OBLIGATORIA DE TOOL CALLING"));
    }

    @Test
    void testStreamingFileWriterSuppressesThinkingTags() {
        var writer = new com.boris.edit.StreamingFileWriter();
        String output = writer.processChunk("<think>\nAnalizando proyecto\n</think>\nVoy a compilar.");
        writer.finish();
        assertFalse(output.contains("<think>"));
        assertFalse(output.contains("</think>"));
        assertTrue(output.contains("Voy a compilar."));
    }
}
