package com.boris.llm;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.boris.settings.Settings;
import org.springframework.ai.chat.client.ChatClient;

import static org.junit.jupiter.api.Assertions.*;

class LlmClientTest {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .enable(SerializationFeature.INDENT_OUTPUT);

    @TempDir
    Path tempDir;

    @Test
    void constructor_throwsWhenSettingsMissing() {
        assertThrows(IllegalStateException.class, () -> {
            new LlmClient("/nonexistent/path/settings.json").send("hola");
        });
    }

    @Test
    void constructor_succeedsWithValidSettingsFile() throws Exception {
        Settings settings = new Settings(
            new com.boris.settings.ModelConfig("http://localhost:11434/v1", "qwen3.6-35b-64k"),
            java.util.Map.of("OLLAMA_API_KEY", "ollama")
        );

        Path settingsFile = tempDir.resolve("settings.json");
        Files.writeString(settingsFile, MAPPER.writeValueAsString(settings));

        assertDoesNotThrow(() -> new LlmClient(settingsFile.toString()));
    }

    @Test
    void constructor_throwsWhenModelFieldMissing() throws Exception {
        Settings settings = new Settings(
            null,
            java.util.Map.of("OLLAMA_API_KEY", "ollama")
        );

        Path settingsFile = tempDir.resolve("settings.json");
        Files.writeString(settingsFile, MAPPER.writeValueAsString(settings));

        assertThrows(Exception.class, () -> new LlmClient(settingsFile.toString()));
    }

    @Test
    void send_returnsMockedChatResponse() {
        ChatClient mockChatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec mockSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec mockResponse = mock(ChatClient.CallResponseSpec.class);

        when(mockChatClient.prompt("hola")).thenReturn(mockSpec);
        when(mockSpec.call()).thenReturn(mockResponse);
        when(mockResponse.content()).thenReturn("respuesta_mock");

        LlmClient client = new LlmClient(mockChatClient);
        String result = client.send("hola");

        assertEquals("respuesta_mock", result);
    }

    @Test
    void send_callsChatClientPromptWithCorrectMessage() {
        ChatClient mockChatClient = mock(ChatClient.class);
        ChatClient.ChatClientRequestSpec mockSpec = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec mockResponse = mock(ChatClient.CallResponseSpec.class);

        when(mockChatClient.prompt(anyString())).thenReturn(mockSpec);
        when(mockSpec.call()).thenReturn(mockResponse);
        when(mockResponse.content()).thenReturn("mocked_response");

        LlmClient client = new LlmClient(mockChatClient);
        String result = client.send("que hora es");

        assertEquals("mocked_response", result);
        verify(mockChatClient).prompt("que hora es");
    }
}
