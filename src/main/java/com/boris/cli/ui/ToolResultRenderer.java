package com.boris.cli.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public final class ToolResultRenderer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String[] TOOL_RESULT_KEYS = {"success", "path", "message", "deleted", "tool"};

    private static final Map<String, String> TOOL_EMOJIS = Map.of(
            "write_file", "📝",
            "delete_file", "🗑️",
            "read_file", "📄",
            "apply_edit", "✏️",
            "bash", "🐧",
            "web_search", "𔍀",
            "web_fetch", "🌐",
            "git", "📦",
            "batch_read_files", "📂"
    );

    private ToolResultRenderer() {
    }

    public static boolean isToolResultLine(String line) {
        if (line == null || line.isEmpty()) {
            return false;
        }
        String trimmed = line.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            return false;
        }
        for (String key : TOOL_RESULT_KEYS) {
            if (trimmed.contains("\"" + key + "\"")) {
                return true;
            }
        }
        return false;
    }

    public static String getToolEmoji(String toolName) {
        if (toolName == null) {
            return TOOL_EMOJIS.getOrDefault("", "🛠️");
        }
        return TOOL_EMOJIS.getOrDefault(toolName, "🛠️");
    }

    public static String formatToolResult(String line) {
        if (line == null || line.isEmpty()) {
            throw new IllegalArgumentException("Tool result line cannot be null or empty");
        }
        String trimmed = line.trim();
        if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) {
            throw new IllegalArgumentException("Line is not a JSON object: " + line);
        }
        JsonNode node;
        try {
            node = MAPPER.readTree(trimmed);
        } catch (Exception e) {
            throw new IllegalArgumentException("Invalid JSON in tool result line: " + line, e);
        }
        String toolName = node.get("tool") != null ? node.get("tool").asText() : "";
        String path = node.get("path") != null ? node.get("path").asText() : null;
        if (path == null || path.isEmpty()) {
            throw new IllegalArgumentException("Tool result line missing 'path' field: " + line);
        }
        String emoji = getToolEmoji(toolName);
        return emoji + " " + toolName + ": " + path;
    }
}
