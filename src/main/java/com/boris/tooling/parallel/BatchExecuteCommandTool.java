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

import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.context.ProjectContext;
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

    /** Contador de intentos bloqueados por sesión */
    private static final AtomicInteger BLOCKED_ATTEMPTS = new AtomicInteger(0);
    private static final AtomicInteger REDIRECT_ATTEMPTS = new AtomicInteger(0);
    private static final int MAX_BLOCKED_ATTEMPTS = 3;
    private static final int MAX_REDIRECT_ATTEMPTS = 3;

    /**
     * Comandos prohibidos para evitar inspección de JARs y acceso a ~/.m2/.
     * El modelo debe usar create_library_context para documentación.
     */
    private static final List<String> BLOCKED_PATTERNS = List.of(
        // Acceso a directorio Maven
        ".m2/",
        ".m2\\",
        // Inspección de JARs
        "jar tf",
        "jar xf",
        "jar -tf",
        "jar -xf",
        "jar --list",
        "jar --extract",
        // Decompilador
        "javap",
        // Extracción de JARs
        ".jar -d",
        ".jar com/",
        // Análisis binario de JARs
        "strings",
        "od -",
        "hexdump",
        "xxd"
    );

    /**
     * Comandos que tienen herramientas dedicadas y no deben usar execute_command.
     */
    private static final java.util.Map<String, String> REDIRECT_COMMANDS = java.util.Map.ofEntries(
        // Listar archivos → list_files
        java.util.Map.entry("ls ", "USA list_files en lugar de ls"),
        java.util.Map.entry("ls\t", "USA list_files en lugar de ls"),
        java.util.Map.entry("find ", "USA list_files o parallel_search en lugar de find"),
        java.util.Map.entry("tree ", "USA list_files en lugar de tree"),
        java.util.Map.entry("dir ", "USA list_files en lugar de dir"),
        // Leer archivos → batch_read_files
        java.util.Map.entry("cat ", "USA batch_read_files en lugar de cat"),
        java.util.Map.entry("head ", "USA batch_read_files en lugar de head"),
        java.util.Map.entry("tail ", "USA batch_read_files en lugar de tail"),
        java.util.Map.entry("less ", "USA batch_read_files en lugar de less"),
        java.util.Map.entry("more ", "USA batch_read_files en lugar de more"),
        java.util.Map.entry("sed -n", "USA batch_read_files en lugar de sed"),
        // Buscar texto → parallel_search o search_context
        java.util.Map.entry("grep ", "USA parallel_search o search_context en lugar de grep"),
        java.util.Map.entry("egrep ", "USA parallel_search en lugar de egrep"),
        java.util.Map.entry("fgrep ", "USA parallel_search en lugar de fgrep"),
        java.util.Map.entry("rg ", "USA parallel_search en lugar de rg"),
        java.util.Map.entry("ag ", "USA parallel_search en lugar de ag"),
        // Escribir archivos → write_file o apply_edit
        java.util.Map.entry("echo ", "USA write_file o apply_edit en lugar de echo"),
        java.util.Map.entry("printf ", "USA write_file en lugar de printf"),
        java.util.Map.entry("tee ", "USA write_file en lugar de tee")
    );

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

        // Verificar comandos prohibidos o redirigidos
        String blockedReason = checkBlockedCommand(command);
        if (blockedReason != null) {
            ToolProgressNotifier.notify("execute_command", "BLOQUEADO: " + command.substring(0, Math.min(30, command.length())));
            return new CommandResult(command, false, -1, "", "", blockedReason);
        }

        File workingDir = null;
        if (dirStr != null && !dirStr.isBlank()) {
            String resolvedDir = ProjectContext.resolveRelativePath(dirStr);
            Path path = Paths.get(resolvedDir);
            if (!Files.exists(path) || !Files.isDirectory(path)) {
                return new CommandResult(command, false, -1, "", "",
                    "Error: working directory does not exist: " + dirStr);
            }
            workingDir = path.toFile();
        } else {
            // Usar el proyecto del usuario si está definido, sino el directorio actual
            String projectPath = ProjectContext.getProjectPath();
            if (projectPath != null && !projectPath.isBlank()) {
                workingDir = new File(projectPath);
            } else {
                workingDir = new File(System.getProperty("user.dir", "."));
            }
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

    /**
     * Verifica si un comando está en la lista de bloqueados o debe redirigirse.
     * @return razón del bloqueo o null si está permitido
     */
    private String checkBlockedCommand(String command) {
        if (command == null) return null;
        String cmdLower = command.toLowerCase().trim();

        // 1. Verificar comandos que deben usar herramientas dedicadas
        for (java.util.Map.Entry<String, String> entry : REDIRECT_COMMANDS.entrySet()) {
            if (cmdLower.startsWith(entry.getKey())) {
                int attempts = REDIRECT_ATTEMPTS.incrementAndGet();
                if (attempts >= MAX_REDIRECT_ATTEMPTS) {
                    return "STOP. COMANDO BLOQUEADO PERMANENTEMENTE.\n" +
                           "Has usado " + entry.getKey().trim() + " " + attempts + " veces.\n" +
                           entry.getValue() + ".\n" +
                           "NO REINTENTES. CAMBIA A LA HERRAMIENTA CORRECTA.";
                }
                return "REDIRIGIR (intento " + attempts + "/" + MAX_REDIRECT_ATTEMPTS + "): " +
                       entry.getValue() + ".\n" +
                       "execute_command es para compilar, git, mvn, etc.\n" +
                       "NO para operaciones de archivos que tienen tools dedicadas.";
            }
        }

        // 2. Verificar patrones prohibidos (JARs, .m2, etc.)
        boolean isBlocked = false;
        for (String pattern : BLOCKED_PATTERNS) {
            if (cmdLower.contains(pattern.toLowerCase())) {
                isBlocked = true;
                break;
            }
        }

        // Verificación adicional: unzip con .jar en cualquier parte
        if (!isBlocked && cmdLower.contains("unzip") && cmdLower.contains(".jar")) {
            isBlocked = true;
        }

        if (isBlocked) {
            int attempts = BLOCKED_ATTEMPTS.incrementAndGet();

            if (attempts >= MAX_BLOCKED_ATTEMPTS) {
                return "STOP. DETENERSE AHORA.\n" +
                       "Has intentado " + attempts + " veces acceder a JARs/.m2.\n" +
                       "ESTÁ PROHIBIDO. NO HAY EXCEPCIONES.\n" +
                       "ÚNICA OPCIÓN: create_library_context(repo, version, project_path)\n" +
                       "NO REINTENTES ESTE COMANDO. CAMBIA DE ESTRATEGIA.";
            }

            return "BLOQUEADO (intento " + attempts + "/" + MAX_BLOCKED_ATTEMPTS + "): " +
                   "Acceso a JARs/.m2 prohibido.\n" +
                   "Usa create_library_context para documentación.\n" +
                   "Si insistes, serás bloqueado permanentemente.";
        }

        return null;
    }

    /** Resetea los contadores de intentos bloqueados (para tests o nueva sesión) */
    public static void resetBlockedAttempts() {
        BLOCKED_ATTEMPTS.set(0);
        REDIRECT_ATTEMPTS.set(0);
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
