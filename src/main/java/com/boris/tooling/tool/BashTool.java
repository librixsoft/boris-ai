package com.boris.tooling.tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.context.ProjectContext;
import com.boris.exceptions.BorisException;

public class BashTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TIMEOUT_SECONDS = 120;
    private static final ReentrantLock SHELL_LOCK = new ReentrantLock();

    public static String execute(Map<String, Object> args) {
        String command = (String) args.get("command");
        String workingDir = (String) args.get("working_dir");

        if (command == null || command.isBlank()) {
            return formatOutput(false, "Error: command is required", -1, "");
        }

        File workDir = workingDir != null && !workingDir.isBlank()
                ? new File(workingDir)
                : ProjectContext.projectRootOrCurrent().toFile();

        if (!workDir.exists() || !workDir.isDirectory()) {
            return formatOutput(false, "Error: working directory does not exist: " + workDir.getPath(), -1, "");
        }

        String os = System.getProperty("os.name", "").toLowerCase();
        ProcessBuilder pb = os.contains("win")
                ? new ProcessBuilder("cmd.exe", "/c", command)
                : new ProcessBuilder("/bin/sh", "-c", command);
        pb.directory(workDir);
        pb.redirectErrorStream(true);

        SHELL_LOCK.lock();
        try {
            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return formatOutput(false, "Error: command timed out after " + TIMEOUT_SECONDS + " seconds",
                        process.exitValue(), output.toString().trim());
            }

            int exitCode = process.exitValue();
            boolean success = exitCode == 0;
            String message = success
                    ? "Command executed successfully"
                    : "Command failed with exit code " + exitCode;

            return formatOutput(success, message, exitCode, output.toString().trim());

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BorisException("Error executing bash command: " + command, e);
        } finally {
            SHELL_LOCK.unlock();
        }
    }

    private static String formatOutput(boolean success, String message, int exitCode, String output) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", success);
            node.put("exit_code", exitCode);
            node.put("message", message);
            if (output != null && !output.isEmpty()) {
                node.put("output", output);
            }
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new BorisException("Failed to format bash tool output", e);
        }
    }
}
