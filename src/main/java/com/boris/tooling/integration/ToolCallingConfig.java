package com.boris.tooling.integration;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import com.boris.llm.LlmClient;
import com.boris.settings.Settings;
import com.boris.settings.SettingsManager;
import com.boris.tooling.tool.FileTool;
import com.boris.tooling.tool.SystemInfoTool;

public class ToolCallingConfig {

    private static final String[] SYSTEM_PROMPT_PATHS = {
        "~/.boris/AGENTS.md"
    };


    public static String loadSystemPrompt(Settings settings) {
        if (settings != null && settings.getSystemPrompt() != null && !settings.getSystemPrompt().isBlank()) {
            return settings.getSystemPrompt();
        }
        try {
            for (String path : SYSTEM_PROMPT_PATHS) {
                Path resolved = Path.of(System.getProperty("user.home"), path.substring(1));
                if (Files.exists(resolved)) {
                    return Files.readString(resolved).trim();
                }
            }
        } catch (IOException e) {
            throw new com.boris.exceptions.BorisException("Failed to read system prompt from any configured path", e);
        }
        throw new com.boris.exceptions.BorisException("No system prompt found in any configured path: " + java.util.List.of(SYSTEM_PROMPT_PATHS));
    }

    private static final String DEFAULT_SYSTEM_PROMPT = """
            Default system prompt — no AGENTS.md found at any configured path.

            AVAILABLE TOOLS:
            - read_file(path): Read file contents from disk.
            - write_file(path, content): Create or overwrite a file.
            - delete_file(path): Delete a file.
            - list_files(path): List directory contents.
            - get_system_info(): Get OS, memory, CPU info.

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
        return ChatClient.builder(chatModel)
                .defaultSystem(prompt)
                .defaultTools(new BorisToolCallbackProvider().getToolCallbacks())
                .build();
    }

    public static org.springframework.ai.tool.ToolCallback[] buildNativeToolCallbacks() {
        return new BorisToolCallbackProvider().getToolCallbacks();
    }

    @SuppressWarnings("unchecked")
    private static org.springframework.ai.chat.model.ChatModel extractChatModel(LlmClient llmClient) throws Exception {
        var field = llmClient.getClass().getDeclaredField("chatClient");
        field.setAccessible(true);
        ChatClient client = (ChatClient) field.get(llmClient);
        var method = client.getClass().getMethod("getModel");
        return (org.springframework.ai.chat.model.ChatModel) method.invoke(client);
    }

    private static class BorisToolCallbackProvider {
        public org.springframework.ai.tool.ToolCallback[] getToolCallbacks() {
            return ToolCallbacks.from(new FileAndSystemTools());
        }
    }

    private static class FileAndSystemTools {

        @Tool(
                name = "read_file",
                description = "Read the contents of a file at the given path. Returns the file content as a string.")
        public String read_file(@ToolParam(description = "Absolute or relative path to the file to read") String path) {
            return FileTool.read_file(java.util.Map.of("path", path));
        }

        @Tool(
                name = "write_file",
                description = "Create a new file or overwrite an existing one with the given content.")
        public String write_file(@ToolParam(description = "Absolute or relative path for the file to create/overwrite") String path,
                                 @ToolParam(description = "Full text content to write into the file") String content) {
            return FileTool.write_file(java.util.Map.of("path", path, "content", content));
        }

        @Tool(
                name = "delete_file",
                description = "Delete a file at the given path. Returns success status and message.")
        public String delete_file(@ToolParam(description = "Absolute or relative path to the file to delete") String path) {
            return FileTool.delete_file(java.util.Map.of("path", path));
        }

        @Tool(
                name = "list_files",
                description = "List files and directories in the given directory. Returns a formatted listing.")
        public String list_files(@ToolParam(description = "Directory path to list contents of") String path) {
            return FileTool.list_files(java.util.Map.of("path", path));
        }

        @Tool(
                name = "get_system_info",
                description = "Get system information including OS name, memory, CPU cores, and hostname.")
        public String get_system_info() {
            return SystemInfoTool.get_system_info(java.util.Map.of());
        }
    }
}
