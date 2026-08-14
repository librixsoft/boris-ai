package com.boris.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.boris.chat.ChatService;
import com.boris.llm.LlmClient;

public class BorisUI {

    private static final String GREEN  = "\u001B[38;2;74;191;85m";
    private static final String GRAY   = "\u001B[90m";
    private static final String RESET  = "\u001B[0m";

    private final LlmClient llmClient;

    public BorisUI(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public void start() {
        System.out.println();
        System.out.println(GREEN + "boris" + RESET);
        System.out.println(GRAY + "I'm an invisible" + RESET);
        System.out.println();

        ChatService chatService = new ChatService(llmClient::send, "boris");
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

        while (true) {
            System.out.print("\u001B[32m" + "_> " + RESET);
            String input;
            try {
                input = reader.readLine();
            } catch (Exception e) {
                break;
            }

            if (input == null) break;

            input = input.trim();
            if (input.isEmpty()) continue;

            String response = chatService.sendMessage(input);
            if (response != null && ChatService.EXIT_COMMAND.equals(response)) {
                break;
            }
            if (response != null) {
                System.out.println(GRAY + response + RESET);
            }
        }

        System.out.println();
    }
}
