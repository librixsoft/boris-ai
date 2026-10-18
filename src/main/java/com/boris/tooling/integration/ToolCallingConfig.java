package com.boris.tooling.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.boris.tooling.annotation.Tool;
import com.boris.tooling.annotation.ToolParam;
import com.boris.tooling.engine.ToolRegistry;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

import com.boris.exceptions.BorisException;
import com.boris.llm.LlmClient;
import com.boris.settings.Settings;
import com.boris.settings.SettingsManager;
import com.boris.tooling.tool.DeleteTool;
import com.boris.tooling.tool.EditTool;
import com.boris.tooling.tool.GitTool;
import com.boris.tooling.tool.OfficeDocumentTool;
import com.boris.tooling.tool.PdfGenerationTool;
import com.boris.tooling.tool.SystemInfoTool;
import com.boris.tooling.tool.WebSearchTool;
import com.boris.tooling.tool.WriteTool;
import com.boris.tooling.tool.LibraryContextTool;
import com.boris.tooling.tool.ListFilesTool;
import com.boris.tooling.parallel.BatchReadFileTool;
import com.boris.tooling.parallel.BatchEditTool;
import com.boris.tooling.parallel.BatchWebFetchTool;

// BatchGitHubSearchTool ELIMINADO - usar create_library_context
// ParallelSearchTool ELIMINADO
import com.boris.tooling.ToolProgressNotifier;
import com.boris.context.ProjectContext;
import com.boris.tooling.ToolResultNotifier;
import com.boris.task.decomposition.TaskPlannerTool;

/**
 * Tool configuration with PARALLEL-FIRST approach.
 * All tools that can benefit from parallelism use batch versions.
 * Single-item calls work the same but are processed through batch tools.
 */
public class ToolCallingConfig {

    private static final String[] SYSTEM_PROMPT_PATHS = {
        "~/.boris/AGENTS.md",
        "src/main/resources/prompts/init/AGENTS.md"
    };

    private static final Encoding TOKEN_ENCODING;
    static {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        TOKEN_ENCODING = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    private static int countTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return TOKEN_ENCODING.countTokens(text);
    }

    private static void notifyToolTokens(String toolName, String input, String output) {
        int inputTokens = countTokens(input);
        int outputTokens = countTokens(output);
        if (inputTokens > 0 || outputTokens > 0) {
            ToolResultNotifier.notifyTokenUsage(toolName, inputTokens, outputTokens);
        }
    }

    // Core tools (no parallel alternative needed)
    private final DeleteTool deleteTool;
    private final EditTool editTool;
    private final SystemInfoTool systemInfoTool;
    private final PdfGenerationTool pdfGenerationTool;
    private final OfficeDocumentTool officeDocumentTool;
    private final TaskPlannerTool taskPlannerTool;
    private final Settings settings;

    // PARALLEL tools (replace sequential versions)
    private final BatchReadFileTool batchReadFileTool;
    private final BatchEditTool batchEditTool;
    private final BatchWebFetchTool batchWebFetchTool;
    // batchExecuteCommandTool ELIMINADO
    // batchGitHubSearchTool ELIMINADO
    // parallelSearchTool ELIMINADO
    private final WebSearchTool webSearchTool;

    public ToolCallingConfig(
                            DeleteTool deleteTool,
                            EditTool editTool,
                            SystemInfoTool systemInfoTool,
                            PdfGenerationTool pdfGenerationTool,
                            OfficeDocumentTool officeDocumentTool,
                            TaskPlannerTool taskPlannerTool,
                            Settings settings) {
        this.deleteTool = deleteTool;
        this.editTool = editTool;
        this.systemInfoTool = systemInfoTool;
        this.pdfGenerationTool = pdfGenerationTool;
        this.officeDocumentTool = officeDocumentTool;
        this.taskPlannerTool = taskPlannerTool;
        this.settings = settings;

        // Initialize tools
        this.batchReadFileTool = new BatchReadFileTool();
        this.batchEditTool = new BatchEditTool();
        this.batchWebFetchTool = new BatchWebFetchTool();
        // batchExecuteCommandTool ELIMINADO
        // batchGitHubSearchTool ELIMINADO
        // parallelSearchTool ELIMINADO
        this.webSearchTool = new WebSearchTool();
    }

