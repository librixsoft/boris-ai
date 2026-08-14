package com.boris.chat;

import org.junit.jupiter.api.*;

import com.boris.task.TaskAborter;

import static org.junit.jupiter.api.Assertions.*;

class ChatServiceTest {

    @Test
    void sendMessage_throwsWhenMessageEmpty() {
        ChatService service = new ChatService(() -> null, "boris", new TaskAborter());
        assertThrows(com.boris.exceptions.BorisException.class, () -> service.sendMessage(""));
    }

    @Test
    void sendMessage_throwsWhenMessageNull() {
        ChatService service = new ChatService(() -> null, "boris", new TaskAborter());
        assertThrows(com.boris.exceptions.BorisException.class, () -> service.sendMessage(null));
    }

    @Test
    void sendMessage_returnsExitCommandForQ() {
        ChatService service = new ChatService(() -> null, "boris", new TaskAborter());
        assertEquals(ChatService.EXIT_COMMAND, service.sendMessage("q"));
    }

    @Test
    void sendMessage_returnsExitCommandForExit() {
        ChatService service = new ChatService(() -> null, "boris", new TaskAborter());
        assertEquals(ChatService.EXIT_COMMAND, service.sendMessage("exit"));
    }

    @Test
    void withTools_construction_throwsWhenSettingsMissing() {
        assertThrows(Exception.class, () -> ChatService.withTools("/nonexistent/settings.json", "test"));
    }
}
