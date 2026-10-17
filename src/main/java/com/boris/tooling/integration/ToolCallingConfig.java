package com.boris.tooling.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;

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
import com.boris.tooling.tool.OfficeDocumentTool;
import com.boris.tooling.tool.PdfGenerationTool;
import com.boris.tooling.tool.SystemInfoTool;
import com.boris.tooling.tool.WebSearchTool;
import com.boris.tooling.tool.LibraryContextTool;
import com.boris.tooling.tool.SearchContextTool;
import com.boris.tooling.parallel.BatchReadFileTool;
import com.boris.tooling.parallel.BatchEditTool;
import com.boris.tooling.parallel.BatchWebFetchTool;
import com.boris.tooling.parallel.BatchExecuteCommandTool;
import com.boris.tooling.parallel.BatchListDirectoriesTool;
// BatchGitHubSearchTool ELIMINADO - usar create_library_context
import com.boris.tooling.parallel.ParallelSearchTool;
import com.boris.tooling.ToolCallLimiter;
import com.boris.tooling.ToolProgressNotifier;
import com.boris.tooling.ToolResultNotifier;
import com.boris.tooling.quality.QualityIteratorHolder;
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
    private final BatchExecuteCommandTool batchExecuteCommandTool;
    private final BatchListDirectoriesTool batchListDirectoriesTool;
    // batchGitHubSearchTool ELIMINADO
    private final ParallelSearchTool parallelSearchTool;
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

        // Initialize all parallel tools
        this.batchReadFileTool = new BatchReadFileTool();
        this.batchEditTool = new BatchEditTool();
        this.batchWebFetchTool = new BatchWebFetchTool();
        this.batchExecuteCommandTool = new BatchExecuteCommandTool();
        this.batchListDirectoriesTool = new BatchListDirectoriesTool();
        // batchGitHubSearchTool ELIMINADO
        this.parallelSearchTool = new ParallelSearchTool();
        this.webSearchTool = new WebSearchTool();
    }

    public static String loadSystemPrompt(Settings settings) {
        StringBuilder prompt = new StringBuilder();

        String defaultPrompt = loadDefaultSystemPrompt();
        prompt.append(defaultPrompt.trim());

        try {
            for (String path : SYSTEM_PROMPT_PATHS) {
                Path resolved = Path.of(System.getProperty("user.home"), path.substring(1));
                if (Files.exists(resolved)) {
                    String agentsContent = Files.readString(resolved).trim();
                    if (!agentsContent.isEmpty()) {
                        prompt.append("\n\n").append(agentsContent);
                    }
                }
            }
        } catch (IOException ignored) {}

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

    public static ChatClient buildChatClientWithTools(String settingsPath) throws Exception {
        LlmClient llmClient = new LlmClient(settingsPath);
        var chatModel = extractChatModel(llmClient);
        SettingsManager mgr = new SettingsManager();
        Settings s = mgr.loadSettings(settingsPath);
        String prompt = loadSystemPrompt(s);
        ToolCallingConfig config = createDefaultConfig(s);
        ChatClient client = ChatClient.builder(chatModel)
                .defaultSystem(prompt)
                .defaultTools(ToolCallbacks.from(config))
                .build();

        return client;
    }

    public static org.springframework.ai.tool.ToolCallback[] buildNativeToolCallbacks() {
        return buildNativeToolCallbacks(null);
    }

    public static org.springframework.ai.tool.ToolCallback[] buildNativeToolCallbacks(Settings settings) {
        ToolCallingConfig config = createDefaultConfig(settings);
        return ToolCallbacks.from(config);
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
    private static org.springframework.ai.chat.model.ChatModel extractChatModel(LlmClient llmClient) throws Exception {
        var field = llmClient.getClass().getDeclaredField("chatClient");
        field.setAccessible(true);
        ChatClient client = (ChatClient) field.get(llmClient);
        var method = client.getClass().getMethod("getModel");
        return (org.springframework.ai.chat.model.ChatModel) method.invoke(client);
    }

    @SuppressWarnings("unchecked")
    private static java.util.Map<String, Object> castToMap(Object o) {
        return (java.util.Map<String, Object>) o;
    }

    // ========== Core Single-Item Tools ==========

    @Tool(
            name = "bulk_edit",
            description = "Create or replace a file. Validates ALL lines against CONTEXT.md BEFORE writing. If errors found, reports ALL together without writing. Shows diff line-by-line in real-time.")
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
            name = "apply_edit",
            description = "Apply a surgical edit to an existing file by finding old_text and replacing it with new_text. Returns success status, message, and a diff showing changes. IMPORTANT: Always display the diff to the user with lines starting with '- ' for removed and '+ ' for added content.")
    public String apply_edit(@ToolParam(description = "Absolute or relative file path") String path,
                             @ToolParam(description = "Exact text to find and replace in the file") String old_text,
                             @ToolParam(description = "Replacement text") String new_text) {
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

    // ========== PARALLEL Tools (Replace Sequential) ==========

    @Tool(
            name = "list_files",
            description = "List files and directories. Accepts one or multiple paths - multiple paths are listed IN PARALLEL for faster results.")
    public String list_files(@ToolParam(description = "Single path or array of directory paths to list") Object paths) {
        List<String> pathList = new ArrayList<>();
        if (paths instanceof String s) {
            pathList.add(s);
        } else if (paths instanceof List<?> list) {
            for (Object item : list) {
                if (item instanceof String s) {
                    pathList.add(s);
                }
            }
        }
        if (pathList.isEmpty()) {
            pathList.add(".");
        }

        String result = batchListDirectoriesTool.execute(pathList);
        String pathsStr = String.join(", ", pathList);
        notifyToolTokens("list_files", pathsStr, result);
        ToolResultNotifier.notifyQueryResult("list_files", pathsStr, result);
        return result;
    }

    @Tool(
            name = "web_search",
            description = "Search the web using Google, Bing, and DuckDuckGo IN PARALLEL. Returns combined, deduplicated results from all engines.")
    public String web_search(@ToolParam(description = "Search query string") String query,
                             @ToolParam(description = "Number of results to return (1-10, default 5)") Integer count) {
        if (!ToolCallLimiter.incrementAndCheck()) {
            return "{\"success\":false,\"error\":\"" + ToolCallLimiter.getLimitMessage() + "\"}";
        }
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
            description = "Fetch and extract content from URLs. Accepts one or multiple URLs - multiple URLs are fetched IN PARALLEL for faster results. Can extract text, code blocks, or specific sections.")
    public String web_fetch(@ToolParam(description = "Single URL or array of URLs to fetch") Object urls,
                            @ToolParam(description = "Optional CSS selector to extract specific content (e.g., 'article', '.content', '#readme')") String selector,
                            @ToolParam(description = "If true, extract code blocks from the page (useful for documentation)") Boolean extract_code) {
        if (!ToolCallLimiter.incrementAndCheck()) {
            return "{\"success\":false,\"error\":\"" + ToolCallLimiter.getLimitMessage() + "\"}";
        }

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

    // execute_command y batch_execute_commands ELIMINADOS
    // Ver TODO.md para plan de reemplazo con git_tool y build_tool

    // ========== Existing Batch Tools ==========

    @Tool(
            name = "batch_read_files",
            description = "Read multiple files concurrently from disk in a single call. Much faster than reading files one by one. Use this when inspecting multiple files, dependencies, or tests.")
    public String batch_read_files(
            @ToolParam(description = "List of absolute or relative file paths to read") java.util.List<String> paths,
            @ToolParam(description = "Optional maximum lines to read per file (default: all)") Integer max_lines_per_file) {

        String result = batchReadFileTool.execute(paths, max_lines_per_file);
        String pathsStr = (paths != null && !paths.isEmpty()) ? String.join(", ", paths) : "empty";
        notifyToolTokens("batch_read_files", pathsStr, result);
        ToolResultNotifier.notifyQueryResult("batch_read_files", pathsStr, result);
        return result;
    }

    @Tool(
            name = "batch_apply_edits",
            description = "Apply surgical edits across multiple files in parallel. Much faster than applying edits one by one. Use this when modifying multiple files simultaneously.")
    @SuppressWarnings("unchecked")
    public String batch_apply_edits(
            @ToolParam(description = "List of edit specifications. Each must have: path, old_text, new_text") java.util.List<java.util.Map<String, Object>> edits) {
        ToolProgressNotifier.notify("batch_apply_edits", "Aplicando " + (edits != null ? edits.size() : 0) + " ediciones en paralelo...");

        java.util.List<?> rawEdits = (java.util.List<?>) (edits != null ? edits : new java.util.ArrayList<>());
        java.util.List<BatchEditTool.FileEditSpec> specs = new java.util.ArrayList<>();
        for (Object rawEdit : rawEdits) {
            java.util.Map<String, Object> edit = (java.util.Map<String, Object>) rawEdit;
            String path = edit != null ? (String) edit.get("path") : null;
            String oldText = edit != null ? (String) edit.get("old_text") : null;
            String newText = edit != null ? (String) edit.get("new_text") : null;
            String content = edit != null ? (String) edit.get("content") : null;
            boolean doDelete = edit != null && "true".equals(edit.get("do_delete"));
            specs.add(new BatchEditTool.FileEditSpec(path, oldText, newText, doDelete, content));
        }

        String result = batchEditTool.execute(specs);
        String inputSummary = specs.stream().map(s -> s.path()).collect(java.util.stream.Collectors.joining(", "));
        notifyToolTokens("batch_apply_edits", inputSummary, result);
        return result;
    }

    @Tool(
            name = "parallel_search",
            description = "High-speed multi-threaded text search across the entire workspace. Much faster than listing files and searching individually.")
    public String parallel_search(
            @ToolParam(description = "Search query or pattern to find") String query,
            @ToolParam(description = "File extension filter (e.g., '*.java', '*.ts'). Default: all files") String file_pattern,
            @ToolParam(description = "Maximum results to return (default: 100)") Integer max_results,
            @ToolParam(description = "Base directory to search in (default: current directory)") String base_path) {
        ToolProgressNotifier.notify("parallel_search", "Buscando: " + query);
        String result = parallelSearchTool.execute(query, file_pattern, max_results, base_path);
        notifyToolTokens("parallel_search", query, result);
        ToolResultNotifier.notifyQueryResult("parallel_search", query, result);
        return result;
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

    // ========== Library Context Tool ==========

    @Tool(
            name = "create_library_context",
            description = "Downloads GitHub repo as ZIP (once), analyzes source code locally, and creates .boris/CONTEXT.md. " +
                         "NO API rate limits. After first download, uses local cache. Use BEFORE writing library code.")
    public String create_library_context(
            @ToolParam(description = "GitHub repository in format owner/repo (e.g., mabe02/lanterna)") String repo,
            @ToolParam(description = "Branch or version (e.g., master, main, 3.1.5)") String version,
            @ToolParam(description = "Path to project where .boris/CONTEXT.md will be created") String project_path,
            @ToolParam(description = "Force re-download even if repo already exists locally") Boolean force) {
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("repo", repo);
        args.put("version", version != null ? version : "main");
        args.put("project_path", project_path != null ? project_path : ".");
        args.put("force", force != null ? force : false);
        String result = LibraryContextTool.execute(args);
        notifyToolTokens("create_library_context", repo, result);
        return result;
    }

    // ========== Search Context Tool ==========

    @Tool(
            name = "search_context",
            description = "Busca clases y métodos en .boris/CONTEXT.md. Devuelve resultados estructurados. " +
                         "USA ESTE TOOL en lugar de grep para buscar en CONTEXT.md.")
    public String search_context(
            @ToolParam(description = "Clase o método a buscar (ej: 'Panel', 'TextGraphics', 'putString')") String query,
            @ToolParam(description = "Path al proyecto donde está .boris/CONTEXT.md") String project_path) {
        String result = SearchContextTool.execute(query, project_path);
        notifyToolTokens("search_context", query, result);
        return result;
    }

    // ========== Search Replace Tool (Para ediciones parciales) ==========

    @Tool(
            name = "search_replace",
            description = "Apply multiple SEARCH/REPLACE blocks to a file. Validates ALL replacements before applying. " +
                         "Use when you only need to change specific parts without rewriting the whole file.")
    public String search_replace(
            @ToolParam(description = "Path to the file to edit") String path,
            @ToolParam(description = "List of replacements: [{search: 'text to find', replace: 'new text'}, ...]") java.util.List<java.util.Map<String, String>> replacements) {
        ToolProgressNotifier.notify("search_replace", "Aplicando " + (replacements != null ? replacements.size() : 0) + " reemplazos en: " + path);
        String result = com.boris.edit.SearchReplaceEditTool.search_replace(path, replacements);
        notifyToolTokens("search_replace", path, result);
        return result;
    }

    // ========== Streaming Edit Tools (DEPRECATED - usar write_file) ==========

    // ========== Validated Line Edit Tools (Just-in-time context) ==========

    @Tool(
            name = "validated_line_start",
            description = "STEP 1: Start editing a file. Call ONLY ONCE per file. " +
                         "After this, use validated_line_write for EACH line. " +
                         "NEVER call this again for the same file - it will RESET and lose all progress. " +
                         "Flow: start(1x) -> write(Nx) -> end(1x)")
    public String validated_line_start(
            @ToolParam(description = "Path for the file to create/edit") String path) {
        ToolProgressNotifier.notify("validated_line_start", "Iniciando edición validada: " + path);
        java.util.Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("path", path);
        String result = com.boris.edit.ValidatedLineEditTool.validated_line_start(args);
        notifyToolTokens("validated_line_start", path, result);
        return result;
    }

    @Tool(
            name = "validated_line_write",
            description = "STEP 2: Write ONE line. Call this for EACH line of code, one at a time. " +
                         "Validates against CONTEXT.md. If error: fix and retry the SAME line. " +
                         "Do NOT repeat lines already written. Do NOT restart the file. " +
                         "Continue until all lines are written, then call validated_line_end.")
    public String validated_line_write(
            @ToolParam(description = "Single line of code to write") String line) {
        java.util.Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("line", line != null ? line : "");
        String result = com.boris.edit.ValidatedLineEditTool.validated_line_write(args);
        notifyToolTokens("validated_line_write", line, result);
        return result;
    }

    @Tool(
            name = "validated_line_end",
            description = "STEP 3: Finish and save the file. Call ONLY ONCE after all lines are written. " +
                         "This closes the file. After this, the file is complete.")
    public String validated_line_end() {
        String result = com.boris.edit.ValidatedLineEditTool.validated_line_end(java.util.Map.of());
        notifyToolTokens("validated_line_end", "", result);
        return result;
    }

    // streaming_edit_* ELIMINADOS - no tenían validación contra CONTEXT.md
    // El agente debe usar bulk_edit que SÍ valida
}
