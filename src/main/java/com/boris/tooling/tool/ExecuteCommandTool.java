package com.boris.tooling.tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.exceptions.BorisException;
import com.boris.tooling.ToolDefinition;

public class ExecuteCommandTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long DEFAULT_TIMEOUT_SECONDS = 60;

    public static ToolDefinition execute_command() {
        var commandProp = Map.of("type", "string", "description", "The shell/terminal command line string to execute (e.g. 'git status', 'git commit -m \"msg\"', 'git push', 'mvn test')");
        var dirProp = Map.of("type", "string", "description", "Optional working directory path where the command should be executed");
        var properties = new LinkedHashMap<String, Object>();
        properties.put("command", commandProp);
        properties.put("workingDirectory", dirProp);
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return ToolDefinition.of(
                "execute_command",
                "Execute a shell or terminal command on the operating system (e.g., git commit, git push, mvn test, npm install). Returns standard output, error, and exit code.",
                schema);
    }

    public String execute(Map<String, Object> args) {
        String command = extractCommand(args);
        if (command == null || command.isBlank()) {
            return formatOutput(false, -1, "", "", "Error: command is required");
        }

        String dirStr = extractWorkingDirectory(args);
        File workingDir = null;
        if (dirStr != null && !dirStr.isBlank()) {
            Path path = Paths.get(dirStr);
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                return formatOutput(false, -1, "", "", "Error: working directory does not exist or is not a directory: " + dirStr);
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
                return formatOutput(false, -1, stdoutBuilder.toString(), stderrBuilder.toString(),
                        "Error: Command timed out after " + DEFAULT_TIMEOUT_SECONDS + " seconds");
            }

            stdoutThread.join(2000);
            stderrThread.join(2000);

            int exitCode = process.exitValue();
            boolean success = (exitCode == 0);
            String stdout = stdoutBuilder.toString().trim();
            String stderr = stderrBuilder.toString().trim();

            String message = success ? "Command executed successfully with exit code 0"
                                     : "Command failed with exit code " + exitCode;

            return formatOutput(success, exitCode, stdout, stderr, message);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return formatOutput(false, -1, "", "", "Error: Command execution interrupted");
        } catch (IOException e) {
            throw new BorisException("Failed to execute command: " + command, e);
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

    private String extractCommand(Map<String, Object> args) {
        if (args == null) return null;
        if (args.containsKey("command") && args.get("command") instanceof String s) return s;
        if (args.containsKey("cmd") && args.get("cmd") instanceof String s) return s;
        return null;
    }

    private String extractWorkingDirectory(Map<String, Object> args) {
        if (args == null) return null;
        if (args.containsKey("workingDirectory") && args.get("workingDirectory") instanceof String s) return s;
        if (args.containsKey("working_directory") && args.get("working_directory") instanceof String s) return s;
        if (args.containsKey("directory") && args.get("directory") instanceof String s) return s;
        if (args.containsKey("dir") && args.get("dir") instanceof String s) return s;
        if (args.containsKey("path") && args.get("path") instanceof String s) return s;
        return null;
    }

    private String formatOutput(boolean success, int exitCode, String stdout, String stderr, String message) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", success);
            node.put("exitCode", exitCode);
            node.put("stdout", stdout);
            node.put("stderr", stderr);
            node.put("message", message);
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new BorisException("Failed to format output", e);
        }
    }
}
