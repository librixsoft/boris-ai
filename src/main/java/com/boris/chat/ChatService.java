package com.boris.chat;

import java.util.function.Supplier;

import org.springframework.ai.chat.client.ChatClient;

import com.boris.llm.LlmClient;
import com.boris.tooling.integration.ToolCallingConfig;

public class ChatService {

    public static final String EXIT_COMMAND = "EXIT";

    private final LlmProvider llmProvider;
    private final Supplier<ChatClient> chatClientSupplier;
    private final String botName;
    private boolean useToolCalling;

    public ChatService(LlmProvider llmProvider, String botName) {
        this(llmProvider, null, botName);
    }

    public ChatService(LlmProvider llmProvider, Supplier<ChatClient> chatClientSupplier, String botName) {
        this.llmProvider = llmProvider;
        this.chatClientSupplier = chatClientSupplier;
        this.botName = botName;
        this.useToolCalling = (chatClientSupplier != null);
    }

    public void setUseToolCalling(boolean useToolCalling) {
        this.useToolCalling = useToolCalling;
    }

    public boolean isUsingToolCalling() {
        return useToolCalling;
    }

    public String sendMessage(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return null;
        }

        String lower = userMessage.toLowerCase().trim();
        if ("q".equals(lower)) {
            return EXIT_COMMAND;
        }
        if ("exit".equals(lower)) {
            return EXIT_COMMAND;
        }

        try {
            if (useToolCalling && chatClientSupplier != null) {
                String response = sendWithTools(userMessage);
                return "*%s* %s".formatted(botName, response != null ? response : "");
            }
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }

        try {
            String response = llmProvider.send(userMessage);
            return "*%s* %s".formatted(botName, response != null ? response : "");
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }

    private String sendWithTools(String userMessage) {
        ChatClient client = chatClientSupplier.get();
        if (client == null) {
            throw new IllegalStateException("ChatClient not available for tool calling");
        }

        try {
            return client.prompt(userMessage).call().content() != null ? client.prompt(userMessage).call().content() : "";
        } catch (Exception e) {
            throw new RuntimeException("Tool calling failed: " + e.getMessage(), e);
        }
    }

    public static ChatService withTools(String settingsPath, String botName) throws Exception {
        var registry = ToolCallingConfig.buildDefaultRegistry();
        LlmClient llmClient = new LlmClient(settingsPath);
        var chatModel = extractChatModel(llmClient);
        ChatClient client = ChatClient.builder(chatModel)
                .defaultTools(ToolCallingConfig.buildToolCallbacks())
                .build();

        return new ChatService(null, () -> client, botName);
    }

    @SuppressWarnings("unchecked")
    private static org.springframework.ai.chat.model.ChatModel extractChatModel(LlmClient llmClient) throws Exception {
        var field = llmClient.getClass().getDeclaredField("chatClient");
        field.setAccessible(true);
        ChatClient c = (ChatClient) field.get(llmClient);
        var method = c.getClass().getMethod("getModel");
        return (org.springframework.ai.chat.model.ChatModel) method.invoke(c);
    }
}
