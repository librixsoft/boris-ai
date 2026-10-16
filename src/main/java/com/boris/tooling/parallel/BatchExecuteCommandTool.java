package com.boris.tooling.parallel;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.task.TaskAborter;
import com.boris.tooling.ToolProgressNotifier;

/**
 * Batch command execution tool that CONCATENATES multiple commands into a single shell execution.
 * More efficient than parallel execution for most use cases (avoids process creation overhead).
 *
 * Features:
 * - Concatenates commands with && (stops on first failure) or ; (run all)
 * - Single command works exactly like old ExecuteCommandTool
 * - Single process = less overhead than N processes
 * - Timeout support
 * - Progress notifications to UI
 */
public class BatchExecuteCommandTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long DEFAULT_TIMEOUT_SECONDS = 120;

    public record CommandSpec(String command, String workingDirectory) {}
    public record CommandResult(String command, boolean success, int exitCode, String stdout, String stderr, String message) {}

    public String execute(List<CommandSpec> specs, TaskAborter aborter) {
        if (specs == null || specs.isEmpty()) {
            return formatError("commands list is empty");
        }

        List<CommandSpec> validSpecs = specs.stream()
            .filter(s -> s.command() != null && !s.command().isBlank())
            .toList();

        if (validSpecs.isEmpty()) {
            return formatError("no valid commands provided");
        }

        int totalCommands = validSpecs.size();

        if (totalCommands == 1) {
            CommandSpec spec = validSpecs.get(0);
            ToolProgressNotifier.notify("execute_command", "Ejecutando: " + spec.command());
            return formatSingleResult(executeCommand(spec.command(), spec.workingDirectory(), aborter));
        }

        String workingDir = validSpecs.get(0).workingDirectory();
        String concatenatedCommand = validSpecs.stream()
            .map(CommandSpec::command)
            .collect(Collectors.joining(" && "));

        String cmdPreview = validSpecs.stream()
            .map(CommandSpec::command)
            .map(c -> c.length() > 25 ? c.substring(0, 25) + "..." : c)
            .collect(Collectors.joining(" → "));

        ToolProgressNotifier.notify("execute_command", "Ejecutando " + totalCommands + " comandos: " + cmdPreview);

        CommandResult result = executeCommand(concatenatedCommand, workingDir, aborter);

        return formatBatchResult(result, totalCommands, concatenatedCommand);
    }

    public String execute(List<CommandSpec> specs) {
        return execute(specs, null);
    }

    private CommandResult executeCommand(String command, String dirStr, TaskAborter aborter) {
        if (aborter != null && aborter.isAborted()) {
            return new CommandResult(command, false, -1, "", "", "Aborted");
        }

        File workingDir = null;
        if (dirStr != null && !dirStr.isBlank()) {
            Path path = Paths.get(dirStr);
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                return new CommandResult(command, false, -1, "", "",
                    "Error: working directory does not exist: " + dirStr);
            }
            workingDir = path.toFile();
        } else {
            workingDir = new File(System.getProperty("user.dir", "."));
        }

        ProcessBuilder processBuilder = new ProcessBuilder();
        if (workingDir.exists()) {
            processBuilder.directory(workingDir);
        }

        boolean isWindows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (isWindows) {
            processBuilder.command("cmd.exe", "/c", command);
        } else {
            processBuilder.command("/bin/sh", "-c", command);
        }

        try {
            Process process = processBuilder.start();

            StringBuilder stdoutBuilder = new StringBuilder();
            StringBuilder stderrBuilder = new StringBuilder();

            Thread stdoutThread = new Thread(() -> readStream(process.getInputStream(), stdoutBuilder));
            Thread stderrThread = new Thread(() -> readStream(process.getErrorStream(), stderrBuilder));

            stdoutThread.start();
            stderrThread.start();

            boolean finished = process.waitFor(DEFAULT_TIMEOUT_SECONDS, TimeUnit.SECONDS);

            if (!finished) {
                process.destroyForcibly();
                stdoutThread.join(1000);
                stderrThread.join(1000);
                return new CommandResult(command, false, -1, stdoutBuilder.toString(), stderrBuilder.toString(),
                        "Error: Command timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds");
            }

            stdoutThread.join(2000);
            stderrThread.join(2000);

            int exitCode = process.exitValue();
            boolean success = (exitCode == 0);
            String stdout = stdoutBuilder.toString().trim();
            String stderr = stderrBuilder.toString().trim();

            String message = success ? "Commands executed successfully"
                                     : "Command failed with exit code " + exitCode;

            return new CommandResult(command, success, exitCode, stdout, stderr, message);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new CommandResult(command, false, -1, "", "", "Error: Command execution interrupted");
        } catch (IOException e) {
            return new CommandResult(command, false, -1, "", "", "Error: Failed to execute: " + e.getMessage());
        }
    }

    private void readStream(java.io.InputStream inputStream, StringBuilder builder) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                builder.append(line).append("\n");
            }
        } catch (IOException ignored) {
        }
    }

    private String formatSingleResult(CommandResult result) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", result.success());
            node.put("exitCode", result.exitCode());
            node.put("stdout", result.stdout());
            node.put("stderr", result.stderr());
            node.put("message", result.message());
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }

    private String formatBatchResult(CommandResult result, int totalCommands, String concatenatedCommand) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", result.success());
            node.put("totalCommands", totalCommands);
            node.put("executedAs", concatenatedCommand);
            node.put("exitCode", result.exitCode());
            node.put("stdout", result.stdout());
            node.put("stderr", result.stderr());
            node.put("message", result.message());
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }

    private String formatError(String message) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", false);
            node.put("error", message);
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }

    public void shutdown() {
        // No resources to clean up
    }
}
