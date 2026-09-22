package com.boris.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.tool.ToolCallbacks;

import com.boris.llm.think.OllamaThinkSpringAiFactory;
import com.boris.settings.Settings;
import com.boris.task.TaskAborter;
import com.boris.tooling.tool.DeleteTool;
import com.boris.tooling.tool.EditTool;
import com.boris.tooling.tool.ListFilesTool;
import com.boris.tooling.tool.OfficeDocumentTool;
import com.boris.tooling.tool.PdfGenerationTool;
import com.boris.tooling.tool.ReadFileTool;
import com.boris.tooling.tool.SystemInfoTool;
import com.boris.tooling.tool.WebSearchTool;
import com.boris.tooling.tool.WriteTool;

/**
 * MultiAgentExecutor orchestrates concurrent execution of subtasks across multiple
 * autonomous worker agent instances.
 *
 * After parallel execution completes, an automatic integration phase inspects all
 * generated files and reconciles them (e.g. linking CSS in HTML, synchronizing
 * class names, verifying imports) to ensure end-to-end consistency.
 */
public class MultiAgentExecutor {

    private static final List<Consumer<String>> GLOBAL_STATUS_LISTENERS = new CopyOnWriteArrayList<>();

    /** Regex to extract absolute file paths from task descriptions. */
    private static final Pattern FILE_PATH_PATTERN = Pattern.compile(
            "(/[\\w.\\-/]+(?:\\.[a-zA-Z]{1,10}))");

    private final Settings settings;
    private final TaskAborter taskAborter;
    private final ExecutorService executorService;
    private final int maxParallelWorkers;
    private final List<Consumer<String>> statusListeners = new CopyOnWriteArrayList<>();

    public static void addGlobalStatusListener(Consumer<String> listener) {
        if (listener != null) {
            GLOBAL_STATUS_LISTENERS.add(listener);
        }
    }

    public static void removeGlobalStatusListener(Consumer<String> listener) {
        if (listener != null) {
            GLOBAL_STATUS_LISTENERS.remove(listener);
        }
    }

    public static void clearGlobalStatusListeners() {
        GLOBAL_STATUS_LISTENERS.clear();
    }

    public void addStatusListener(Consumer<String> listener) {
        if (listener != null) {
            this.statusListeners.add(listener);
        }
    }

    public void removeStatusListener(Consumer<String> listener) {
        if (listener != null) {
            this.statusListeners.remove(listener);
        }
    }

    public void emitStatus(String status) {
        if (status == null || status.isBlank()) return;
        for (Consumer<String> listener : statusListeners) {
            try {
                listener.accept(status);
            } catch (Exception ignored) {}
        }
        for (Consumer<String> listener : GLOBAL_STATUS_LISTENERS) {
            try {
                listener.accept(status);
            } catch (Exception ignored) {}
        }
    }

    public MultiAgentExecutor(Settings settings) {
        this(settings, new TaskAborter(), Math.max(4, Runtime.getRuntime().availableProcessors()));
    }

    public MultiAgentExecutor(Settings settings, TaskAborter taskAborter) {
        this(settings, taskAborter, Math.max(4, Runtime.getRuntime().availableProcessors()));
    }

    public MultiAgentExecutor(Settings settings, TaskAborter taskAborter, int maxParallelWorkers) {
        this.settings = settings;
        this.taskAborter = taskAborter != null ? taskAborter : new TaskAborter();
        this.maxParallelWorkers = Math.max(1, maxParallelWorkers);
        AtomicInteger threadCount = new AtomicInteger(1);
        this.executorService = Executors.newFixedThreadPool(this.maxParallelWorkers, r -> {
            Thread t = new Thread(r, "boris-subagent-" + threadCount.getAndIncrement());
            t.setDaemon(true);
            return t;
        });
    }

    /**
     * Executes a list of tasks concurrently across multiple agent instances.
     * After all workers complete, an automatic integration phase is triggered
     * to reconcile and link all generated files.
     *
     * @param tasks List of task descriptions
     * @return Consolidated results from all worker agents including integration
     */
    public String runParallelTasks(List<String> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return "No tasks provided for parallel execution.";
        }

        if (taskAborter.isAborted()) {
            return "Parallel task execution aborted.";
        }

        emitStatus("[status] 🚀 [Multi-Agent] Desplegando " + tasks.size() + " subagentes en paralelo...");

