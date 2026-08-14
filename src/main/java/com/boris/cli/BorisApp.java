package com.boris.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.boris.chat.ChatService;
import com.boris.llm.LlmClient;

@Command(name = "boris", mixinStandardHelpOptions = true, version = "1.0.0",
         description = "Boris CLI - Asistente de linea de comandos")
public class BorisApp implements Runnable {

    private static final String GREEN  = "\u001B[38;2;74;191;85m";
    private static final String GRAY   = "\u001B[90m";
    private static final String RESET  = "\u001B[0m";

    @Override
    public void run() {
        System.out.println();
        System.out.println(GREEN + "boris" + RESET);
        System.out.println(GRAY + "I'm an invisible" + RESET);
        System.out.println();

        LlmClient llmClient;
        try {
            String settingsPath = System.getProperty("user.home") + "/.boris/settings.json";
            llmClient = new LlmClient(settingsPath);
        } catch (Exception e) {
            System.err.println("Error inicializando Boris: " + e.getMessage());
            return;
        }

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

    public static void main(String[] args) {
        int exitCode = new CommandLine(new BorisApp()).execute(args);
        System.exit(exitCode);
    }
}
