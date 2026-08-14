package com.boris.tooling.integration;

import java.util.Map;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbacks;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;

import com.boris.llm.LlmClient;
import com.boris.tooling.ToolRegistry;
import com.boris.tooling.tool.FileTool;
import com.boris.tooling.tool.SystemInfoTool;

public class ToolCallingConfig {

    public static ToolRegistry buildDefaultRegistry() {
        ToolRegistry registry = new ToolRegistry();
        registerFileTools(registry);
        registerSystemInfoTool(registry);
        return registry;
    }

    private static void registerFileTools(ToolRegistry registry) {
        registry.register(FileTool.read_file(), FileTool::read_file);
        registry.register(FileTool.write_file(), FileTool::write_file);
        registry.register(FileTool.delete_file(), FileTool::delete_file);
        registry.register(FileTool.list_files(), FileTool::list_files);
    }

    private static void registerSystemInfoTool(ToolRegistry registry) {
        registry.register(SystemInfoTool.get_system_info(), SystemInfoTool::get_system_info);
    }

    public static ToolCallback[] buildToolCallbacks() {
        return ToolCallbacks.from(new Object() {
            @org.springframework.ai.tool.annotation.Tool(
                    name = "read_file",
                    description = "Read the contents of a file at the given path. Returns the file content as a string.")
            public String read_file(@org.springframework.ai.tool.annotation.ToolParam() String path) {
                return FileTool.read_file(Map.of("path", path));
            }

            @org.springframework.ai.tool.annotation.Tool(
                    name = "write_file",
                    description = "Create a new file or overwrite an existing one with the given content.")
            public String write_file(@org.springframework.ai.tool.annotation.ToolParam() String path,
                                     @org.springframework.ai.tool.annotation.ToolParam() String content) {
                return FileTool.write_file(Map.of("path", path, "content", content));
            }

            @org.springframework.ai.tool.annotation.Tool(
                    name = "delete_file",
                    description = "Delete a file at the given path. Returns success status and message.")
            public String delete_file(@org.springframework.ai.tool.annotation.ToolParam() String path) {
                return FileTool.delete_file(Map.of("path", path));
            }

            @org.springframework.ai.tool.annotation.Tool(
                    name = "list_files",
                    description = "List files and directories in the given directory. Returns a formatted listing.")
            public String list_files(@org.springframework.ai.tool.annotation.ToolParam() String path) {
                return FileTool.list_files(Map.of("path", path));
            }

            @org.springframework.ai.tool.annotation.Tool(
                    name = "get_system_info",
                    description = "Get system information including OS name, memory, CPU cores, and hostname.")
            public String get_system_info() {
                return SystemInfoTool.get_system_info(Map.of());
            }
        });
    }

    public static ChatClient buildChatClientWithTools(String settingsPath) throws Exception {
        LlmClient llmClient = new LlmClient(settingsPath);
        var chatModel = extractChatModel(llmClient);
        return ChatClient.builder(chatModel)
                .defaultTools(buildToolCallbacks())
                .build();
    }

    public static ChatClient buildChatClientWithTools(ToolRegistry registry, org.springframework.ai.chat.model.ChatModel chatModel) {
        ToolCallback[] callbacks = toDynamicCallbacks(registry);
        return ChatClient.builder(chatModel)
                .defaultTools(callbacks)
                .build();
    }

    public static ChatClient buildChatClient(ToolRegistry registry) {
        ToolCallback[] callbacks = toDynamicCallbacks(registry);
        return ChatClient.builder(null)
                .defaultTools(callbacks)
                .build();
    }

    private static ToolCallback[] toDynamicCallbacks(ToolRegistry registry) {
        return registry.getAll().stream()
                .map(def -> new DynamicToolCallback(toSpringDefinition(def), registry))
                .toArray(ToolCallback[]::new);
    }

    private static ToolDefinition toSpringDefinition(com.boris.tooling.ToolDefinition def) {
        String schema = "{}";
        var params = def.parameters();
        if (params.containsKey("properties")) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                schema = mapper.writeValueAsString(params);
            } catch (Exception ignored) {
            }
        }
        return DefaultToolDefinition.builder()
                .name(def.name())
                .description(def.description())
                .inputSchema(schema)
                .build();
    }

    @SuppressWarnings("unchecked")
    private static org.springframework.ai.chat.model.ChatModel extractChatModel(LlmClient llmClient) throws Exception {
        var field = llmClient.getClass().getDeclaredField("chatClient");
        field.setAccessible(true);
        ChatClient client = (ChatClient) field.get(llmClient);
        var method = client.getClass().getMethod("getModel");
        return (org.springframework.ai.chat.model.ChatModel) method.invoke(client);
    }

    private static class DynamicToolCallback implements ToolCallback {
        private final ToolDefinition definition;
        private final ToolRegistry registry;

        DynamicToolCallback(ToolDefinition def, ToolRegistry reg) {
            this.definition = def;
            this.registry = reg;
        }

        @Override
        public ToolDefinition getToolDefinition() {
            return definition;
        }

        @Override
        public String call(String toolInput) {
            try {
                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                Map<String, Object> args = mapper.readValue(toolInput, java.util.Map.class);
                return registry.execute(definition.name(), args);
            } catch (Exception e) {
                return "Error: " + e.getMessage();
            }
        }
    }
}
