package com.boris.tooling.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import com.boris.exceptions.BorisException;
import com.boris.llm.LlmClient;
import com.boris.settings.Settings;
import com.boris.settings.SettingsManager;
import com.boris.tooling.tool.DeleteTool;
import com.boris.tooling.tool.EditTool;
import com.boris.tooling.tool.ListFilesTool;
import com.boris.tooling.tool.ReadFileTool;
import com.boris.tooling.tool.SystemInfoTool;
import com.boris.tooling.tool.WebSearchTool;
import com.boris.tooling.tool.WriteTool;

public class ToolCallingConfig {

    private static final String[] SYSTEM_PROMPT_PATHS = {
        "~/.boris/AGENTS.md"
    };

    private final ReadFileTool readFileTool;
    private final WriteTool writeTool;
    private final DeleteTool deleteTool;
    private final ListFilesTool listFilesTool;
    private final EditTool editTool;
    private final SystemInfoTool systemInfoTool;
    private final WebSearchTool webSearchTool;

    public ToolCallingConfig() {
        this.readFileTool = new ReadFileTool();
        this.writeTool = new WriteTool();
        this.deleteTool = new DeleteTool();
        this.listFilesTool = new ListFilesTool();
        this.editTool = new EditTool();
        this.systemInfoTool = new SystemInfoTool();
        this.webSearchTool = new WebSearchTool();
    }

    public static String loadSystemPrompt(Settings settings) {
        StringBuilder prompt = new StringBuilder();
        prompt.append(DEFAULT_SYSTEM_PROMPT.trim());

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

    private static final String DEFAULT_SYSTEM_PROMPT = """
            Default system prompt — no AGENTS.md found at any configured path.

            AVAILABLE TOOLS:
            - read_file(path): Read file contents from disk.
            - write_file(path, content): Create or overwrite a file.
            - delete_file(path): Delete a file.
            - list_files(path): List directory contents.
            - apply_edit(path, old_text, new_text): Apply a surgical edit to an existing file.
            - multi_edit(path, edits): Apply multiple sequential edits to a file.
            - revert_edit(path, old_text, new_text): Revert a previous edit by restoring original content.
            - get_system_info(): Get OS, memory, CPU info.
            - web_search(query): Search via SearXNG (aggregates Google, Bing, DuckDuckGo, Wikipedia, etc.) for current information. Returns structured JSON results with title, URL, engine (source), and snippet. The `content` field contains the extracted text from the first search result page. Read the `content` field to answer the user's question directly — do not need to fetch the URL yourself.

            INSTRUCTIONS:
            1. Analyze the user's request carefully.
            2. Use tools directly — do not explain what you would do.
            3. When writing files, include full content with all code and imports.
            4. Always read existing files before editing them.
            """;

    public static ChatClient buildChatClientWithTools(String settingsPath) throws Exception {
        LlmClient llmClient = new LlmClient(settingsPath);
        var chatModel = extractChatModel(llmClient);
        SettingsManager mgr = new SettingsManager();
        Settings s = mgr.loadSettings(settingsPath);
        String prompt = loadSystemPrompt(s);
        ToolCallingConfig config = new ToolCallingConfig();
        return ChatClient.builder(chatModel)
                .defaultSystem(prompt)
                .defaultTools(ToolCallbacks.from(config))
                .build();
    }

    public static org.springframework.ai.tool.ToolCallback[] buildNativeToolCallbacks() {
        ToolCallingConfig config = new ToolCallingConfig();
        return ToolCallbacks.from(config);
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
        return readFileTool.execute(Map.of("path", path));
    }

    @Tool(
            name = "write_file",
            description = "Create a new file or overwrite an existing one with the given content.")
    public String write_file(@ToolParam(description = "Absolute or relative path for the file to create/overwrite") String path,
                             @ToolParam(description = "Full text content to write into the file") String content) {
        return writeTool.execute(Map.of("path", path, "content", content));
    }

    @Tool(
            name = "delete_file",
            description = "Delete a file at the given path. Returns success status and message.")
    public String delete_file(@ToolParam(description = "Absolute or relative path to the file to delete") String path) {
        return deleteTool.execute(Map.of("path", path));
    }

    @Tool(
            name = "list_files",
            description = "List files and directories in the given directory. Returns a formatted listing.")
    public String list_files(@ToolParam(description = "Directory path to list contents of") String path) {
        return listFilesTool.execute(Map.of("path", path));
    }

    @Tool(
            name = "apply_edit",
            description = "Apply a surgical edit to an existing file by finding old_text and replacing it with new_text. Returns success status and message.")
    public String apply_edit(@ToolParam(description = "Absolute or relative file path") String path,
                             @ToolParam(description = "Exact text to find and replace in the file") String old_text,
                             @ToolParam(description = "Replacement text") String new_text) {
        return editTool.apply_edit(Map.of("path", path, "old_text", old_text, "new_text", new_text));
    }

    @Tool(
            name = "multi_edit",
            description = "Apply multiple sequential edits to a file. Each edit replaces old_text with new_text. Returns success status and message.")
    public String multi_edit(@ToolParam(description = "Absolute or relative file path") String path,
                             @ToolParam(description = "Array of edit objects, each with old_text and new_text fields") java.util.List<java.util.Map<String, Object>> edits) {
        return editTool.multi_edit(Map.of("path", path, "edits", edits));
    }

    @Tool(
            name = "revert_edit",
            description = "Revert a previous edit by finding the edited text and replacing it with its original value. Returns success status and message.")
    public String revert_edit(@ToolParam(description = "Absolute or relative file path") String path,
                              @ToolParam(description = "Exact text to find and replace with new_text (the original content)") String old_text,
                              @ToolParam(description = "Replacement text (original value to restore)") String new_text) {
        return editTool.revert_edit(Map.of("path", path, "old_text", old_text, "new_text", new_text));
    }

    @Tool(
            name = "get_system_info",
            description = "Get system information including OS name, memory, CPU cores, and hostname.")
    public String get_system_info() {
        return systemInfoTool.get_system_info(Map.of());
    }

    @Tool(
            name = "web_search",
            description = "Search via SearXNG (aggregates Google, Bing, DuckDuckGo, Wikipedia, etc.) for current information. Returns structured JSON results with title, URL, engine, and snippet.")
    public String web_search(@ToolParam(description = "Search query string") String query) {
        return WebSearchTool.execute(Map.ofEntries(Map.entry("query", query)));
    }
}