package com.boris.chat;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import com.boris.memory.MemoryService;
import com.boris.settings.Settings;
import com.boris.settings.SettingsManager;
import com.boris.task.TaskAborter;
import com.boris.tooling.integration.ToolCallingConfig;

public class ChatService {

    public static final String EXIT_COMMAND = "EXIT";

    private final Supplier<ChatClient> chatClientSupplier;
    private final String botName;
    private final TaskAborter taskAborter;
    private final MemoryService memoryService;
    private final boolean enableHistory;

    public ChatService(Supplier<ChatClient> chatClientSupplier, String botName, TaskAborter taskAborter,
                       MemoryService memoryService, boolean enableHistory) {
        this.chatClientSupplier = chatClientSupplier;
        this.botName = botName;
        this.taskAborter = taskAborter;
        this.memoryService = memoryService;
        this.enableHistory = enableHistory;
    }

    public String sendMessage(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new com.boris.exceptions.BorisException("User message cannot be null or empty");
        }

        if (taskAborter.isAborted()) {
            return null;
        }

        String lower = userMessage.toLowerCase().trim();
        if ("q".equals(lower) || "exit".equals(lower)) {
            return EXIT_COMMAND;
        }

        ChatClient client = chatClientSupplier.get();
        if (client == null) {
            throw new IllegalStateException("Spring AI ChatClient is required — tool calling must be used. Use ChatService.withTools() to construct.");
        }

        try {
            String fullMessage = buildPromptWithMemory(userMessage);

            if (enableHistory && memoryService != null) {
                memoryService.saveUserMessage(userMessage);
            }

            String response = client.prompt(fullMessage).call().content();

            if (enableHistory && memoryService != null && response != null && !response.isEmpty()) {
                memoryService.saveAssistantMessage(response);
            }

            return "*%s* %s".formatted(botName, response != null ? response : "");
        } catch (Exception e) {
            if (taskAborter.isAborted()) {
                return null;
            }
            throw new com.boris.exceptions.BorisException("Chat error", e);
        }
    }

    public void sendMessageStream(String userMessage, Consumer<String> onChunk, Runnable onComplete) {
        sendMessageStreamWithPrompt(buildPromptWithMemory(userMessage), userMessage, onChunk, onComplete);
    }

    public void sendMessageStreamWithPrompt(String fullPrompt, String userMessage, Consumer<String> onChunk, Runnable onComplete) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new com.boris.exceptions.BorisException("User message cannot be null or empty");
        }

        if (taskAborter.isAborted()) {
            return;
        }

        String lower = userMessage.toLowerCase().trim();
        if ("q".equals(lower) || "exit".equals(lower)) {
            return;
        }

        ChatClient client = chatClientSupplier.get();
        if (client == null) {
            throw new IllegalStateException("Spring AI ChatClient is required — tool calling must be used. Use ChatService.withTools() to construct.");
        }

        try {
            if (enableHistory && memoryService != null) {
                memoryService.saveUserMessage(userMessage);
            }

            StringBuilder responseBuilder = new StringBuilder();
            client.prompt(fullPrompt)
                    .stream()
                    .content()
                    .doOnNext(chunk -> {
                        onChunk.accept(chunk);
                        if (chunk != null) {
                            responseBuilder.append(chunk);
                        }
                    })
                    .doOnComplete(() -> {
                        if (enableHistory && memoryService != null) {
                            String fullResponse = responseBuilder.toString();
                            if (!fullResponse.isEmpty()) {
                                memoryService.saveAssistantMessage(fullResponse);
                            }
                        }
                        if (onComplete != null) onComplete.run();
                    })
                    .doOnError(e -> {
                        if (!taskAborter.isAborted()) {
                            throw new com.boris.exceptions.BorisException("Chat error", e);
                        }
                    })
                    .subscribe();
        } catch (Exception e) {
            if (taskAborter.isAborted()) {
                return;
            }
            throw new com.boris.exceptions.BorisException("Chat error", e);
        }
    }

    public static ChatService withTools(String settingsPath, String botName, MemoryService memoryService) throws Exception {
        SettingsManager mgr = new SettingsManager();
        mgr.ensureAgentsMd();
        Settings s = mgr.loadSettings(settingsPath);
        if (s == null || s.getModel() == null) {
            throw new IllegalStateException("Settings file not found or invalid: " + settingsPath);
        }

        String prompt = ToolCallingConfig.loadSystemPrompt(s);
        var chatModel = buildChatModel(s);

        boolean enableHistory = s.getEnableHistory() != null ? s.getEnableHistory() : true;

        if (memoryService != null && s.getMemory() != null) {
            memoryService.configureFromSettings(s.getMemory());
        }

        TaskAborter aborter = new TaskAborter();
        ChatService chatService = new ChatService(() -> null, botName, aborter, memoryService, enableHistory);

        ChatClient client = ChatClient.builder(chatModel)
                .defaultSystem(prompt)
                .defaultTools(ToolCallingConfig.buildNativeToolCallbacks(s))
                .build();

        return new ChatService(() -> client, botName, aborter, memoryService, enableHistory);
    }

    public TaskAborter getTaskAborter() {
        return taskAborter;
    }

    public void clearHistory() {
        if (memoryService != null) {
            memoryService.clearSession();
        }
    }

    public List<String> getConversationHistory() {
        if (memoryService == null) {
            return List.of();
        }
        return memoryService.getAllMessages().stream()
                .map(m -> m.getRole().toUpperCase() + ": " + m.getContent())
                .toList();
    }

    public long getMessageCount() {
        if (memoryService == null) {
            return 0;
        }
        return memoryService.getMessageCount();
    }

    private String buildPromptWithMemory(String currentMessage) {
        if (!enableHistory || memoryService == null) {
            return currentMessage;
        }

        return memoryService.buildContextPrompt(currentMessage);
    }

    private static org.springframework.ai.chat.model.ChatModel buildChatModel(Settings settings) throws Exception {
        String baseUrl = settings.getModel().getBaseUrl();
        String modelName = settings.getModel().getName();
        Map<String, String> envMap = settings.getEnv();
        String apiKey = (String) envMap.getOrDefault("OLLAMA_API_KEY", "ollama");

        OpenAiApi openAiApi = new OpenAiApi.Builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        return org.springframework.ai.openai.OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model(modelName).build())
                .build();
    }
}