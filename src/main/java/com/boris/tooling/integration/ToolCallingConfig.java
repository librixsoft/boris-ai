package com.boris.tooling.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
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
import com.boris.tooling.tool.ExecuteCommandTool;
import com.boris.tooling.tool.GitHubApiSearchTool;
import com.boris.tooling.tool.GitHubRepoSearchTool;
import com.boris.tooling.tool.ListFilesTool;
import com.boris.tooling.tool.OfficeDocumentTool;
import com.boris.tooling.tool.PdfGenerationTool;
import com.boris.tooling.tool.ReadFileTool;
import com.boris.tooling.tool.SystemInfoTool;
import com.boris.tooling.tool.WebFetchTool;
import com.boris.tooling.tool.WebSearchTool;
import com.boris.tooling.tool.WriteTool;
import com.boris.tooling.ToolCallLimiter;
import com.boris.tooling.ToolProgressNotifier;
import com.boris.tooling.ToolResultNotifier;
import com.boris.tooling.quality.QualityIteratorHolder;
import com.boris.task.decomposition.TaskPlannerTool;

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

    private final ReadFileTool readFileTool;
    private final WriteTool writeTool;
    private final DeleteTool deleteTool;
    private final ListFilesTool listFilesTool;
    private final EditTool editTool;
    private final SystemInfoTool systemInfoTool;
    private final WebSearchTool webSearchTool;
    private final WebFetchTool webFetchTool;
    private final PdfGenerationTool pdfGenerationTool;
    private final OfficeDocumentTool officeDocumentTool;
    private final ExecuteCommandTool executeCommandTool;
    private final TaskPlannerTool taskPlannerTool;
    private final GitHubRepoSearchTool gitHubRepoSearchTool;
    private final GitHubApiSearchTool gitHubApiSearchTool;
    private final Settings settings;

    public ToolCallingConfig(ReadFileTool readFileTool,
                            WriteTool writeTool,
                            DeleteTool deleteTool,
                            ListFilesTool listFilesTool,
                            EditTool editTool,
                            SystemInfoTool systemInfoTool,
                            WebSearchTool webSearchTool,
                            WebFetchTool webFetchTool,
                            PdfGenerationTool pdfGenerationTool,
                            OfficeDocumentTool officeDocumentTool,
                            ExecuteCommandTool executeCommandTool,
                            TaskPlannerTool taskPlannerTool,
                            GitHubRepoSearchTool gitHubRepoSearchTool,
                            GitHubApiSearchTool gitHubApiSearchTool,
                            Settings settings) {
        this.readFileTool = readFileTool;
        this.writeTool = writeTool;
        this.deleteTool = deleteTool;
        this.listFilesTool = listFilesTool;
        this.editTool = editTool;
        this.systemInfoTool = systemInfoTool;
        this.webSearchTool = webSearchTool;
        this.webFetchTool = webFetchTool;
        this.pdfGenerationTool = pdfGenerationTool;
        this.officeDocumentTool = officeDocumentTool;
        this.executeCommandTool = executeCommandTool;
        this.taskPlannerTool = taskPlannerTool;
        this.gitHubRepoSearchTool = gitHubRepoSearchTool;
        this.gitHubApiSearchTool = gitHubApiSearchTool;
        this.settings = settings;
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
                new ReadFileTool(),
                new WriteTool(),
                new DeleteTool(),
                new ListFilesTool(),
                new EditTool(),
                new SystemInfoTool(),
                new WebSearchTool(),
                new WebFetchTool(),
                new PdfGenerationTool(),
                new OfficeDocumentTool(),
                new ExecuteCommandTool(),
                new TaskPlannerTool(),
                new GitHubRepoSearchTool(),
                new GitHubApiSearchTool(),
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

    @Tool(
            name = "read_file",
            description = "Read the contents of a file at the given path. Returns the file content as a string.")
    public String read_file(@ToolParam(description = "Absolute or relative path to the file to read") String path) {
        ToolProgressNotifier.notify("read_file", "Leyendo archivo: " + path);
        String result = readFileTool.execute(Map.of("path", path));
        notifyToolTokens("read_file", path, result);
        return result;
    }

    @Tool(
            name = "write_file",
            description = "Create a new file or overwrite an existing one with the given content.")
    public String write_file(@ToolParam(description = "Absolute or relative path for the file to create/overwrite") String path,
                             @ToolParam(description = "Full text content to write into the file") String content) {
        ToolProgressNotifier.notify("write_file", "Escribiendo archivo: " + path);
        String inputText = path + "\n" + content;
        String result = writeTool.execute(Map.of("path", path, "content", content));
        notifyToolTokens("write_file", inputText, result);
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
            description = "List files and directories in the given directory. Returns a formatted listing.")
    public String list_files(@ToolParam(description = "Directory path to list contents of") String path) {
        ToolProgressNotifier.notify("list_files", "Listando: " + path);
        String result = listFilesTool.execute(Map.of("path", path));
        notifyToolTokens("list_files", path, result);
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
        String result = EditTool.multi_edit(Map.of("path", path, "edits", edits));
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
            name = "web_search",
            description = "Search the web using Google/Bing/DuckDuckGo via Playwright. Returns titles, URLs, and snippets with no API key required. Limited to avoid infinite loops.")
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
        return result;
    }

    @Tool(
            name = "web_fetch",
            description = "Fetch and extract content from a specific URL. Ideal for reading documentation, articles, or any webpage. Can extract text, code blocks, or specific sections. Limited to avoid infinite loops.")
    public String web_fetch(@ToolParam(description = "URL to fetch content from") String url,
                            @ToolParam(description = "Optional CSS selector to extract specific content (e.g., 'article', '.content', '#readme')") String selector,
                            @ToolParam(description = "If true, extract code blocks from the page (useful for documentation)") Boolean extract_code) {
        if (!ToolCallLimiter.incrementAndCheck()) {
            return "{\"success\":false,\"error\":\"" + ToolCallLimiter.getLimitMessage() + "\"}";
        }
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("url", url);
        if (selector != null) args.put("selector", selector);
        if (extract_code != null) args.put("extract_code", extract_code);
        String result = WebFetchTool.execute(args);
        notifyToolTokens("web_fetch", url, result);
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

    @Tool(
            name = "execute_command",
            description = "Execute a shell or terminal command on the operating system (e.g. git commit, git push, mvn test, npm install) and return standard output, error, and exit code.")
    public String execute_command(
            @ToolParam(description = "The terminal command string to execute") String command,
            @ToolParam(description = "Optional working directory path where the command should run") String workingDirectory) {
        ToolProgressNotifier.notify("execute_command", "Ejecutando: " + command);
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("command", command);
        if (workingDirectory != null) {
            args.put("workingDirectory", workingDirectory);
        }
        String result = executeCommandTool.execute(args);
        notifyToolTokens("execute_command", command, result);
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

    // ========== GitHub Repository Search ==========

    @Tool(
            name = "github_repo_search",
            description = "Search code in a GitHub repository by cloning it temporarily. Supports specialized modes: 'docs' (find README, CONTRIBUTING, docs/), 'examples' (find sample code), 'config' (detect project type and find config files). Uses caching - same repo won't be re-cloned within 30 minutes.")
    public String github_repo_search(
            @ToolParam(description = "GitHub repository URL or owner/repo format (e.g., 'angular/angular' or 'https://github.com/angular/angular')") String repo,
            @ToolParam(description = "Search query: text to find in file content or filename pattern") String query,
            @ToolParam(description = "Search type: 'content' (grep), 'filename' (find by name), 'structure' (list tree), 'docs' (documentation files), 'examples' (sample code), 'config' (project config files). Default: content") String search_type,
            @ToolParam(description = "File extension filter (e.g., '*.java', '*.ts'). Default: all files") String file_pattern,
            @ToolParam(description = "Subdirectory to search in (e.g., 'src/main'). Default: entire repo") String path,
            @ToolParam(description = "Branch to clone. Default: default branch") String branch,
            @ToolParam(description = "Maximum results (1-50). Default: 10") Integer max_results,
            @ToolParam(description = "Include file content in results. Default: true for content search") Boolean show_content) {
        if (!ToolCallLimiter.incrementAndCheck()) {
            return "{\"success\":false,\"error\":\"" + ToolCallLimiter.getLimitMessage() + "\"}";
        }
        ToolProgressNotifier.notify("github_repo_search", "Buscando en repositorio: " + repo);
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        args.put("repo", repo);
        if (query != null) args.put("query", query);
        if (search_type != null) args.put("search_type", search_type);
        if (file_pattern != null) args.put("file_pattern", file_pattern);
        if (path != null) args.put("path", path);
        if (branch != null) args.put("branch", branch);
        if (max_results != null) args.put("max_results", max_results);
        if (show_content != null) args.put("show_content", show_content);
        String result = GitHubRepoSearchTool.execute(args);
        notifyToolTokens("github_repo_search", repo + " " + (query != null ? query : ""), result);
        return result;
    }

    // ========== GitHub API Search (No Clone Required) ==========

    @Tool(
            name = "github_api_search",
            description = "Search GitHub via API without cloning. Use this FIRST for documentation lookup, finding repos, searching code patterns, or getting README content. Faster than github_repo_search for initial exploration.")
    public String github_api_search(
            @ToolParam(description = "Search type: 'repos' (find repositories), 'code' (search code), 'readme' (get README), 'issues' (search issues/PRs), 'files' (list repo files), 'file_content' (get file)") String search_type,
            @ToolParam(description = "Search query or repo (owner/repo for readme/files/file_content, keywords for repos/code/issues)") String query,
            @ToolParam(description = "For 'file_content': path to file. For 'files': subdirectory to list") String path,
            @ToolParam(description = "Filter by programming language (e.g., 'java', 'typescript')") String language,
            @ToolParam(description = "Maximum results (1-30). Default: 10") Integer max_results) {
        if (!ToolCallLimiter.incrementAndCheck()) {
            return "{\"success\":false,\"error\":\"" + ToolCallLimiter.getLimitMessage() + "\"}";
        }
        ToolProgressNotifier.notify("github_api_search", "Consultando GitHub API...");
        Map<String, Object> args = new java.util.LinkedHashMap<>();
        if (search_type != null) args.put("search_type", search_type);
        args.put("query", query);
        if (path != null) args.put("path", path);
        if (language != null) args.put("language", language);
        if (max_results != null) args.put("max_results", max_results);
        String result = GitHubApiSearchTool.execute(args);
        notifyToolTokens("github_api_search", (search_type != null ? search_type + " " : "") + query, result);
        return result;
    }
}
