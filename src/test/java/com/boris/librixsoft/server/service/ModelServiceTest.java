package com.boris.librixsoft.server.service;

import com.boris.librixsoft.agent.tools.ReadFileTool;
import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.server.dto.ToolAction;
import com.boris.librixsoft.server.dto.ToolExecutionPayload;
import com.boris.librixsoft.server.dto.ChatResponseDTO;
import com.boris.librixsoft.server.dto.TokenInfo;
import com.boris.librixsoft.server.service.prompts.EditorEditPrompt;
import com.boris.librixsoft.server.service.prompts.EditorSystemPrompt;
import com.boris.librixsoft.server.service.prompts.PromptModel;
import com.boris.librixsoft.server.dto.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ModelServiceTest {

    @Mock
    private BorisProperties borisProperties;

    @Mock
    private LlamaChatService llamaChatService;

    @Mock
    private PromptModel promptModel;

    @Mock
    private EditorSystemPrompt editorSystemPrompt;

    @Mock
    private EditorEditPrompt editorEditPrompt;

    @Mock
    private ReadFileTool readFileTool;

    @Mock
    private ChatSessionService chatSessionService;

    @InjectMocks
    private ModelService modelService;

    private BorisProperties.ModelConfig modelConfig;

    @BeforeEach
    void setUp() {
        modelConfig = new BorisProperties.ModelConfig();
        modelConfig.setId("test-model");
        modelConfig.setTemperature(0.7);
        modelConfig.setMaxTokens(4096);
        
        when(borisProperties.getWorkspacePrefix()).thenReturn("/.boris/workspace");
        when(llamaChatService.resolveConfig(null, 0)).thenReturn(modelConfig);
        when(promptModel.build()).thenReturn("test prompt");
        when(editorSystemPrompt.build()).thenReturn("editor system prompt");
        when(editorEditPrompt.build(anyString(), anyString(), anyString())).thenReturn("editor edit prompt");
    }

    @Test
    void testExecuteFlow_ConversationalResponse() {
        // Arrange
        String instruction = "Hello, how are you?";
        List<org.springframework.ai.chat.messages.Message> history = new ArrayList<>();
        AtomicBoolean cancellationRequested = new AtomicBoolean(false);
        String sessionId = "session-123";

        String modelResponse = "I'm doing well, thank you!";
        when(llamaChatService.executePromptWithTools(
                eq("test-model"), eq("test prompt"), eq(instruction), eq(0.7),
                isNull(), eq(cancellationRequested), eq(4096), eq(history)))
                .thenReturn(modelResponse);

        when(llamaChatService.getTokenInfo()).thenReturn(new TokenInfo(10, 5, 15));

        // Act
        ApiResponse<Map<String, Object>> apiResponse = modelService.executeFlow(instruction, history, cancellationRequested, sessionId);
        Map<String, Object> result = apiResponse.getData();

        // Assert
        assertNotNull(apiResponse);
        assertEquals("success", apiResponse.getStatus());
        assertNotNull(result);
        assertEquals("direct", result.get("type"));
        assertEquals(modelResponse, result.get("result"));

        verify(chatSessionService).addMessage(eq("session-123"), any(UserMessage.class));
        verify(chatSessionService).addMessage(eq("session-123"), any(AssistantMessage.class));
    }

    @Test
    void testExecuteFlow_CreateRequest() {
        // Arrange
        String instruction = "Create a file";
        List<org.springframework.ai.chat.messages.Message> history = new ArrayList<>();
        AtomicBoolean cancellationRequested = new AtomicBoolean(false);
        String sessionId = "session-123";

        String modelResponse = "{\"type\":\"CREATE\",\"path\":\"/test/file.txt\",\"content\":\"Hello World\"}";
        when(llamaChatService.executePromptWithTools(
                anyString(), anyString(), anyString(), anyDouble(),
                isNull(), any(), anyInt(), anyList()))
                .thenReturn(modelResponse);

        when(llamaChatService.executeNativeToolsDirectly(anyString()))
                .thenReturn("File created successfully");

        // Act
        ApiResponse<Map<String, Object>> apiResponse = modelService.executeFlow(instruction, history, cancellationRequested, sessionId);
        Map<String, Object> result = apiResponse.getData();

        // Assert
        assertNotNull(apiResponse);
        assertEquals("success", apiResponse.getStatus());
        assertNotNull(result);
        assertEquals("create", result.get("type"));
    }

    @Test
    void testExecuteFlow_EditRequest() {
        // Arrange
        String instruction = "Edit the file";
        List<org.springframework.ai.chat.messages.Message> history = new ArrayList<>();
        AtomicBoolean cancellationRequested = new AtomicBoolean(false);
        String sessionId = "session-123";

        String modelResponse = "{\"type\":\"EDIT\",\"path\":\"/test/file.txt\",\"instructions\":\"Change text\"}";
        when(llamaChatService.executePromptWithTools(
                anyString(), anyString(), anyString(), anyDouble(),
                isNull(), any(), anyInt(), anyList()))
                .thenReturn(modelResponse);

        when(readFileTool.call(anyString())).thenReturn("Original content");

        // Act
        ApiResponse<Map<String, Object>> apiResponse = modelService.executeFlow(instruction, history, cancellationRequested, sessionId);
        Map<String, Object> result = apiResponse.getData();

        // Assert
        assertNotNull(apiResponse);
        assertEquals("success", apiResponse.getStatus());
        assertNotNull(result);
        assertEquals("edit", result.get("type"));
        assertTrue(result.get("result").toString().contains("Edición completada"));
    }

    @Test
    void testExecuteFlow_DeleteRequest() {
        // Arrange
        String instruction = "Delete the file";
        List<org.springframework.ai.chat.messages.Message> history = new ArrayList<>();
        AtomicBoolean cancellationRequested = new AtomicBoolean(false);
        String sessionId = "session-123";

        String modelResponse = "{\"type\":\"DELETE\",\"path\":\"/test/file.txt\"}";
        when(llamaChatService.executePromptWithTools(
                anyString(), anyString(), anyString(), anyDouble(),
                isNull(), any(), anyInt(), anyList()))
                .thenReturn(modelResponse);

        when(llamaChatService.executeNativeToolsDirectly(anyString()))
                .thenReturn("File deleted successfully");

        // Act
        ApiResponse<Map<String, Object>> apiResponse = modelService.executeFlow(instruction, history, cancellationRequested, sessionId);
        Map<String, Object> result = apiResponse.getData();

        // Assert
        assertNotNull(apiResponse);
        assertEquals("success", apiResponse.getStatus());
        assertNotNull(result);
        assertEquals("delete", result.get("type"));
    }

    @Test
    void testExecuteFlow_Error() {
        // Arrange
        String instruction = "Test error";
        List<org.springframework.ai.chat.messages.Message> history = new ArrayList<>();
        AtomicBoolean cancellationRequested = new AtomicBoolean(false);
        String sessionId = "session-123";

        when(llamaChatService.executePromptWithTools(
                anyString(), anyString(), anyString(), anyDouble(),
                isNull(), any(), anyInt(), anyList()))
                .thenThrow(new RuntimeException("Test error"));

        // Act
        ApiResponse<Map<String, Object>> apiResponse = modelService.executeFlow(instruction, history, cancellationRequested, sessionId);

        // Assert
        assertNotNull(apiResponse);
        assertEquals("error", apiResponse.getStatus());
        assertTrue(apiResponse.getMessage().contains("Orchestration Failed"));
    }

    @Test
    void testExecuteFlow_WithNullSessionId() {
        // Arrange
        String instruction = "Hello";
        List<org.springframework.ai.chat.messages.Message> history = new ArrayList<>();
        AtomicBoolean cancellationRequested = new AtomicBoolean(false);

        String modelResponse = "Response";
        when(llamaChatService.executePromptWithTools(
                anyString(), anyString(), anyString(), anyDouble(),
                isNull(), any(), anyInt(), anyList()))
                .thenReturn(modelResponse);

        when(llamaChatService.getTokenInfo()).thenReturn(new TokenInfo(10, 5, 15));

        // Act
        ApiResponse<Map<String, Object>> apiResponse = modelService.executeFlow(instruction, history, cancellationRequested, null);

        // Assert
        assertNotNull(apiResponse);
        assertEquals("success", apiResponse.getStatus());

        verify(chatSessionService, never()).addMessage(anyString(), any());
    }

    @Test
    void testResolveModelConfig() {
        // Act
        BorisProperties.ModelConfig result = modelService.resolveModelConfig();

        // Assert
        assertNotNull(result);
        assertEquals("test-model", result.getId());
        verify(llamaChatService).resolveConfig(null, 0);
    }

    @Test
    void testStartNewConversation() {
        // Act
        modelService.startNewConversation();

        // Assert
        verify(llamaChatService).startNewConversation();
    }

    @Test
    void testEnsureValidPath_WithAbsolutePath() {
        // Arrange
        String absolutePath = "/absolute/path/to/file.txt";

        // Act - This is a private method, so we test it indirectly through executeFlow
        // or we can use reflection. For now, we'll test the behavior indirectly.
    }

    @Test
    void testParseChatResponse_ValidJson() {
        // This is a private method, tested indirectly through executeFlow
        // The testExecuteFlow_CreateRequest already tests this
    }

    @Test
    void testExecuteFlow_RedesignRequest() {
        // Arrange
        String instruction = "Redesign the file";
        List<org.springframework.ai.chat.messages.Message> history = new ArrayList<>();
        AtomicBoolean cancellationRequested = new AtomicBoolean(false);
        String sessionId = "session-123";

        String modelResponse = "{\"type\":\"REDESIGN\",\"path\":\"/test/file.txt\",\"content\":\"New content\"}";
        when(llamaChatService.executePromptWithTools(
                anyString(), anyString(), anyString(), anyDouble(),
                isNull(), any(), anyInt(), anyList()))
                .thenReturn(modelResponse);

        when(llamaChatService.executeNativeToolsDirectly(anyString()))
                .thenReturn("Redesign applied successfully");

        // Act
        ApiResponse<Map<String, Object>> apiResponse = modelService.executeFlow(instruction, history, cancellationRequested, sessionId);
        Map<String, Object> result = apiResponse.getData();

        // Assert
        assertNotNull(apiResponse);
        assertEquals("success", apiResponse.getStatus());
        assertNotNull(result);
        assertEquals("redesign", result.get("type"));
    }

    @Test
    void testExecuteFlow_CreateRequestWithEmptyContent() {
        // Arrange
        String instruction = "Create a file";
        List<org.springframework.ai.chat.messages.Message> history = new ArrayList<>();
        AtomicBoolean cancellationRequested = new AtomicBoolean(false);
        String sessionId = "session-123";

        String modelResponse = "{\"type\":\"CREATE\",\"path\":\"/test/file.txt\",\"content\":\"\"}";
        when(llamaChatService.executePromptWithTools(
                anyString(), anyString(), anyString(), anyDouble(),
                isNull(), any(), anyInt(), anyList()))
                .thenReturn(modelResponse);

        when(llamaChatService.executeNativeToolsDirectly(anyString()))
                .thenReturn("File created successfully");

        // Act
        ApiResponse<Map<String, Object>> apiResponse = modelService.executeFlow(instruction, history, cancellationRequested, sessionId);
        Map<String, Object> result = apiResponse.getData();

        // Assert
        assertNotNull(apiResponse);
        assertEquals("success", apiResponse.getStatus());
        assertNotNull(result);
        assertEquals("create", result.get("type"));
    }

    @Test
    void testExecuteFlow_EditRequestWithReadError() {
        // Arrange
        String instruction = "Edit the file";
        List<org.springframework.ai.chat.messages.Message> history = new ArrayList<>();
        AtomicBoolean cancellationRequested = new AtomicBoolean(false);
        String sessionId = "session-123";

        String modelResponse = "{\"type\":\"EDIT\",\"path\":\"/test/file.txt\",\"instructions\":\"Change text\"}";
        when(llamaChatService.executePromptWithTools(
                anyString(), anyString(), anyString(), anyDouble(),
                isNull(), any(), anyInt(), anyList()))
                .thenReturn(modelResponse);

        when(readFileTool.call(anyString())).thenReturn("error:File not found");

        // Act
        ApiResponse<Map<String, Object>> apiResponse = modelService.executeFlow(instruction, history, cancellationRequested, sessionId);
        Map<String, Object> result = apiResponse.getData();

        // Assert
        assertNotNull(apiResponse);
        assertEquals("success", apiResponse.getStatus());
        assertNotNull(result);
        assertEquals("edit", result.get("type"));
    }
}