        List<CompletableFuture<WorkerResult>> futures = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            final int index = i + 1;
            final String taskDesc = tasks.get(i);
            CompletableFuture<WorkerResult> future = CompletableFuture.supplyAsync(() -> {
                if (taskAborter.isAborted()) {
                    emitStatus("[status] ✗ [Multi-Agent] Subagente #" + index + " abortado antes de iniciar.");
                    return new WorkerResult(index, taskDesc, "Aborted before execution.");
                }
                emitStatus("[status] 🤖 [Multi-Agent] Subagente #" + index + " iniciado para tarea: \"" + summarize(taskDesc) + "\"");
                try {
                    String result = executeWorkerTask(taskDesc, "worker_" + index);
                    emitStatus("[status] ✓ [Multi-Agent] Subagente #" + index + " completó su tarea.");
                    return new WorkerResult(index, taskDesc, result);
                } catch (Exception e) {
                    emitStatus("[status] ✗ [Multi-Agent] Subagente #" + index + " falló: " + e.getMessage());
                    return new WorkerResult(index, taskDesc, "Error: " + e.getMessage());
                }
            }, executorService);
            futures.add(future);
        }

        StringBuilder output = new StringBuilder();
        output.append("=== PARALLEL MULTI-AGENT EXECUTION (").append(tasks.size()).append(" tasks) ===\n\n");

        for (CompletableFuture<WorkerResult> future : futures) {
            try {
                WorkerResult res = future.get(120, TimeUnit.SECONDS);
                output.append("--- [Agent Worker #").append(res.workerIndex()).append("] ---\n");
                output.append("Task: ").append(res.task()).append("\n");
                output.append("Result:\n").append(res.output()).append("\n\n");
            } catch (TimeoutException te) {
                emitStatus("[status] ⚠️ [Multi-Agent] Un subagente excedió el tiempo límite.");
                output.append("--- [Agent Worker Timed Out] ---\nError: Task exceeded timeout limit.\n\n");
            } catch (Exception e) {
                emitStatus("[status] ✗ [Multi-Agent] Error en subagente: " + e.getMessage());
                output.append("--- [Agent Worker Error] ---\nError: ").append(e.getMessage()).append("\n\n");
            }
        }

        output.append("=== END PARALLEL EXECUTION ===\n\n");
        emitStatus("[status] ✓ [Multi-Agent] Ejecución paralela completada (" + tasks.size() + " agentes finalizados).");

        // --- AUTO-INTEGRATION PHASE ---
        String integrationResult = runAutoIntegration(tasks);
        if (integrationResult != null && !integrationResult.isBlank()) {
            output.append(integrationResult);
        }

        return output.toString().trim();
    }

    /**
     * Automatically spawns an integrator subagent after parallel execution to
     * reconcile, link, verify consistency, and synthesize all completed tasks.
     *
     * @param tasks The original task descriptions from the parallel execution
     * @return Integration result string, or null if integration was not needed
     */
    protected String runAutoIntegration(List<String> tasks) {
        if (taskAborter.isAborted() || tasks == null || tasks.size() < 2) {
            return null;
        }

        // Extract all file paths mentioned across all tasks (if any)
        Set<String> allPaths = new LinkedHashSet<>();
        for (String task : tasks) {
            allPaths.addAll(extractFilePaths(task));
        }

        if (!allPaths.isEmpty()) {
            emitStatus("[status] 🔗 [Multi-Agent] Iniciando fase de integración automática (" + allPaths.size() + " archivos detectados)...");
        } else {
            emitStatus("[status] 🔗 [Multi-Agent] Iniciando fase de integración automática (" + tasks.size() + " tareas en paralelo)...");
        }

        String integrationPrompt = buildIntegrationPrompt(allPaths, tasks);

        try {
            String result = executeWorkerTask(integrationPrompt, "integrator");
            emitStatus("[status] ✓ [Multi-Agent] Fase de integración completada.");
            StringBuilder sb = new StringBuilder();
            sb.append("=== AUTO-INTEGRATION PHASE ===\n");
            if (!allPaths.isEmpty()) {
                sb.append("Files integrated: ").append(String.join(", ", allPaths)).append("\n");
            }
            sb.append("Integrated Tasks: ").append(tasks.size()).append("\n");
            sb.append("Result:\n").append(result).append("\n");
            sb.append("=== END AUTO-INTEGRATION ===");
            return sb.toString();
        } catch (Exception e) {
            emitStatus("[status] ✗ [Multi-Agent] Fase de integración falló: " + e.getMessage());
            return "=== AUTO-INTEGRATION PHASE ===\nError: " + e.getMessage() + "\n=== END AUTO-INTEGRATION ===";
        }
    }

    /**
     * Extracts absolute file paths from a task description string.
     * Matches patterns like /path/to/file.ext
     *
     * @param text The task description text
     * @return Set of extracted file paths
     */
    static Set<String> extractFilePaths(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        Set<String> paths = new LinkedHashSet<>();
        Matcher matcher = FILE_PATH_PATTERN.matcher(text);
        while (matcher.find()) {
            String path = matcher.group(1);
            // Filter out common false positives
            if (!path.startsWith("/usr/") && !path.startsWith("/etc/")
                    && !path.startsWith("/bin/") && !path.startsWith("/sbin/")
                    && !path.startsWith("/dev/") && !path.startsWith("/proc/")
                    && !path.startsWith("/sys/") && !path.startsWith("/tmp/")) {
                paths.add(path);
            }
        }
        return paths;
    }

    /**
     * Builds a detailed integration prompt for the integrator subagent.
     * This prompt is generic across all types of parallel tasks (code, web, docs, research, backend/frontend, configs).
     *
     * @param filePaths Set of file paths to integrate (may be empty if no explicit file paths found)
     * @param originalTasks The original task descriptions for context
     * @return The integration prompt
     */
    static String buildIntegrationPrompt(Set<String> filePaths, List<String> originalTasks) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are the INTEGRATOR agent. Multiple tasks were just executed in parallel by separate subagents.\n");
        prompt.append("Your job is to read all generated artifacts/files, reconcile any inconsistencies, ensure all dependent components ");
        prompt.append("are fully linked and aligned, and unify the results into a cohesive, consistent whole.\n\n");

        if (filePaths != null && !filePaths.isEmpty()) {
            prompt.append("DETECTED FILES TO INTEGRATE:\n");
            for (String path : filePaths) {
                prompt.append("- ").append(path).append("\n");
            }
            prompt.append("\n");
        }

        prompt.append("PARALLEL TASKS EXECUTED:\n");
        for (int i = 0; i < originalTasks.size(); i++) {
            prompt.append("Task ").append(i + 1).append(": ").append(originalTasks.get(i)).append("\n");
        }

        prompt.append("\nINTEGRATION INSTRUCTIONS (execute using tools as applicable):\n");
        prompt.append("1. File & Artifact Verification: Use read_file to inspect all generated or referenced files.\n");
        prompt.append("2. Cross-Component Linking & Dependency Integration:\n");
        prompt.append("   - Web / Frontend: Link CSS stylesheets (<link rel=\"stylesheet\">) and JS scripts (<script src=\"\">) in HTML using proper relative paths. Ensure CSS selectors and JS DOM queries match the HTML elements.\n");
        prompt.append("   - Backend & Frontend: Verify API routes, request/response models, endpoint paths, and data types align between backend and frontend.\n");
        prompt.append("   - Documentation & Code: Ensure README, guides, or docs accurately reference created scripts, commands, and file locations.\n");
        prompt.append("   - Modules & Imports: Resolve missing import statements, package references, or configuration keys across created code files.\n");
        prompt.append("   - General / Research: Synthesize findings, harmonize naming conventions, and ensure no conflicting statements exist across outputs.\n");
        prompt.append("3. Edits & Fixes: Use apply_edit or write_file to correct any broken references, missing imports, unlinked styles, or inconsistencies.\n");
        prompt.append("4. Summary: Provide a clear summary of how the tasks were integrated and any adjustments made.\n");

        return prompt.toString();
    }

    /**
     * Spawns an isolated subagent instance with a specified role to handle a dedicated task.
     *
     * @param task Task description
     * @param role Optional role specification (e.g. researcher, coder, reviewer)
     * @return Subagent result
     */
    public String spawnSubagent(String task, String role) {
        if (task == null || task.isBlank()) {
            return "Task description cannot be empty.";
        }
        if (taskAborter.isAborted()) {
            return "Subagent execution aborted.";
        }

        String effectiveRole = (role != null && !role.isBlank()) ? role.trim() : "specialized_assistant";
        emitStatus("[status] 🤖 [Multi-Agent] Desplegando nuevo subagente [rol: " + effectiveRole + "]...");
        emitStatus("[status] ⚡ [Multi-Agent] Subagente [" + effectiveRole + "] ejecutando tarea: \"" + summarize(task) + "\"");

        try {
            String result = executeWorkerTask(task, effectiveRole);
            emitStatus("[status] ✓ [Multi-Agent] Subagente [" + effectiveRole + "] completó la tarea.");
            return result;
        } catch (Exception e) {
            emitStatus("[status] ✗ [Multi-Agent] Subagente [" + effectiveRole + "] falló: " + e.getMessage());
            return "Subagent execution error: " + e.getMessage();
        }
    }

    private static String summarize(String text) {
        if (text == null) return "";
        String trimmed = text.replaceAll("\\s+", " ").trim();
        if (trimmed.length() <= 60) {
            return trimmed;
        }
        return trimmed.substring(0, 57) + "...";
    }

    /**
     * Executes a task using an isolated ChatClient instance.
     */
    protected String executeWorkerTask(String task, String role) {
        if (taskAborter.isAborted()) {
            return "Execution aborted.";
        }

        if (settings == null || settings.getModel() == null) {
            return "Executed task [" + task + "] by agent role [" + role + "]";
        }

        try {
            ChatClient subagentClient = createSubagentChatClient(role);
            if (subagentClient == null) {
                return "Executed task [" + task + "] by agent role [" + role + "]";
            }

            return subagentClient.prompt(task).call().content();
        } catch (Exception e) {
            return "Subagent (" + role + ") encountered error: " + e.getMessage();
        }
    }

    private ChatClient createSubagentChatClient(String role) {
        if (settings == null || settings.getModel() == null) {
            return null;
        }

        String thinkMode = OllamaThinkSpringAiFactory.resolveThinkMode(settings);
        OpenAiChatModel chatModel = OllamaThinkSpringAiFactory.createChatModel(settings, thinkMode);

        String subagentSystemPrompt = """
                You are an autonomous subagent worker running as part of the Boris AI multi-agent swarm.
                Your assigned role is: %s.
                
                Guidelines:
                - Execute the assigned subtask directly, concisely, and completely.
                - Follow any shared contracts, class names, API signatures, and file naming conventions specified in your task.
                - Use the available tools when you need to inspect or modify files or search the web.
                - When assigned the 'integrator' or 'reviewer' role, inspect all interrelated files, reconcile discrepancies, link dependencies, and ensure complete end-to-end consistency.
                - Deliver a clear and accurate final response with your findings, code, or answer.
                """.formatted(role);

        SubagentToolCallbacks subagentTools = new SubagentToolCallbacks();

        return ChatClient.builder(chatModel)
                .defaultSystem(subagentSystemPrompt)
                .defaultTools(ToolCallbacks.from(subagentTools))
                .build();
    }

    public void shutdown() {
        executorService.shutdown();
    }

    public record WorkerResult(int workerIndex, String task, String output) {}

    /**
     * Worker tool definitions available to subagents without nesting recursion.
     */
    public static class SubagentToolCallbacks {
        private final ReadFileTool readFileTool = new ReadFileTool();
        private final WriteTool writeTool = new WriteTool();
        private final DeleteTool deleteTool = new DeleteTool();
        private final ListFilesTool listFilesTool = new ListFilesTool();
        private final EditTool editTool = new EditTool();
        private final SystemInfoTool systemInfoTool = new SystemInfoTool();
        private final WebSearchTool webSearchTool = new WebSearchTool();
        private final PdfGenerationTool pdfGenerationTool = new PdfGenerationTool();
        private final OfficeDocumentTool officeDocumentTool = new OfficeDocumentTool();

        @org.springframework.ai.tool.annotation.Tool(name = "read_file", description = "Read contents of a file")
        public String read_file(@org.springframework.ai.tool.annotation.ToolParam(description = "Path to file") String path) {
            return readFileTool.execute(Map.of("path", path));
        }

        @org.springframework.ai.tool.annotation.Tool(name = "write_file", description = "Write content to a file")
        public String write_file(@org.springframework.ai.tool.annotation.ToolParam(description = "Path to file") String path,
                                  @org.springframework.ai.tool.annotation.ToolParam(description = "Content to write") String content) {
            return writeTool.execute(Map.of("path", path, "content", content));
        }

        @org.springframework.ai.tool.annotation.Tool(name = "list_files", description = "List files in directory")
        public String list_files(@org.springframework.ai.tool.annotation.ToolParam(description = "Directory path") String path) {
            return listFilesTool.execute(Map.of("path", path));
        }

        @org.springframework.ai.tool.annotation.Tool(name = "apply_edit", description = "Apply a surgical edit to a file")
        public String apply_edit(@org.springframework.ai.tool.annotation.ToolParam(description = "File path") String path,
                                 @org.springframework.ai.tool.annotation.ToolParam(description = "Old text") String old_text,
                                 @org.springframework.ai.tool.annotation.ToolParam(description = "New text") String new_text) {
            return editTool.apply_edit(Map.of("path", path, "old_text", old_text, "new_text", new_text));
        }

        @org.springframework.ai.tool.annotation.Tool(name = "get_system_info", description = "Get system information")
        public String get_system_info() {
            return systemInfoTool.get_system_info(Map.of());
        }

        @org.springframework.ai.tool.annotation.Tool(name = "web_search", description = "Search the web")
        public String web_search(@org.springframework.ai.tool.annotation.ToolParam(description = "Query string") String query,
                                 @org.springframework.ai.tool.annotation.ToolParam(description = "Result count") Integer count) {
            Map<String, Object> args = new java.util.LinkedHashMap<>();
            args.put("query", query);
            if (count != null) args.put("count", count);
            return WebSearchTool.execute(args);
        }
    }
}

