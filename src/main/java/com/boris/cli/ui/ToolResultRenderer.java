package com.boris.cli.ui;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.regex.Matcher;

public final class ToolResultRenderer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String[] TOOL_RESULT_KEYS = {
            "success", "path", "message", "deleted", "tool",
            "command", "query", "urls", "paths", "outputPath", "documentType"
    };

    public static final String INVALID_PATH_DISPLAY = "⚠️ Invalid tool result path";

    private static final java.util.regex.Pattern HTML_TAG_PATTERN = java.util.regex.Pattern.compile("<[^>]+>");
    private static final java.util.regex.Pattern HTML_ENTITY_PATTERN = java.util.regex.Pattern.compile("&[a-zA-Z#]+;");
    private static final java.util.regex.Pattern ANGLE_BRACKET_PATTERN = java.util.regex.Pattern.compile("[<>]");

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

    /**
     * Regex que detecta objetos JSON planos que contengan al menos una clave
     * de resultado de herramienta (tool, path, message, deleted, success).
     * Se usa para extraer y formatear resultados de herramienta embebidos
     * dentro de texto (por ejemplo, dentro de bloques de thinking).
     */
    private static final java.util.regex.Pattern TOOL_RESULT_JSON_PATTERN =
            java.util.regex.Pattern.compile("\\{[^{}]*\"(?:tool|path|message|deleted|success|command|query|urls|paths|outputPath|documentType)\"[^{}]*\\}");

    private ToolResultRenderer() {
    }

    private static void validatePath(String path) {
        if (path == null || path.isEmpty()) {
            throw new InvalidToolResultPathException("Tool result path cannot be null or empty");
        }
        if (HTML_TAG_PATTERN.matcher(path).find()) {
            throw new InvalidToolResultPathException("Tool result path contains HTML tags: " + path);
        }
        if (HTML_ENTITY_PATTERN.matcher(path).find()) {
            throw new InvalidToolResultPathException("Tool result path contains HTML entities: " + path);
        }
        if (ANGLE_BRACKET_PATTERN.matcher(path).find()) {
            throw new InvalidToolResultPathException("Tool result path contains angle brackets: " + path);
        }
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
        if (toolName.isBlank()) {
            toolName = inferToolName(node);
        }
        if (path != null && !path.isEmpty()) {
            validatePath(path);
            String emoji = getToolEmoji(toolName);
            return toolName.isBlank()
                    ? emoji + " " + path
                    : emoji + " " + toolName + ": " + path;
        }
        String detail = inferDetail(node);
        if (detail.isEmpty()) {
            throw new IllegalArgumentException("Tool result line missing 'path' field: " + line);
        }
        String emoji = getToolEmoji(toolName);
        return toolName.isBlank()
                ? emoji + " " + detail
                : emoji + " " + toolName + ": " + detail;
    }

    private static String inferToolName(JsonNode node) {
        if (node.has("command")) {
            String command = node.get("command").asText("").trim();
            return command.startsWith("git") ? "git" : "bash";
        }
        if (node.has("query")) {
            return "web_search";
        }
        if (node.has("urls")) {
            return "web_fetch";
        }
        if (node.has("paths")) {
            return "batch_read_files";
        }
        if (node.has("outputPath")) {
            return "generate_pdf";
        }
        if (node.has("documentType")) {
            return "create_office_document";
        }
        return "";
    }

    private static String inferDetail(JsonNode node) {
        if (node.has("command")) {
            return node.get("command").asText("").trim();
        }
        if (node.has("query")) {
            return node.get("query").asText("").trim();
        }
        if (node.has("urls") && node.get("urls").isArray()) {
            return joinArrayValues(node.get("urls"));
        }
        if (node.has("paths") && node.get("paths").isArray()) {
            return joinArrayValues(node.get("paths"));
        }
        return "";
    }

    private static String joinArrayValues(JsonNode arrayNode) {
        StringBuilder joined = new StringBuilder();
        for (JsonNode item : arrayNode) {
            if (joined.length() > 0) {
                joined.append(' ');
            }
            joined.append(item.asText());
        }
        return joined.toString();
    }

    /**
     * Busca y formatea objetos JSON de resultado de herramienta embebidos dentro
     * de un texto (por ejemplo, dentro de un bloque de thinking). Reemplaza cada
     * JSON encontrado por su representación con emoji.
     */
    public static String formatToolResultsInText(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        Matcher matcher = TOOL_RESULT_JSON_PATTERN.matcher(text);
        StringBuilder result = new StringBuilder();
        int lastEnd = 0;
        while (matcher.find()) {
            result.append(text, lastEnd, matcher.start());
            String json = matcher.group();
            try {
                result.append(formatToolResult(json));
            } catch (Exception e) {
                // Si el JSON no es un resultado de herramienta válido, mantenerlo tal cual
                result.append(json);
            }
            lastEnd = matcher.end();
        }
        result.append(text, lastEnd, text.length());
        return result.toString();
    }
}
