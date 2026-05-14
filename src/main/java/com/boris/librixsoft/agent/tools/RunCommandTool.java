package com.boris.librixsoft.agent.tools;

import com.boris.librixsoft.agent.exception.ToolException;
import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.util.PathResolver;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.IOException;

@Component
public class RunCommandTool implements ToolInterface {

    @Autowired
    private BorisProperties borisProperties;

    @Override
    public String call(String command, String workingDirectory) {
        try {
            String os = System.getProperty("os.name").toLowerCase();
            java.lang.ProcessBuilder processBuilder;

            if (os.contains("win")) {
                // Windows: usar PowerShell
                processBuilder = new java.lang.ProcessBuilder("powershell", "-Command", command);
            } else {
                // Unix/Linux/Mac: usar sh
                processBuilder = new java.lang.ProcessBuilder("sh", "-c", command);
            }

            // Resolve working directory relative to workspace if needed
            String resolvedDir = PathResolver.resolveWorkspacePath(
                    workingDirectory != null ? workingDirectory : "",
                    borisProperties.getWorkspacePrefix()
            );
            // If empty, use workspace root
            if (resolvedDir.isBlank()) {
                resolvedDir = PathResolver.resolveWorkspacePath(".", borisProperties.getWorkspacePrefix());
            }

            java.lang.Process process = processBuilder
                    .directory(new File(resolvedDir))
                    .start();
            java.io.InputStream stdoutStream = process.getInputStream();
            java.io.InputStream stderrStream = process.getErrorStream();
            String stdout = new String(stdoutStream.readAllBytes());
            String stderr = new String(stderrStream.readAllBytes());
            int exitCode  = process.waitFor();
            System.out.println("🖥️ \u001B[93m[Command]\u001B[0m " + command + " → exit:" + exitCode);
            return exitCode == 0 ? stdout : "error: " + stderr;
        } catch (Exception e) {
            throw new ToolException("error: " + e.getMessage());
        }
    }
}