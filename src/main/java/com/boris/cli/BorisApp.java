package com.boris.cli;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.boris.chat.ChatService;
import com.boris.config.BorisConfiguration;
import com.boris.context.CodeIndexerService;
import com.boris.context.ContentIndexerService;
import com.boris.context.ContentRepository;
import com.boris.context.SymbolRepository;
import com.boris.llm.ollama.OllamaModelService;
import com.boris.settings.Settings;
import com.boris.skill.SkillManager;
import com.boris.task.TaskAborter;

import picocli.CommandLine;
import picocli.CommandLine.Command;

import java.nio.file.Files;
import java.nio.file.Path;

@Command(name = "boris", mixinStandardHelpOptions = true, version = "2.2.0",
         description = "Boris CLI - Asistente de linea de comandos")
public class BorisApp implements Runnable {

    @Override
    public void run() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(BorisConfiguration.class);
            context.refresh();

            // Auto-index current project at startup
            autoIndexCurrentProject();

            ChatService chatService = context.getBean(ChatService.class);
            SkillManager skillManager = context.getBean(SkillManager.class);
            TaskAborter taskAborter = context.getBean(TaskAborter.class);
            Settings settings = context.getBean(Settings.class);
            OllamaModelService ollamaModelService = context.getBean(OllamaModelService.class);

            BorisUI ui = new BorisUI(chatService, skillManager, taskAborter, settings, ollamaModelService);
            ui.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void autoIndexCurrentProject() {
        Path currentDir = Path.of(System.getProperty("user.dir"));
        String projectName = currentDir.getFileName().toString();

        if (SymbolRepository.isIndexed(projectName) || ContentRepository.isIndexed(projectName)) {
            System.out.println("[index] " + projectName + " already indexed (" +
                SymbolRepository.countByRepo(projectName) + " symbols, " +
                ContentRepository.countByRepo(projectName) + " content records)");
            return;
        }

        System.out.println("[index] Indexing " + projectName + "...");
        try {
            int symbolCount = CodeIndexerService.indexRepository(projectName, currentDir);
            int contentCount = ContentIndexerService.indexRepository(projectName, currentDir);
            System.out.println("[index] Indexed " + symbolCount + " symbols and " +
                contentCount + " content records from " + projectName);
        } catch (Exception e) {
            System.err.println("[index] Warning: Could not index project: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new BorisApp()).execute(args);
        System.exit(exitCode);
    }
}
