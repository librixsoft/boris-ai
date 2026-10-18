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
import com.boris.tooling.parallel.BatchExecuteCommandTool;
// BatchGitHubSearchTool ELIMINADO - usar create_library_context
import com.boris.tooling.parallel.ParallelSearchTool;
import com.boris.tooling.ToolProgressNotifier;
import com.boris.context.ProjectContext;
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

        // Initialize tools
        this.batchReadFileTool = new BatchReadFileTool();
        this.batchEditTool = new BatchEditTool();
        this.batchWebFetchTool = new BatchWebFetchTool();
        this.batchExecuteCommandTool = new BatchExecuteCommandTool();
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
        ToolProgressNotifier.notify("web_search", "Buscando: " + query);
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

    // execute_command y batch_execute_commands ELIMINADOS
    // Ver TODO.md para plan de reemplazo con git_tool y build_tool

    // ========== Git Tool ==========

    @Tool(
            name = "git",
            description = "Execute git commands for version control. Supports: status, diff, log, add, commit, push, pull, branch, checkout, reset, stash, fetch, merge, rebase. Use for all git operations.")
    public String git(
            @ToolParam(description = "Git command: status, diff, log, add, commit, push, pull, branch, checkout, reset, stash, fetch, merge, rebase") String command,
            @ToolParam(description = "Additional arguments (file paths, branch names, commit messages, etc.)") String args,
            @ToolParam(description = "Working directory (default: current)") String working_dir) {

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
            description = "Read files from disk. Use this when inspecting files, dependencies, or tests.")
    public String batch_read_files(
            @ToolParam(description = "List of absolute or relative file paths to read") java.util.List<String> paths,
            @ToolParam(description = "Optional maximum lines to read per file (default: all)") Integer max_lines_per_file) {
        String result = batchReadFileTool.execute(paths, max_lines_per_file);
        String pathsStr = (paths != null && !paths.isEmpty()) ? String.join(", ", paths) : "empty";
        notifyToolTokens("batch_read_files", pathsStr, result);
        ToolResultNotifier.notifyQueryResult("batch_read_files", pathsStr, result);
        return result;
    }

    // batch_apply_edits ELIMINADO - usar bulk_edit para crear archivos, apply_edit para ediciones
    /*
    @Tool(
            name = "batch_apply_edits",
            description = "Apply surgical edits across multiple files in parallel.")
    @SuppressWarnings("unchecked")
    public String batch_apply_edits(
            @ToolParam(description = "List of edit specifications") java.util.List<java.util.Map<String, Object>> edits) {
        // DISABLED
        return "{\"error\": \"batch_apply_edits disabled. Use bulk_edit or apply_edit.\"}";
    }
    */

    @Tool(
            name = "parallel_search",
            description = "Text search in files. NOT for classes/methods - use query_symbols instead.")
    public String parallel_search(
            @ToolParam(description = "Search query or pattern to find") String query,
            @ToolParam(description = "File extension filter (e.g., '*.java', '*.ts'). Default: all files") String file_pattern,
            @ToolParam(description = "Maximum results to return (default: 100)") Integer max_results,
            @ToolParam(description = "Base directory to search in (default: current directory)") String base_path) {

        // Bloquear búsquedas de clases, métodos y comandos
        if (query != null && isClassSearch(query)) {
            String q = query.trim().toLowerCase();
            if (q.equals("mvn") || q.equals("maven") || q.startsWith("mvn ")) {
                return "{\"error\": \"BLOQUEADO: Para compilar usa maven_build(\\\"compile\\\") en vez de parallel_search\"}";
            }
            String term = extractClassName(query);
            // Si parece método (camelCase), sugerir query_methods
            if (query.trim().matches("^[a-z][a-zA-Z0-9]*$")) {
                return "{\"error\": \"BLOQUEADO: Para buscar métodos usa query_methods(\\\"NombreClase\\\") en vez de parallel_search\"}";
            }
            return "{\"error\": \"BLOQUEADO: Para buscar clases/métodos usa query_symbols(\\\"" + term + "\\\") en vez de parallel_search\"}";
        }

        ToolProgressNotifier.notify("parallel_search", "Buscando: " + query);
        String result = parallelSearchTool.execute(query, file_pattern, max_results, base_path);
        notifyToolTokens("parallel_search", query, result);
        ToolResultNotifier.notifyQueryResult("parallel_search", query, result);
        return result;
    }

    private boolean isClassSearch(String query) {
        String q = query.trim().toLowerCase();

        // Bloquear búsquedas de comandos - usar maven_build
        if (q.equals("mvn") || q.equals("maven") || q.startsWith("mvn ")) {
            return true;
        }

        // Detectar patrones de búsqueda de clases
        if (q.startsWith("class ") || q.startsWith("interface ") ||
            q.startsWith("enum ") || q.startsWith("abstract class ")) {
            return true;
        }
        // Detectar nombre de clase solo (PascalCase)
        if (query.trim().matches("^[A-Z][a-zA-Z0-9]*$")) {
            return true;
        }
        // Detectar nombre de método (camelCase) - probablemente busca API
        if (query.trim().matches("^[a-z][a-zA-Z0-9]*$") && query.trim().length() > 3) {
            return true;
        }
        // Detectar "class X implements" o similar
        if (q.matches("(?i).*\\b(class|interface|enum|extends|implements)\\s+[A-Z].*")) {
            return true;
        }
        return false;
    }

    private String extractClassName(String query) {
        String q = query.trim();
        // Extraer nombre de clase del query
        if (q.matches("^[A-Z][a-zA-Z0-9]*$")) {
            return q;
        }
        java.util.regex.Matcher m = java.util.regex.Pattern
            .compile("(?:class|interface|enum)\\s+([A-Z][a-zA-Z0-9]*)")
            .matcher(q);
        if (m.find()) {
            return m.group(1);
        }
        // Buscar cualquier palabra PascalCase
        m = java.util.regex.Pattern.compile("([A-Z][a-zA-Z0-9]*)").matcher(q);
        if (m.find()) {
            return m.group(1);
        }
        return q;
    }

    // ========== Maven Build Tool ==========

    @Tool(
            name = "maven_build",
            description = "Execute Maven commands (compile, test, package, clean). Use to compile and check for errors.")
    public String maven_build(
            @ToolParam(description = "Maven goal: compile, test, package, clean, verify, install") String goal,
            @ToolParam(description = "Project directory (optional, default: current)") String project_path) {

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

        String dir = project_path != null && !project_path.isBlank()
            ? project_path
            : System.getProperty("user.dir");

        ToolProgressNotifier.notify("maven_build", "mvn " + goal + " en " + dir);

        try {
            ProcessBuilder pb = new ProcessBuilder("mvn", goal, "-q");
            pb.directory(new java.io.File(dir));
            pb.redirectErrorStream(true);

            Process process = pb.start();
            String output = new String(process.getInputStream().readAllBytes());
            int exitCode = process.waitFor();

            if (exitCode == 0) {
                return "{\"success\": true, \"message\": \"mvn " + goal + " completado OK\"}";
            } else {
                // Extraer errores de compilación
                String errors = extractCompileErrors(output);
                return "{\"success\": false, \"exit_code\": " + exitCode + ", \"errors\": \"" +
                    errors.replace("\"", "\\\"").replace("\n", "\\n") + "\"}";
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
            description = "Downloads GitHub repo and indexes it into SQLite for symbol queries. After indexing, use query_symbols() to search classes/methods. Use force=true to re-download.")
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
            description = "Search for classes, methods, constructors or fields in indexed code. " +
                         "ALWAYS use this BEFORE writing code to verify what actually exists. " +
                         "Returns real signatures from the source code.")
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
            description = "Get all public methods of a class. Use when you need to see what methods are available.")
    public String query_methods(
            @ToolParam(description = "Class name to get methods from") String className) {
        String result = com.boris.context.SymbolQueryTool.findMethods(className);
        notifyToolTokens("query_methods", className, result);
        return result;
    }

    @Tool(
            name = "list_classes",
            description = "List all indexed classes and interfaces. Use to see what's available in a library.")
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
            name = "index_project",
            description = "Index a local project to enable symbol queries. Use for projects not auto-indexed at startup.")
    public String index_project(
            @ToolParam(description = "Path to project root (default: current directory)") String path) {
        String result = com.boris.context.SymbolQueryTool.indexProject(path);
        notifyToolTokens("index_project", path != null ? path : ".", result);
        return result;
    }

}
