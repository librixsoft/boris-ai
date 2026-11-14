package com.boris.cli;

import java.nio.file.Path;

import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import com.boris.chat.ChatService;
import com.boris.config.BorisConfiguration;
import com.boris.context.ProjectContext;
import com.boris.context.SearchSessionContext;
import com.boris.context.SessionLogResolver;
import com.boris.context.SessionLogWriter;
import com.boris.settings.Settings;
import com.boris.skill.SkillManager;
import com.boris.task.TaskAborter;

import picocli.CommandLine;
import picocli.CommandLine.Command;

@Command(name = "boris", mixinStandardHelpOptions = true, versionProvider = BorisVersion.class,
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

            // Inicializar project path al directorio de trabajo actual
            ProjectContext.setProjectPath(System.getProperty("user.dir"));

            // Generar sessionId para el registro de búsquedas en .boris/searchs/{SESION}.md
            SearchSessionContext.setSessionId(SearchSessionContext.generateSessionId());

            // Configurar logging de sesión
            boolean logsEnabled = settings.getLogsEnabled() != null ? settings.getLogsEnabled() : false;
            Path logFile = SessionLogResolver.sessionLogFile();
            SessionLogWriter sessionLogWriter = new SessionLogWriter(logFile, logsEnabled);
            sessionLogWriter.logSessionStart();
            chatService.setSessionLogWriter(sessionLogWriter);

            BorisUI ui = new BorisUI(chatService, skillManager, taskAborter, settings);
            ui.setSessionLogWriter(sessionLogWriter);
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
