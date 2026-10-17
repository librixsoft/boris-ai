package com.boris.tooling.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.tooling.ToolDefinition;
import com.boris.context.ProjectContext;
public class WriteTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ToolDefinition write_file() {
        var pathProp = Map.of("type", "string", "description", "Absolute or relative file path");
        var contentProp = Map.of("type", "string", "description", "Content to write to the file");
        var properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProp);
        properties.put("content", contentProp);
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return ToolDefinition.of(
                "write_file",
                "Create a new file or overwrite an existing one with the given content. Returns success status and message.",
                schema);
    }

    public static String execute(Map<String, Object> args) {
        @SuppressWarnings("unchecked")
        String pathStr = (String) args.getOrDefault("path", "");
        @SuppressWarnings("unchecked")
        String content = (String) args.getOrDefault("content", "");

        if (pathStr == null || pathStr.isBlank()) {
            return formatOutput(false, "Error: path is required");
        }

        // Resolver ruta relativa usando el proyecto actual
        pathStr = ProjectContext.resolveRelativePath(pathStr);

        if (content == null) {
            content = "";
        }

        Path path = Paths.get(pathStr);

        // Sugerir usar streaming_edit para archivos de código
        if (isCodeFile(pathStr)) {
            long lineCount = content.lines().count();
            if (lineCount > 15) {
                return formatOutput(false,
                    "Para archivos de código grandes, usa streaming_edit_start/line/finish.\n" +
                    "write_file es solo para archivos de configuración (json, xml, properties).");
            }
        }

        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content);
            long size = Files.size(path);
            return formatOutput(true, "File written successfully: " + pathStr + " (" + size + " bytes)");
        } catch (IOException e) {
            throw new com.boris.exceptions.BorisException("Error writing file: " + pathStr, e);
        }
    }

    private static boolean isCodeFile(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".java") ||
               lower.endsWith(".kt") ||
               lower.endsWith(".scala") ||
               lower.endsWith(".groovy") ||
               lower.endsWith(".py") ||
               lower.endsWith(".js") ||
               lower.endsWith(".ts") ||
               lower.endsWith(".tsx") ||
               lower.endsWith(".jsx") ||
               lower.endsWith(".go") ||
               lower.endsWith(".rs") ||
               lower.endsWith(".c") ||
               lower.endsWith(".cpp") ||
               lower.endsWith(".h") ||
               lower.endsWith(".cs") ||
               lower.endsWith(".rb") ||
               lower.endsWith(".php") ||
               lower.endsWith(".swift") ||
               lower.endsWith(".m");
    }

    private static String formatOutput(boolean success, String message) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", success);
            node.put("message", message);
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }
}