    public static String loadSystemPrompt(Settings settings) {
        StringBuilder prompt = new StringBuilder();

        String defaultPrompt = loadDefaultSystemPrompt();
        prompt.append(defaultPrompt.trim());

        try {
            for (String path : SYSTEM_PROMPT_PATHS) {
                Path resolved;
                if (path.startsWith("~/")) {
                    resolved = Path.of(System.getProperty("user.home"), path.substring(2));
                } else {
                    resolved = Path.of(path);
                }
                if (Files.exists(resolved)) {
                    String agentsContent = Files.readString(resolved).trim();
                    if (!agentsContent.isEmpty()) {
                        prompt.append("\n\n").append(agentsContent);
                    }
                }
            }
        } catch (IOException ignored) {}

        if (settings != null && settings.getSystemPrompt() != null && !settings.getSystemPrompt().isBlank()) {
            prompt.append("\n\n").append(settings.getSystemPrompt().trim());
        }

        return prompt.toString();
    }

    private static String loadDefaultSystemPrompt() {
        try {
            Path resourcePath = Path.of("src/main/resources/prompts/core/default-system-prompt.md");
            if (Files.exists(resourcePath)) {
                return Files.readString(resourcePath);
            }

            var resource = ToolCallingConfig.class.getClassLoader().getResourceAsStream("prompts/core/default-system-prompt.md");
            if (resource != null) {
                return new String(resource.readAllBytes());
            }

            throw new BorisException("Default system prompt file not found: src/main/resources/prompts/core/default-system-prompt.md");
        } catch (IOException e) {
            throw new BorisException("Failed to load default system prompt: " + e.getMessage(), e);
        }
    }
    public static ToolRegistry buildToolRegistry() {
        return buildToolRegistry(null);
    }

    public static ToolRegistry buildToolRegistry(Settings settings) {
        ToolCallingConfig config = createDefaultConfig(settings);
        return ToolRegistry.from(config);
    }

