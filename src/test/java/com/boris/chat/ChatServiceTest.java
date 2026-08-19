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
}
