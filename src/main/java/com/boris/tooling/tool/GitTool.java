package com.boris.tooling.tool;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.tooling.ToolDefinition;
import com.boris.exceptions.BorisException;

public class GitTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int TIMEOUT_SECONDS = 60;

    public static ToolDefinition git() {
        var commandProp = Map.of(
            "type", "string",
            "description", "Git command to execute: status, diff, log, add, commit, push, pull, branch, checkout, reset, stash, fetch, merge, rebase",
            "enum", List.of("status", "diff", "log", "add", "commit", "push", "pull", "branch", "checkout", "reset", "stash", "fetch", "merge", "rebase")
        );
        var argsProp = Map.of(
            "type", "string",
            "description", "Additional arguments for the git command (e.g., file paths, branch names, commit messages)"
        );
        var workingDirProp = Map.of(
            "type", "string",
            "description", "Working directory path (defaults to current directory)"
        );

        var properties = new LinkedHashMap<String, Object>();
        properties.put("command", commandProp);
        properties.put("args", argsProp);
        properties.put("working_dir", workingDirProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("command"));

        return ToolDefinition.of(
            "git",
            "Execute git commands for version control operations. Supports: status, diff, log, add, commit, push, pull, branch, checkout, reset, stash, fetch, merge, rebase.",
            schema
        );
    }

    public static String execute(Map<String, Object> args) {
        String command = (String) args.get("command");
        String gitArgs = (String) args.getOrDefault("args", "");
        String workingDir = (String) args.getOrDefault("working_dir", ".");

        if (command == null || command.isBlank()) {
            return formatOutput(false, "Error: command is required", "");
        }

        List<String> allowedCommands = List.of(
            "status", "diff", "log", "add", "commit", "push", "pull",
            "branch", "checkout", "reset", "stash", "fetch", "merge", "rebase"
        );

        if (!allowedCommands.contains(command)) {
            return formatOutput(false, "Error: command not allowed: " + command, "");
        }

        File workDir = new File(workingDir);
        if (!workDir.exists() || !workDir.isDirectory()) {
            return formatOutput(false, "Error: working directory does not exist: " + workingDir, "");
        }

        if (!isGitRepository(workDir)) {
            return formatOutput(false, "Error: not a git repository: " + workingDir, "");
        }

        List<String> cmdList = new ArrayList<>();
        cmdList.add("git");
        cmdList.add(command);

        if (gitArgs != null && !gitArgs.isBlank()) {
            cmdList.addAll(parseArguments(gitArgs));
        }

        try {
            ProcessBuilder pb = new ProcessBuilder(cmdList);
            pb.directory(workDir);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();

            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            boolean completed = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                process.destroyForcibly();
                return formatOutput(false, "Error: command timed out after " + TIMEOUT_SECONDS + " seconds", "");
            }

            int exitCode = process.exitValue();
            boolean success = exitCode == 0;
            String message = success ? "Command executed successfully" : "Command failed with exit code " + exitCode;

            return formatOutput(success, message, output.toString().trim());

        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new BorisException("Error executing git command: " + String.join(" ", cmdList), e);
        }
    }

    private static List<String> parseArguments(String args) {
        List<String> result = new ArrayList<>();
        boolean inQuotes = false;
        StringBuilder current = new StringBuilder();

        for (int i = 0; i < args.length(); i++) {
            char c = args.charAt(i);

            if (c == '"' || c == '\'') {
                inQuotes = !inQuotes;
            } else if (Character.isWhitespace(c) && !inQuotes) {
                if (current.length() > 0) {
                    result.add(current.toString());
                    current = new StringBuilder();
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            result.add(current.toString());
        }

        return result;
    }

    private static boolean isGitRepository(File dir) {
        File gitDir = new File(dir, ".git");
        if (gitDir.exists() && gitDir.isDirectory()) {
            return true;
        }
        File parent = dir.getParentFile();
        return parent != null && isGitRepository(parent);
    }

    private static String formatOutput(boolean success, String message, String output) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", success);
            node.put("message", message);
            if (output != null && !output.isEmpty()) {
                node.put("output", output);
            }
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new BorisException("Failed to format output", e);
        }
    }
}