    private static ToolCallingConfig createDefaultConfig(Settings settings) {
        return new ToolCallingConfig(
                new DeleteTool(),
                new EditTool(),
                new SystemInfoTool(),
                new PdfGenerationTool(),
                new OfficeDocumentTool(),
                new TaskPlannerTool(),
                settings
        );
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> castToMap(Object o) {
        return (java.util.Map<String, Object>) o;
    }

    // ========== Core Single-Item Tools ==========

    // DISABLED: bulk_edit - replaced by streaming_line_* tools
    /*
    @Tool(
            name = "bulk_edit",
            description = "Create or replace a file with streaming visual feedback. Validates classes against indexed symbols BEFORE writing. Shows each line as it writes (green diff). Use query_symbols() first to verify classes exist.")
    public String bulk_edit(
            @ToolParam(description = "Path for the file to create/overwrite") String path,
            @ToolParam(description = "Complete file content") String content) {
        ToolProgressNotifier.notify("bulk_edit", "Validando y escribiendo: " + path);
        java.util.Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("path", path);
        args.put("content", content != null ? content : "");
        String result = com.boris.edit.BulkEditTool.bulk_edit(args);
        notifyToolTokens("bulk_edit", path + "\n" + content, result);
        return result;
    }
    */

    @Tool(
            name = "write_file",
            description = "Create a new file or overwrite an existing one with the given content. Returns success status and message.")
    public String write_file(
            @ToolParam(description = "Absolute or relative file path") String path,
            @ToolParam(description = "Content to write to the file") String content) {
        // Check if editing is allowed (must document first if there are compile errors)
        if (!com.boris.tooling.LearningLimiter.allowEdit()) {
            return "{\"success\":false,\"error\":\"Hay errores de compilación sin corregir. Antes de editar, usa query_framework_knowledge para documentarte sobre las clases involucradas.\"}";
        }

        // Clear compile error flag and reset learning limits when agent starts writing
        com.boris.tooling.LearningLimiter.clearCompileErrors();
        com.boris.tooling.LearningLimiter.reset();

        ToolProgressNotifier.notify("write_file", "Escribiendo: " + path);
        String result = WriteTool.execute(Map.of("path", path, "content", content != null ? content : ""));
        notifyToolTokens("write_file", path + "\n" + content, result);
        return result;
    }

    @Tool(
            name = "delete_file",
            description = "Delete a file at the given path. Returns success status and message.")
    public String delete_file(@ToolParam(description = "Absolute or relative path to the file to delete") String path) {
        ToolProgressNotifier.notify("delete_file", "Eliminando: " + path);
        String result = deleteTool.execute(Map.of("path", path));
        notifyToolTokens("delete_file", path, result);
        return result;
    }

    @Tool(
            name = "list_files",
            description = "List files and directories in a given path. Returns file names, types, and sizes.")
    public String list_files(
            @ToolParam(description = "Directory path to list (absolute or relative)") String path,
            @ToolParam(description = "If true, list files recursively (default: false)") Boolean recursive,
            @ToolParam(description = "Maximum depth for recursive listing (default: 3)") Integer max_depth,
            @ToolParam(description = "Optional glob pattern to filter files (e.g., *.java, *.ts)") String pattern) {
        ToolProgressNotifier.notify("list_files", "Listando: " + path);
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("path", path != null ? path : ".");
        if (recursive != null) args.put("recursive", recursive);
        if (max_depth != null) args.put("max_depth", max_depth);
        if (pattern != null) args.put("pattern", pattern);
        String result = ListFilesTool.execute(args);
        notifyToolTokens("list_files", path != null ? path : ".", result);
        return result;
    }

    @Tool(
            name = "apply_edit",
            description = "Apply a surgical edit to an existing file by finding old_text and replacing it with new_text. Returns success status, message, and a diff showing changes. IMPORTANT: Always display the diff to the user with lines starting with '- ' for removed and '+ ' for added content.")
    public String apply_edit(@ToolParam(description = "Absolute or relative file path") String path,
                             @ToolParam(description = "Exact text to find and replace in the file") String old_text,
                             @ToolParam(description = "Replacement text") String new_text) {
        // Check if editing is allowed (must document first if there are compile errors)
        if (!com.boris.tooling.LearningLimiter.allowEdit()) {
            return "{\"success\":false,\"error\":\"Hay errores de compilación sin corregir. Antes de editar, usa query_framework_knowledge para documentarte sobre las clases involucradas.\"}";
        }

        // Clear compile error flag and reset learning limits when agent starts editing
        com.boris.tooling.LearningLimiter.clearCompileErrors();
        com.boris.tooling.LearningLimiter.reset();

        ToolProgressNotifier.notify("apply_edit", "Editando archivo: " + path);
        String inputText = path + "\n" + old_text + "\n" + new_text;
        String result = EditTool.apply_edit(Map.of("path", path, "old_text", old_text, "new_text", new_text));
        notifyToolTokens("apply_edit", inputText, result);
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var node = mapper.readTree(result);
            boolean success = node.has("success") && node.get("success").asBoolean();
            String diff = node.has("diff") ? node.get("diff").asText() : null;
            if (diff != null && !diff.isEmpty()) {
                ToolResultNotifier.notifyEdit(path, diff, success);
            }
        } catch (Exception ignored) {}
        return result;
    }

    @Tool(
            name = "multi_edit",
            description = "Apply multiple sequential edits to a file. Each edit replaces old_text with new_text. Returns success status and message.")
    public String multi_edit(@ToolParam(description = "Absolute or relative file path") String path,
                             @ToolParam(description = "Array of edit objects, each with old_text and new_text fields") java.util.List<java.util.Map<String, Object>> edits) {
        StringBuilder inputBuilder = new StringBuilder(path);
        for (var edit : edits) {
            inputBuilder.append("\n").append(edit.get("old_text")).append("\n").append(edit.get("new_text"));
        }
        java.util.Map<String, Object> multiEditParams = new java.util.LinkedHashMap<>();
        multiEditParams.put("path", path);
        multiEditParams.put("edits", edits);
        String result = EditTool.multi_edit(multiEditParams);
        notifyToolTokens("multi_edit", inputBuilder.toString(), result);
        return result;
    }

