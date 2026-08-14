package com.boris.cli;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.jansi.Ansi;
import org.jline.jansi.AnsiConsole;

import com.boris.chat.ChatService;

public class BorisUI {

    private volatile boolean spinnerRunning = false;

    private final ChatService chatService;

    public BorisUI(String settingsPath) throws Exception {
        this.chatService = ChatService.withTools(settingsPath, "boris");
    }

    public void start() throws Exception {
        AnsiConsole.systemInstall();
        try (Terminal terminal = TerminalBuilder.builder().build()) {
            System.out.println();
            printGreen("boris");
            printlnGray("I'm an invisible");
            System.out.println();

            BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

            while (true) {
                System.out.print(Ansi.ansi().fgGreen());
                System.out.print("boris> ");
                System.out.flush();

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
                    for (String line : response.split("\\n", -1)) {
                        printlnGray(line);
                        System.out.println();
                    }
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
        System.out.print(Ansi.ansi().fgRgb(255, 255, 255));
        System.out.println(text + Ansi.ansi().reset());
    }

    private final String[] SPINNER = {"\u280B", "\u2819", "\u2838", "\u2834", "\u2826", "\u2807", "\u2809", "\u2808"};
    private int spinnerIndex = 0;

    private void startSpinner() {
        spinnerRunning = true;
        int tick = 0;
        while (spinnerRunning) {
            System.out.print("\r" + Ansi.ansi().fgRgb(255, 255, 255).bold());
            String status = tick < 3 ? "" : tick % 6 == 0 ? " working..." : tick % 4 == 0 ? " in progress." : " spinning..";
            long elapsed = tick / 12;
            String timeStr = formatTime(elapsed);
            System.out.print(SPINNER[spinnerIndex] + status + " " + timeStr);
            System.out.println(Ansi.ansi().reset());
            spinnerIndex = (spinnerIndex + 1) % SPINNER.length;
            tick++;
            try { Thread.sleep(80); }
            catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new com.boris.exceptions.BorisException("Spinner interrupted", e);
            }
        }
    }

    private String formatTime(long seconds) {
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60;
        long remainingSec = seconds % 60;
        if (minutes < 60) return minutes + "m" + (remainingSec > 0 ? remainingSec + "s" : "");
        long hours = minutes / 60;
        long remMin = minutes % 60;
        return hours + "h" + (remMin > 0 ? remMin + "m" : "") + (remainingSec > 0 ? remainingSec + "s" : "");
    }

    private void stopSpinner() {
        System.out.print("\r" + " ".repeat(4) + "\r");
        spinnerRunning = false;
    }
}
