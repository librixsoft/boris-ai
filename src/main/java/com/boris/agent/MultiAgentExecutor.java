package com.boris.agent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

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
 */
public class MultiAgentExecutor {

    private final Settings settings;
    private final TaskAborter taskAborter;
    private final ExecutorService executorService;
    private final int maxParallelWorkers;

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
     *
     * @param tasks List of task descriptions
     * @return Consolidated results from all worker agents
     */
    public String runParallelTasks(List<String> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            return "No tasks provided for parallel execution.";
        }

        if (taskAborter.isAborted()) {
            return "Parallel task execution aborted.";
        }

        List<CompletableFuture<WorkerResult>> futures = new ArrayList<>();
        for (int i = 0; i < tasks.size(); i++) {
            final int index = i + 1;
            final String taskDesc = tasks.get(i);
            CompletableFuture<WorkerResult> future = CompletableFuture.supplyAsync(() -> {
                if (taskAborter.isAborted()) {
                    return new WorkerResult(index, taskDesc, "Aborted before execution.");
                }
                try {
                    String result = executeWorkerTask(taskDesc, "worker_" + index);
                    return new WorkerResult(index, taskDesc, result);
                } catch (Exception e) {
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
                output.append("--- [Agent Worker Timed Out] ---\nError: Task exceeded timeout limit.\n\n");
            } catch (Exception e) {
                output.append("--- [Agent Worker Error] ---\nError: ").append(e.getMessage()).append("\n\n");
            }
        }

        output.append("=== END PARALLEL EXECUTION ===");
        return output.toString().trim();
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
        try {
            return executeWorkerTask(task, effectiveRole);
        } catch (Exception e) {
            return "Subagent execution error: " + e.getMessage();
        }
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
                - Use the available tools when you need to inspect or modify files or search the web.
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