    @Tool(
            name = "revert_edit",
            description = "Revert a previous edit by finding the edited text and replacing it with its original value. Returns success status and message.")
    public String revert_edit(@ToolParam(description = "Absolute or relative file path") String path,
                              @ToolParam(description = "Exact text to find and replace with new_text (the original content)") String old_text,
                              @ToolParam(description = "Replacement text (original value to restore)") String new_text) {
        String inputText = path + "\n" + old_text + "\n" + new_text;
        String result = EditTool.revert_edit(Map.of("path", path, "old_text", old_text, "new_text", new_text));
        notifyToolTokens("revert_edit", inputText, result);
        return result;
    }

    @Tool(
            name = "get_system_info",
            description = "Get system information including OS name, memory, CPU cores, and hostname.")
    public String get_system_info() {
        String result = systemInfoTool.get_system_info(Map.of());
        notifyToolTokens("get_system_info", "", result);
        return result;
    }

    @Tool(
            name = "generate_pdf",
            description = "Generate a PDF file from HTML, Markdown, or plain text content.")
    public String generate_pdf(@ToolParam(description = "Content to convert to PDF (HTML, Markdown, or plain text)") String content,
                              @ToolParam(description = "Output file path for the PDF") String outputPath,
                              @ToolParam(description = "Type of content: 'html', 'markdown', or 'text'") String contentType) {
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("content", content);
        args.put("outputPath", outputPath);
        args.put("contentType", contentType);
        String inputText = outputPath + "\n" + content;
        String result = pdfGenerationTool.execute(args);
        notifyToolTokens("generate_pdf", inputText, result);
        return result;
    }

    @Tool(
            name = "create_office_document",
            description = "Create personalized Word (.docx), PowerPoint (.pptx), or Excel (.xlsx) documents with advanced styling, multiple layouts, text effects, and corporate design templates.")
    public String create_office_document(
            @ToolParam(description = "Document type: 'word', 'powerpoint', or 'excel'") String documentType,
            @ToolParam(description = "Absolute or relative output file path") String outputPath,
            @ToolParam(description = "Document title") String title,
            @ToolParam(description = "Document content (plain text; for tables use pipe-separated: col1|col2|col3; separate columns with ---)") String content,
            @ToolParam(description = "JSON object with complete styling: {primaryColor, secondaryColor, accentColor, textColor, backgroundColor, fontFamily, headerFontSize, bodyFontSize, boldTitle, italicBody, underlineHeaders, marginTop, marginBottom, marginLeft, marginRight, lineSpacing, layout (oneColumn|twoColumn|threeColumn|grid), style (corporate|modern|minimal|colorful), headerStyle, borderStyle, borderWidth, shadowEffect, and table styling colors}") java.util.Map<String, Object> customization) {
        Map<String, Object> params = new java.util.LinkedHashMap<>();
        params.put("documentType", documentType);
        params.put("outputPath", outputPath);
        params.put("title", title);
        params.put("content", content);
        params.put("customization", customization != null ? customization : new HashMap<>());
        String inputText = documentType + "\n" + outputPath + "\n" + title + "\n" + content;
        String result = OfficeDocumentTool.execute(params);
        notifyToolTokens("create_office_document", inputText, result);
        return result;
    }

    // ========== Web Tools ==========

    @Tool(
            name = "web_search",
            description = "Search the web using DuckDuckGo. Returns titles, URLs, and snippets.")
    public String web_search(@ToolParam(description = "Search query string") String query,
                             @ToolParam(description = "Number of results to return (1-10, default 5)") Integer count) {
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("query", query);
        if (count != null) args.put("count", count);
        String result = WebSearchTool.execute(args);
        notifyToolTokens("web_search", query, result);
        ToolResultNotifier.notifyQueryResult("web_search", query, result);
        return result;
    }

