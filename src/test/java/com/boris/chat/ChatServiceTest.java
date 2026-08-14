package com.boris.chat;

import org.junit.jupiter.api.*;

import static org.mockito.Mockito.*;

import static org.junit.jupiter.api.Assertions.*;

class ChatServiceTest {

    @Test
    void sendMessage_returnsLlmResponse() {
        LlmProvider mockLlm = mock(LlmProvider.class);
        when(mockLlm.send("hola")).thenReturn("respuesta_llm");

        ChatService service = new ChatService(mockLlm, "boris");
        String result = service.sendMessage("hola");

        assertEquals("*boris* respuesta_llm", result);
    }

    @Test
    void sendMessage_prependsBotName() {
        LlmProvider mockLlm = mock(LlmProvider.class);
        when(mockLlm.send("que onda")).thenReturn("que pasa bro");

        ChatService service = new ChatService(mockLlm, "boris");
        String result = service.sendMessage("que onda");

        assertTrue(result.startsWith("*boris*"));
    }

    @Test
    void sendMessage_callsLlmWithUserMessage() {
        LlmProvider mockLlm = mock(LlmProvider.class);
        when(mockLlm.send(anyString())).thenReturn("ok");

        ChatService service = new ChatService(mockLlm, "boris");
        service.sendMessage("test message");

        verify(mockLlm).send("test message");
    }

    @Test
    void sendMessage_returnsNull_whenUserMessageEmpty() {
        LlmProvider mockLlm = mock(LlmProvider.class);

        ChatService service = new ChatService(mockLlm, "boris");
        String result = service.sendMessage("");

        assertNull(result);
        verify(mockLlm, never()).send(anyString());
    }

    @Test
    void sendMessage_returnsNull_whenUserMessageWhitespaceOnly() {
        LlmProvider mockLlm = mock(LlmProvider.class);

        ChatService service = new ChatService(mockLlm, "boris");
        String result = service.sendMessage("   ");

        assertNull(result);
        verify(mockLlm, never()).send(anyString());
    }

    @Test
    void sendMessage_exits_whenCommandIsQ() {
        LlmProvider mockLlm = mock(LlmProvider.class);

        ChatService service = new ChatService(mockLlm, "boris");
        String result = service.sendMessage("q");

        assertEquals(ChatService.EXIT_COMMAND, result);
        verify(mockLlm, never()).send(anyString());
    }

    @Test
    void sendMessage_exits_whenCommandIsExit() {
        LlmProvider mockLlm = mock(LlmProvider.class);

        ChatService service = new ChatService(mockLlm, "boris");
        String result = service.sendMessage("exit");

        assertEquals(ChatService.EXIT_COMMAND, result);
        verify(mockLlm, never()).send(anyString());
    }
}
