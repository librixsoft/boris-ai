package com.boris.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.io.BufferedReader;
import java.io.InputStreamReader;

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

        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
        while (true) {
            System.out.print("\u001B[32m" + "☠️ _> " + RESET);
            String input;
            try {
                input = reader.readLine().trim();
            } catch (Exception e) {
                break;
            }

            if (input.isEmpty()) continue;

            if (input.equals("q") || input.equalsIgnoreCase("exit")) {
                break;
            }

            System.out.println(GRAY + "No entiendo: " + RESET + input);
        }

        System.out.println();
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new BorisApp()).execute(args);
        System.exit(exitCode);
    }
}
