package com.boris.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import com.boris.llm.LlmClient;

@Command(name = "boris", mixinStandardHelpOptions = true, version = "1.0.0",
         description = "Boris CLI - Asistente de linea de comandos")
public class BorisApp implements Runnable {

    private final LlmClient llmClient;

    public BorisApp() {
        String settingsPath = System.getProperty("user.home") + "/.boris/settings.json";
        try {
            this.llmClient = new LlmClient(settingsPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void run() {
        new BorisUI(llmClient).start();
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new BorisApp()).execute(args);
        System.exit(exitCode);
    }
}
