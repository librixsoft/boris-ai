package com.boris.cli;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.boris.chat.ChatService;
import com.boris.config.BorisConfiguration;
import com.boris.settings.Settings;
import com.boris.skill.SkillManager;
import com.boris.task.TaskAborter;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "boris", mixinStandardHelpOptions = true, version = "1.0.0",
         description = "Boris CLI - Asistente de linea de comandos")
public class BorisApp implements Runnable {

    @Override
    public void run() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.register(BorisConfiguration.class);
            context.refresh();

            ChatService chatService = context.getBean(ChatService.class);
            SkillManager skillManager = context.getBean(SkillManager.class);
            TaskAborter taskAborter = context.getBean(TaskAborter.class);
            Settings settings = context.getBean(Settings.class);

            BorisUI ui = new BorisUI(chatService, skillManager, taskAborter, settings);
            ui.start();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new BorisApp()).execute(args);
        System.exit(exitCode);
    }
}
