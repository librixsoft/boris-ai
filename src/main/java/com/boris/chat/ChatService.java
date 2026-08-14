package com.boris.chat;

import java.util.function.Supplier;

import org.springframework.ai.chat.client.ChatClient;

import com.boris.llm.LlmClient;
import com.boris.settings.Settings;
import com.boris.settings.SettingsManager;
import com.boris.tooling.integration.ToolCallingConfig;

public class ChatService {

    public static final String EXIT_COMMAND = "EXIT";

    private final Supplier<ChatClient> chatClientSupplier;
    private final String botName;

    public ChatService(Supplier<ChatClient> chatClientSupplier, String botName) {
        this.chatClientSupplier = chatClientSupplier;
        this.botName = botName;
    }

    public String sendMessage(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new com.boris.exceptions.BorisException("User message cannot be null or empty");
        }

        String lower = userMessage.toLowerCase().trim();
        if ("q".equals(lower) || "exit".equals(lower)) {
            return EXIT_COMMAND;
        }

        ChatClient client = chatClientSupplier.get();
        if (client == null) {
            throw new IllegalStateException("Spring AI ChatClient is required — tool calling must be used. Use ChatService.withTools() to construct.");
        }

        String response = client.prompt(userMessage).call().content();
        return "*%s* %s".formatted(botName, response != null ? response : "");
    }

    public static ChatService withTools(String settingsPath, String botName) throws Exception {
        SettingsManager mgr = new SettingsManager();
        Settings s = mgr.loadSettings(settingsPath);
        String prompt = ToolCallingConfig.loadSystemPrompt(s);

        var chatModel = extractChatModel(new LlmClient(settingsPath));
        ChatClient client = ChatClient.builder(chatModel)
                .defaultSystem(prompt)
                .defaultTools(ToolCallingConfig.buildNativeToolCallbacks())
                .build();

        return new ChatService(() -> client, botName);
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
