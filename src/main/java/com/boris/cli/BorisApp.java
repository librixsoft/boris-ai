package com.boris.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import com.boris.chat.ChatService;

@Command(name = "boris", mixinStandardHelpOptions = true, version = "1.0.0",
         description = "Boris CLI - Asistente de linea de comandos")
public class BorisApp implements Runnable {



    @Override
    public void run() {
        try {
            new BorisUI(System.getProperty("user.home") + "/.boris/settings.json").start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new BorisApp()).execute(args);
        System.exit(exitCode);
    }
}
