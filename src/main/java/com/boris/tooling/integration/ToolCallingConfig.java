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
import com.boris.tooling.tool.CreateDirectoryTool;
import com.boris.tooling.tool.DeleteDirectoryTool;
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
import com.boris.tooling.parallel.BatchWebFetchTool;

// BatchGitHubSearchTool ELIMINADO - usar github_repo
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
            description = "Create or replace a file with streaming visual feedback. Validates classes against indexed symbols BEFORE writing. Shows each line as it writes (green diff). Use github_repo(action='symbols') first to verify classes exist.")
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
            description = "Create a NEW file or fully overwrite the content of an existing one. Each line is shown in the chat while it writes and the file is saved at the end. Do NOT use it for partial changes to an existing file; use apply_edit for those.")
    public String write_file(
            @ToolParam(description = "Absolute or relative file path") String path,
            @ToolParam(description = "Content to write to the file") String content) {
        String writeGateError = com.boris.tooling.EditGateService.checkEditAllowed(path, content != null ? content : "");
        if (writeGateError != null) {
            return "{\"success\":false,\"error\":\"" + writeGateError + "\"}";
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
            name = "create_directory",
            description = "Create a directory, including missing parent directories. Returns success status and message.")
    public String create_directory(@ToolParam(description = "Absolute or relative directory path to create") String path) {
        ToolProgressNotifier.notify("create_directory", "Creando directorio: " + path);
        String result = CreateDirectoryTool.execute(Map.of("path", path != null ? path : ""));
        notifyToolTokens("create_directory", path != null ? path : "", result);
        return result;
    }

    @Tool(
            name = "delete_directory",
            description = "Delete a directory. Fails if the directory is not empty unless recursive=true. Use recursive with caution.")
    public String delete_directory(
            @ToolParam(description = "Absolute or relative directory path to delete") String path,
            @ToolParam(description = "If true, delete the directory with all its contents (default: false). Use with caution.", required = false) Boolean recursive) {
        boolean deleteRecursively = recursive != null && recursive;
        ToolProgressNotifier.notify("delete_directory", (deleteRecursively ? "Eliminando recursivamente: " : "Eliminando: ") + path);
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("path", path != null ? path : "");
        args.put("recursive", deleteRecursively);
        String result = DeleteDirectoryTool.execute(args);
        notifyToolTokens("delete_directory", path != null ? path : "", result);
        return result;
    }

    @Tool(
            name = "list_files",
            description = "List files and directories in a given path. Lists recursively by default (depth 5) and returns a compact tree. To locate files by name in one call, pass a glob in 'filter' (e.g., filter: \"*Dashboard*\").")
    public String list_files(
            @ToolParam(description = "Directory path to list (absolute or relative)") String path,
            @ToolParam(description = "Preferred glob filter matched against the whole relative path (e.g., *Dashboard*, pom.xml, src/**/*Test.java). Avoids exploring directories level by level.", required = false) String filter,
            @ToolParam(description = "If true, list files recursively (default: true). Use false only for a flat single-directory listing.", required = false) Boolean recursive,
            @ToolParam(description = "Maximum depth for recursive listing (default: 5)", required = false) Integer max_depth,
            @ToolParam(description = "Optional glob pattern matched against file names only (e.g., *.java, *.ts)", required = false) String pattern) {
        ToolProgressNotifier.notify("list_files", "Listando: " + path);
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("path", path != null ? path : ".");
        if (filter != null) args.put("filter", filter);
        if (recursive != null) args.put("recursive", recursive);
        if (max_depth != null) args.put("max_depth", max_depth);
        if (pattern != null) args.put("pattern", pattern);
        String result = ListFilesTool.execute(args);
        notifyListing("list_files", result);
        notifyToolTokens("list_files", path != null ? path : ".", result);
        return result;
    }

    private static void notifyListing(String toolName, String result) {
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(result);
            if (node.has("success") && node.get("success").asBoolean() && node.has("entries")) {
                var entriesNode = node.get("entries");
                int maxLines = 60;
                StringBuilder visible = new StringBuilder();
                int total = 0;
                for (var entry : entriesNode) {
                    total++;
                    if (total <= maxLines) {
                        String name = entry.get("name").asText();
                        String type = entry.get("type").asText();
                        visible.append(type.equals("directory") ? name + "/" : name).append("\n");
                    }
                }
                if (visible.length() > 0) {
                    if (total > maxLines) {
                        visible.append("+ ").append(total - maxLines).append(" entradas restantes");
                    }
                    ToolProgressNotifier.notify(toolName, visible.toString());
                }
            }
        } catch (Exception ignored) {}
    }

    @Tool(
            name = "apply_edit",
            description = "Surgical edit of an EXISTING file: replaces the FIRST exact occurrence of old_text with new_text. old_text MUST be copied VERBATIM from real file content read with batch_read_files (byte-for-byte: same indentation, spacing and newlines). Text written from memory, paraphrased, or too short/ambiguous will fail with 'old_text not found'. For multiple distinct changes make several apply_edit calls. The UI renders the diff automatically; do not repeat the diff in your response.")
    public String apply_edit(@ToolParam(description = "Absolute or relative file path") String path,
                             @ToolParam(description = "Exact text copied VERBATIM from the file content, including a few surrounding lines to make it unique. Same indentation, spaces and newlines. Never paraphrased and never written from memory.") String old_text,
                             @ToolParam(description = "Replacement text, keeping the same indentation level as the replaced block") String new_text) {
        String applyGateError = com.boris.tooling.EditGateService.checkEditAllowed(
            path, (old_text != null ? old_text : "") + "\n" + (new_text != null ? new_text : ""));
        if (applyGateError != null) {
            return "{\"success\":false,\"error\":\"" + applyGateError + "\"}";
        }

        // Clear compile error flag and reset learning limits when agent starts editing
        com.boris.tooling.LearningLimiter.clearCompileErrors();
        com.boris.tooling.LearningLimiter.reset();

        ToolProgressNotifier.notify("apply_edit", "Editando archivo: " + path);
        String inputText = path + "\n" + old_text + "\n" + new_text;
        String result = EditTool.apply_edit(Map.of("path", path, "old_text", old_text, "new_text", new_text));
        notifyToolTokens("apply_edit", inputText, result);
        return result;
    }

    @Tool(
            name = "revert_edit",
            description = "Revert a previous apply_edit that went wrong: old_text is the CURRENT (wrong) text now in the file, new_text is the ORIGINAL text to restore. In this tool old and new are the inverse of apply_edit.")
    public String revert_edit(@ToolParam(description = "Absolute or relative file path") String path,
                              @ToolParam(description = "Exact text currently in the file that you want to remove (the result of your previous edit)") String old_text,
                              @ToolParam(description = "Original text to restore in its place") String new_text) {
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
            description = "Search the web using DuckDuckGo and return titles, URLs, and snippets. Use ONLY when the user asks for internet information or when needed data is not available in the project (documentation, recent versions, rare errors). At most one call per question.")
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
            description = "Execute a real git command for version control. Read-only commands (status, diff, log, branch) are safe to use freely to verify changes after edits. NEVER commit, push, pull --rebase, reset or modify history unless the user explicitly asks for it.")
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
            name = "batch_read_files",
            description = "Read ONE or MULTIPLE files from disk in a single call; this is the ONLY file-reading tool. ALWAYS read a file with this tool before editing it (apply_edit requires old_text copied from the real content). Group all needed files into one call; use max_lines_per_file to limit huge files.")
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
            description = "Compile the project with Maven and get exact compiler errors ([ERROR] lines with file, symbol and line number). Use goal=compile after each batch of code changes to validate, fix reported errors with apply_edit, and re-run. Allowed goals: compile, test, package, clean, verify, install, 'clean compile', 'clean test', 'clean package', 'test-compile'. It cannot run the application.")
    public String maven_build(
            @ToolParam(description = "Maven goal: compile, test, package, clean, verify, install, 'clean compile', 'clean test', 'clean package', 'test-compile' (default: compile)", required = false) String goal,
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
            name = "github_repo",
            description = "UNIFIED GitHub repo tool with actions. action='download' (default): download owner/repo and index it; " +
                         "the result includes the 'files' manifest so you can pick exactly which files you need. " +
                         "action='read_files': read selected files from the manifest (pass paths). With target_dir it ALSO copies them " +
                         "exactly from the repo cache into the project (recommended for large files and binary assets like images/fonts). " +
                         "action='search': search or read indexed content of any kind (query or path required). " +
                         "action='symbols': search Java class/method symbols by name. " +
                         "action='methods': public methods of one class (pass class_name). " +
                         "action='classes': list indexed classes. " +
                         "action='status': indexing status summary. " +
                         "action='knowledge': read a markdown knowledge card for a framework/class (framework + class_name). " +
                         "action='knowledge_save': save/update a knowledge card (framework + class_name + content). " +
                         "action='knowledge_list': list cached classes of a framework. " +
                         "Use once per repo when the project depends on something you do not know.")
    public String github_repo(
            @ToolParam(description = "Operation mode: download (default), read_files, search, symbols, methods, classes, status, knowledge, knowledge_save, knowledge_list", required = false) String action,
            @ToolParam(description = "download: repo in 'owner/repo' format. Other actions: registered repo name as filter (required for read_files/knowledge/knowledge_save, optional filter elsewhere)", required = false) String repo,
            @ToolParam(description = "download only: version, tag or branch (default: main)", required = false) String version,
            @ToolParam(description = "download only: project path where repo is cached in .boris/repos/ (default: current project) (optional)", required = false) String project_path,
            @ToolParam(description = "download only: force re-download and re-index (default: false)", required = false) Boolean force,
            @ToolParam(description = "read_files only: file paths chosen from the github_repo 'files' manifest", required = false) java.util.List<String> paths,
            @ToolParam(description = "read_files only: base directory in the project where files are copied exactly from cache (optional)", required = false) String target_dir,
            @ToolParam(description = "read_files only: optional target names, parallel to paths (omit to keep relative paths)", required = false) java.util.List<String> target_paths,
            @ToolParam(description = "search/symbols only: search term (optional for search if path is given)", required = false) String query,
            @ToolParam(description = "search only: relative path inside the repo to read one full file (optional)", required = false) String path,
            @ToolParam(description = "symbols only: filter CLASS, METHOD, CONSTRUCTOR, FIELD, INTERFACE or ENUM (optional)", required = false) String type,
            @ToolParam(description = "methods/knowledge only: class name", required = false) String class_name,
            @ToolParam(description = "knowledge/knowledge_save/knowledge_list only: framework, library or repo name", required = false) String framework,
            @ToolParam(description = "knowledge_save only: concise markdown summary with purpose, key methods and usage example", required = false) String content) {

        Map<String, Object> args = new java.util.LinkedHashMap<>();
        if (action != null) args.put("action", action);
        if (repo != null) args.put("repo", repo);
        if (version != null) args.put("version", version);
        if (project_path != null) args.put("project_path", project_path);
        if (force != null) args.put("force", force);
        if (paths != null) args.put("paths", paths);
        if (target_dir != null) args.put("target_dir", target_dir);
        if (target_paths != null) args.put("target_paths", target_paths);
        if (query != null) args.put("query", query);
        if (path != null) args.put("path", path);
        if (type != null) args.put("type", type);
        if (class_name != null) args.put("class_name", class_name);
        if (framework != null) args.put("framework", framework);
        if (content != null) args.put("content", content);

        String actionLabel = (action != null ? action : "download");
        String result = LibraryContextTool.execute(args);
        notifyToolTokens("github_repo", actionLabel + " " + (repo != null ? repo : "") + " " + (query != null ? query : ""), result);
        return result;
    }
}


