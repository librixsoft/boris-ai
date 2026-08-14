package com.boris.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.jansi.Ansi;
import org.jline.jansi.AnsiConsole;

import com.boris.chat.ChatService;
import com.boris.llm.LlmClient;

public class BorisUI {

    private volatile boolean spinnerRunning = false;

    private final LlmClient llmClient;

    public BorisUI(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public void start() throws Exception {
        AnsiConsole.systemInstall();
        try (Terminal terminal = TerminalBuilder.builder().build()) {
            System.out.println();
            printGreen("boris");
            printlnGray("I'm an invisible");
            System.out.println();

            ChatService chatService = new ChatService(llmClient::send, "boris");
            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            while (true) {
                System.out.print(Ansi.ansi().fgGreen());
                System.out.print("_> ");
                System.out.println(Ansi.ansi().reset());

                String input;
                try {
                    input = reader.readLine();
                } catch (Exception e) {
                    break;
                }

                if (input == null) break;

                input = input.trim();
                if (input.isEmpty()) continue;

                String response;
                Thread spinnerThread = new Thread(() -> startSpinner());
                spinnerThread.setDaemon(true);
                spinnerThread.start();
                response = chatService.sendMessage(input);
                stopSpinner();

                if (response != null && ChatService.EXIT_COMMAND.equals(response)) {
                    break;
                }
                if (response != null) {
                    printlnGray(response);
                }
            }

            System.out.println();
        } finally {
            AnsiConsole.systemUninstall();
        }
    }

    private void printGreen(String text) {
        System.out.print(Ansi.ansi().fgRgb(74, 191, 85));
        System.out.println(text + Ansi.ansi().reset());
    }

    private void printlnGray(String text) {
        System.out.print(Ansi.ansi().fgBlack().bold());
        System.out.println(text + Ansi.ansi().reset());
    }

    private final String[] SPINNER = {"\u280B", "\u2819", "\u2838", "\u2834", "\u2826", "\u2807", "\u2809", "\u2808"};
    private int spinnerIndex = 0;

    private void startSpinner() {
        spinnerRunning = true;
        while (spinnerRunning) {
            System.out.print("\r" + Ansi.ansi().fgBlack().bold());
            System.out.println(SPINNER[spinnerIndex] + " " + Ansi.ansi().reset());
            spinnerIndex = (spinnerIndex + 1) % SPINNER.length;
            try { Thread.sleep(80); }
            catch (InterruptedException ignored) {}
        }
    }

    private void stopSpinner() {
        System.out.print("\r" + " ".repeat(4) + "\r");
        spinnerRunning = false;
    }
}
