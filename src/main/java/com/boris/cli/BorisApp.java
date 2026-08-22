package com.boris.cli;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import com.boris.chat.ChatService;
import com.boris.config.AppContext;
import com.boris.memory.MemoryService;
import com.boris.settings.SettingsManager;

@Command(name = "boris", mixinStandardHelpOptions = true, version = "1.0.0",
         description = "Boris CLI - Asistente de linea de comandos")
public class BorisApp implements Runnable {

    @Override
    public void run() {
        try {
            SettingsManager mgr = new SettingsManager();
            mgr.ensureDefaultSettings();
            mgr.ensureAgentsMd();

            MemoryService memoryService = AppContext.getMemoryService();
            String settingsPath = System.getProperty("user.home") + "/.boris/settings.json";
            ChatService chatService = ChatService.withTools(settingsPath, "boris", memoryService);

            new BorisUI(chatService, memoryService).start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            AppContext.close();
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new BorisApp()).execute(args);
        System.exit(exitCode);
    }
}