    @Tool(
            name = "web_fetch",
            description = "Fetch and extract content from URLs. Can extract text, code blocks, or specific sections.")
    public String web_fetch(@ToolParam(description = "Single URL or array of URLs to fetch") Object urls,
                            @ToolParam(description = "Optional CSS selector to extract specific content (e.g., 'article', '.content', '#readme')") String selector,
                            @ToolParam(description = "If true, extract code blocks from the page (useful for documentation)") Boolean extract_code) {
        List<BatchWebFetchTool.FetchSpec> specs = new ArrayList<>();
        boolean extractCode = extract_code != null && extract_code;

        if (urls instanceof String url) {
            specs.add(new BatchWebFetchTool.FetchSpec(url, selector, extractCode));
        } else if (urls instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String url) {
                    specs.add(new BatchWebFetchTool.FetchSpec(url, selector, extractCode));
                } else if (item instanceof Map<?, ?> map) {
                    String url = (String) map.get("url");
                    String sel = map.containsKey("selector") ? (String) map.get("selector") : selector;
                    Boolean code = map.containsKey("extract_code") ? (Boolean) map.get("extract_code") : extract_code;
                    if (url != null) {
                        specs.add(new BatchWebFetchTool.FetchSpec(url, sel, code != null && code));
                    }
                }
            }
        }

        if (specs.isEmpty()) {
            return "{\"success\":false,\"error\":\"No valid URLs provided\"}";
        }

        String result = batchWebFetchTool.execute(specs);
        String urlsStr = specs.stream().map(BatchWebFetchTool.FetchSpec::url).toList().toString();
        notifyToolTokens("web_fetch", urlsStr, result);
        ToolResultNotifier.notifyQueryResult("web_fetch", urlsStr, result);
        return result;
    }

    // ========== Git Tool ==========

    @Tool(
            name = "git",
            description = "Execute git commands for version control. Supports: status, diff, log, add, commit, push, pull, branch, checkout, reset, stash, fetch, merge, rebase. Use for all git operations.")
    public String git(
            @ToolParam(description = "Git command: status, diff, log, add, commit, push, pull, branch, checkout, reset, stash, fetch, merge, rebase") String command,
            @ToolParam(description = "Additional arguments (file paths, branch names, commit messages, etc.)", required = false) String args,
            @ToolParam(description = "Working directory (default: current)", required = false) String working_dir) {

        if (command == null || command.isBlank()) {
            return "{\"success\": false, \"error\": \"Command is required\"}";
        }

        ToolProgressNotifier.notify("git", "git " + command + (args != null ? " " + args : ""));

        Map<String, Object> toolArgs = new java.util.LinkedHashMap<>();
        toolArgs.put("command", command);
        if (args != null) toolArgs.put("args", args);
        if (working_dir != null) toolArgs.put("working_dir", working_dir);

        String result = GitTool.execute(toolArgs);
        String inputText = "git " + command + (args != null ? " " + args : "");
        notifyToolTokens("git", inputText, result);
        ToolResultNotifier.notifyQueryResult("git", inputText, result);
        return result;
    }

    // ========== Existing Batch Tools ==========

    @Tool(
            name = "read_file",
            description = "Read a single file from disk. Returns file content.")
    public String read_file(
            @ToolParam(description = "Absolute or relative file path to read") String path,
            @ToolParam(description = "Optional maximum lines to read (default: all)", required = false) Integer max_lines_per_file) {
        String resolvedPath = ProjectContext.resolveRelativePath(path != null ? path : "");
        String result = batchReadFileTool.execute(List.of(resolvedPath), max_lines_per_file);
        notifyToolTokens("read_file", path != null ? path : "", result);
        ToolResultNotifier.notifyQueryResult("read_file", path != null ? path : "", result);
        return result;
    }

    @Tool(
            name = "batch_read_files",
            description = "Read files from disk. Use this when inspecting files, dependencies, or tests.")
    public String batch_read_files(
            @ToolParam(description = "List of absolute or relative file paths to read") java.util.List<String> paths,
            @ToolParam(description = "Optional maximum lines to read per file (default: all)", required = false) Integer max_lines_per_file) {
        List<String> resolvedPaths = new ArrayList<>();
        if (paths != null) {
            for (String p : paths) {
                resolvedPaths.add(ProjectContext.resolveRelativePath(p));
            }
        }
        String result = batchReadFileTool.execute(resolvedPaths, max_lines_per_file);
        String pathsStr = !resolvedPaths.isEmpty() ? String.join(", ", resolvedPaths) : "empty";
        notifyToolTokens("batch_read_files", pathsStr, result);
        ToolResultNotifier.notifyQueryResult("batch_read_files", pathsStr, result);
        return result;
    }

    // ========== Maven Build Tool ==========

    @Tool(
            name = "maven_build",
            description = "Execute Maven commands (compile, test, package, clean). Use to compile and check for errors.")
    public String maven_build(
            @ToolParam(description = "Maven goal: compile, test, package, clean, verify, install", required = false) String goal,
            @ToolParam(description = "Project directory (optional, default: current workspace)", required = false) String project_path) {

        // Solo permitir goals seguros
        java.util.Set<String> allowedGoals = java.util.Set.of(
            "compile", "test", "package", "clean", "verify", "install",
            "test-compile", "clean compile", "clean test", "clean package"
        );

        if (goal == null || goal.isBlank()) {
            goal = "compile";
        }

        String normalizedGoal = goal.toLowerCase().trim();
        if (!allowedGoals.contains(normalizedGoal)) {
            return "{\"success\": false, \"error\": \"Goal no permitido: " + goal + ". Usa: compile, test, package, clean, verify, install\"}";
        }

        String dir = (project_path != null && !project_path.isBlank())
            ? ProjectContext.resolveRelativePath(project_path)
            : ProjectContext.getProjectPath();
        if (dir == null || dir.isBlank()) {
            dir = System.getProperty("user.dir");
        }

        ToolProgressNotifier.notify("maven_build", "mvn " + normalizedGoal + " en " + dir);

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add("mvn");
            for (String part : normalizedGoal.split("\\s+")) {
                if (!part.isBlank()) {
                    cmd.add(part);
                }
            }
            cmd.add("-q");

            ProcessBuilder pb = new ProcessBuilder(cmd);
            pb.directory(new java.io.File(dir));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> resultMap = new java.util.LinkedHashMap<>();

            if (exitCode == 0) {
                resultMap.put("success", true);
                resultMap.put("message", "mvn " + normalizedGoal + " completado OK");
                return mapper.writeValueAsString(resultMap);
            } else {
                // Mark that there are compile errors - block further learning
                com.boris.tooling.LearningLimiter.markCompileErrors();
                
                String errors = extractCompileErrors(output);
                resultMap.put("success", false);
                resultMap.put("exit_code", exitCode);
                resultMap.put("errors", errors);
                return mapper.writeValueAsString(resultMap);
            }
        } catch (Exception e) {
            return "{\"success\": false, \"error\": \"" + e.getMessage() + "\"}";
        }
    }

    private String extractCompileErrors(String output) {
        StringBuilder errors = new StringBuilder();
        for (String line : output.split("\n")) {
            if (line.contains("[ERROR]") || line.contains("error:") ||
                line.contains("cannot find symbol") || line.contains("incompatible types")) {
                errors.append(line.trim()).append("\n");
            }
        }
        return errors.length() > 0 ? errors.toString() : output;
    }

    // ========== Task Decomposition Tools ==========

    @Tool(
            name = "plan_task",
            description = "Analyze a complex task and decompose it into ordered microtasks. Use this BEFORE starting tasks that involve multiple files, new components, or architectural changes. Returns a plan ID and list of steps to execute.")
    public String plan_task(
            @ToolParam(description = "The task to analyze and decompose into microtasks") String task_description,
            @ToolParam(description = "Maximum number of microtasks to generate (default: 10)") Integer max_steps) {
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("task_description", task_description);
        if (max_steps != null) {
            args.put("max_steps", max_steps);
        }
        String result = taskPlannerTool.executePlanTask(args);
        notifyToolTokens("plan_task", task_description, result);
        return result;
    }

    @Tool(
            name = "get_plan",
            description = "Get the current status and progress of a task plan.")
    public String get_plan(
            @ToolParam(description = "The ID of the plan to retrieve") String plan_id) {
        String result = taskPlannerTool.executeGetPlan(Map.of("plan_id", plan_id));
        notifyToolTokens("get_plan", plan_id, result);
        return result;
    }

    @Tool(
            name = "complete_microtask",
            description = "Mark a microtask as completed and get the next task to execute. Call this after finishing each step in a plan.")
    public String complete_microtask(
            @ToolParam(description = "The ID of the plan") String plan_id,
            @ToolParam(description = "The order number of the completed microtask") Integer task_order,
            @ToolParam(description = "Summary of what was accomplished") String resultSummary) {
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("plan_id", plan_id);
        args.put("task_order", task_order);
        if (resultSummary != null) {
            args.put("result", resultSummary);
        }
        String inputText = plan_id + "\n" + task_order + "\n" + (resultSummary != null ? resultSummary : "");
        String result = taskPlannerTool.executeCompleteMicrotask(args);
        notifyToolTokens("complete_microtask", inputText, result);
        return result;
    }

    @Tool(
            name = "list_active_plans",
            description = "List all active task plans and their progress.")
    public String list_active_plans() {
        String result = taskPlannerTool.executeListActivePlans(Map.of());
        notifyToolTokens("list_active_plans", "", result);
        return result;
    }

    // ========== Streaming Line Edit Tools (DISABLED - using text format instead) ==========
    // El modelo escribe código con formato ```java:path y StreamingFileWriter lo detecta
    // y guarda automáticamente. No se necesitan herramientas.
    /*
    @Tool(
            name = "streaming_line_start",
            description = "STEP 1: Start writing a file line by line.")
    public String streaming_line_start(
            @ToolParam(description = "Path for the file to create/edit") String path) {
        // DISABLED - use text format ```java:path instead
        return "{\"error\": \"Use text format ```java:path instead of this tool\"}";
    }

    @Tool(
            name = "streaming_line_write",
            description = "STEP 2: Write ONE line of code.")
    public String streaming_line_write(
            @ToolParam(description = "Single line of code to write") String line) {
        // DISABLED - use text format ```java:path instead
        return "{\"error\": \"Use text format ```java:path instead of this tool\"}";
    }

    @Tool(
            name = "streaming_line_end",
            description = "STEP 3: Finish and save the file.")
    public String streaming_line_end() {
        // DISABLED - use text format ```java:path instead
        return "{\"error\": \"Use text format ```java:path instead of this tool\"}";
    }
    */

    // ========== Code Symbol Query Tools ==========

    @Tool(
            name = "create_library_context",
            description = "Downloads GitHub repo and indexes it into SQLite for on-demand symbol queries. " +
                         "NOTE: Do NOT browse or explore all classes after indexing; use only for specific symbols when strictly needed.")
    public String create_library_context(
            @ToolParam(description = "GitHub repo in format owner/repo (e.g., mabe02/lanterna)") String repo,
            @ToolParam(description = "Version, tag or branch (default: main)") String version,
            @ToolParam(description = "Project path where repo will be cached in .boris/repos/") String project_path,
            @ToolParam(description = "Force re-download and re-index (default: false)") Boolean force) {

        ToolProgressNotifier.notify("create_library_context", "Indexando: " + repo);

        Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("repo", repo);
        if (version != null) args.put("version", version);
        args.put("project_path", project_path != null ? project_path : ".");
        if (force != null) args.put("force", force);

        String result = LibraryContextTool.execute(args);
        notifyToolTokens("create_library_context", repo, result);
        return result;
    }

    @Tool(
            name = "query_symbols",
            description = "Search for a specific class or method symbol on-demand. " +
                         "Use ONLY when looking for an unknown symbol or signature. " +
                         "Do NOT query exhaustively or inspect unneeded classes.")
    public String query_symbols(
            @ToolParam(description = "Name to search (class, method, etc)") String query,
            @ToolParam(description = "Filter by type: CLASS, METHOD, CONSTRUCTOR, FIELD, INTERFACE, ENUM (optional)") String type,
            @ToolParam(description = "Filter by repo name (optional)") String repo) {
        String result = com.boris.context.SymbolQueryTool.search(query, type, repo);
        notifyToolTokens("query_symbols", query, result);
        return result;
    }

    @Tool(
            name = "query_methods",
            description = "Get public methods of a specific class when needed for an exact call. Do not call recursively across multiple classes.")
    public String query_methods(
            @ToolParam(description = "Class name to get methods from") String className) {
        String result = com.boris.context.SymbolQueryTool.findMethods(className);
        notifyToolTokens("query_methods", className, result);
        return result;
    }

    @Tool(
            name = "list_classes",
            description = "List indexed classes. Do NOT call this during bug fixes, file editing, or file creation tasks. Use targeted lookups instead.")
    public String list_classes(
            @ToolParam(description = "Filter by repo name (optional)") String repo) {
        String result = com.boris.context.SymbolQueryTool.listClasses(repo);
        notifyToolTokens("list_classes", repo != null ? repo : "", result);
        return result;
    }

    @Tool(
            name = "index_status",
            description = "Show current indexing status: how many symbols are indexed and from which repos.")
    public String index_status() {
        String result = com.boris.context.SymbolQueryTool.indexStatus();
        notifyToolTokens("index_status", "", result);
        return result;
    }

    @Tool(
            name = "query_framework_knowledge",
            description = "Query persistent markdown knowledge card for a class in a framework/library (.boris/<framework>/<CLASE>.md). " +
                         "ALWAYS use this BEFORE reading full raw source files or symbols when working with unfamiliar frameworks.")
    public String query_framework_knowledge(
            @ToolParam(description = "Framework, library or repo name (e.g., tambo-tui, lanterna)") String framework,
            @ToolParam(description = "Class name to query (e.g., Color, Buffer, Frame, Widget)") String class_name,
            @ToolParam(description = "Optional project root path (default: current project)") String project_path) {
        String result = com.boris.knowledge.FrameworkKnowledgeTool.queryKnowledge(framework, class_name, project_path);
        notifyToolTokens("query_framework_knowledge", framework + "/" + class_name, result);
        return result;
    }

    @Tool(
            name = "save_framework_knowledge",
            description = "Save or update a markdown knowledge summary for a class in .boris/<framework>/<CLASE>.md so it can be reused in future tasks.")
    public String save_framework_knowledge(
            @ToolParam(description = "Framework, library or repo name (e.g., tambo-tui)") String framework,
            @ToolParam(description = "Class name (e.g., Style, BarChart)") String class_name,
            @ToolParam(description = "Concise markdown summary with purpose, key methods, and usage example") String content,
            @ToolParam(description = "Optional project root path (default: current project)") String project_path) {
        String result = com.boris.knowledge.FrameworkKnowledgeTool.saveKnowledge(framework, class_name, content, project_path);
        notifyToolTokens("save_framework_knowledge", framework + "/" + class_name, result);
        return result;
    }

    @Tool(
            name = "list_framework_knowledge",
            description = "List all classes cached in .boris/<framework>/ or list all known frameworks.")
    public String list_framework_knowledge(
            @ToolParam(description = "Optional framework name to filter classes (e.g., tambo-tui)") String framework,
            @ToolParam(description = "Optional project root path (default: current project)") String project_path) {
        String result = com.boris.knowledge.FrameworkKnowledgeTool.listKnowledge(framework, project_path);
        notifyToolTokens("list_framework_knowledge", framework != null ? framework : "", result);
        return result;
    }

}

