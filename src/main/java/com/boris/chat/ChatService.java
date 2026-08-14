package com.boris.chat;

public class ChatService {

    public static final String EXIT_COMMAND = "EXIT";

    private final LlmProvider llmProvider;
    private final String botName;

    public ChatService(LlmProvider llmProvider, String botName) {
        this.llmProvider = llmProvider;
        this.botName = botName;
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
            String response = llmProvider.send(userMessage);
            return "*%s* %s".formatted(botName, response != null ? response : "");
        } catch (Exception e) {
            return "Error: " + e.getMessage();
        }
    }
}
