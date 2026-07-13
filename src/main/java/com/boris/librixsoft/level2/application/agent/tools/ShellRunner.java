package com.boris.librixsoft.level2.application.agent.tools;

import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.exception.ToolException;
import com.boris.librixsoft.util.PathResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.Map;

/**
 * Único puente Java → SO. Ejecuta comandos de skills {@code type: shell}.
 * No implementa lógica de archivos: eso vive en los {@code .md}.
 */
@Component
@RequiredArgsConstructor
public class ShellRunner {

    private final BorisProperties borisProperties;

    public String run(String command, String workingDirectory) {
        return run(command, workingDirectory, Map.of());
    }

    public String run(String command, String workingDirectory, Map<String, String> env) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            ProcessBuilder processBuilder;
            if (os.contains("win")) {
                processBuilder = new ProcessBuilder("powershell", "-Command", command);
            } else {
                processBuilder = new ProcessBuilder("sh", "-c", command);
            }

            String resolvedDir = PathResolver.resolveWorkspacePath(
                    workingDirectory != null ? workingDirectory : "",
                    borisProperties.getWorkspacePrefix()
            );
            if (resolvedDir.isBlank()) {
                resolvedDir = PathResolver.resolveWorkspacePath(".", borisProperties.getWorkspacePrefix());
            }

            if (env != null) {
                processBuilder.environment().putAll(env);
            }
            // Convierte args de skill en variables $SKILL_<key> (contenido multilinea seguro)
            processBuilder.environment().put("SKILL_workspace",
                    borisProperties.getWorkspacePrefix() != null
                            ? borisProperties.getWorkspacePrefix()
                            : "");

            Process process = processBuilder
                    .directory(new File(resolvedDir))
                    .start();
            String stdout = new String(process.getInputStream().readAllBytes());
            String stderr = new String(process.getErrorStream().readAllBytes());
            int exitCode = process.waitFor();
            System.out.println("🖥️ \u001B[93m[Shell]\u001B[0m " + command + " → exit:" + exitCode);
            return exitCode == 0 ? stdout : "error: " + (stderr.isBlank() ? stdout : stderr);
        } catch (Exception e) {
            throw new ToolException("error: " + e.getMessage());
        }
    }
}
