package com.boris.tooling;

import com.boris.tooling.DiffLineNotifier;

/**
 * Utilidad pública para renderizar el resumen de tool calls en el chat
 * (emoji + nombre de herramienta + detalle). Se extrae de {@link StreamingFileWriter}
 * y se llama desde el path nativo del bucle de tools.
 */
public final class ToolCallSummaryNotifier {

    private ToolCallSummaryNotifier() {
    }

    /**
     * Emite una línea limpia con emoji y detalle del tool (por ejemplo:
     * "📂 batch_read_files /Users/.../index.html") mediante DiffLineNotifier.
     */
    public static void emitToolSummaryLine(String toolName, String jsonArgs) {
        if (toolName == null || toolName.isBlank()) {
            return;
        }

        String emoji = switch (toolName) {
            case "batch_read_files" -> "📂";
            case "list_files" -> "📁";
            case "write_file", "bulk_edit" -> "📝";
            case "apply_edit" -> "✏️";
            case "revert_edit" -> "↩️";
            case "delete_file", "delete_directory" -> "🗑️";
            case "create_directory" -> "📁";
            case "git" -> "🌿";
            case "maven_build" -> "🔨";
            case "web_search" -> "🔍";
            case "web_fetch" -> "🌐";
            case "bash" -> "💻";
            case "get_system_info" -> "ℹ️";
            case "generate_pdf" -> "📄";
            case "create_office_document" -> "📊";
            case "streaming_line_start", "streaming_line_write", "streaming_line_end" -> "✍️";
            default -> "🔧";
        };

        String detail = extractToolDetail(toolName, jsonArgs);
        String line = detail.isEmpty()
                ? emoji + " " + toolName
                : emoji + " " + toolName + " " + detail;

        DiffLineNotifier.notify(line);
    }

    private static String extractToolDetail(String toolName, String json) {
        if (json == null || json.isBlank()) {
            return "";
        }
        try {
            return switch (toolName) {
                case "batch_read_files" -> extractJsonStringArray(json, "paths");
                case "list_files" -> extractJsonString(json, "path");
                case "write_file", "bulk_edit" -> extractJsonString(json, "path");
                case "apply_edit", "revert_edit" -> extractJsonString(json, "path");
                case "delete_file", "delete_directory" -> extractJsonString(json, "path");
                case "create_directory" -> extractJsonString(json, "path");
                case "bash" -> extractJsonString(json, "command");
                case "web_search" -> extractJsonString(json, "query");
                case "web_fetch" -> extractJsonString(json, "urls");
                case "git" -> extractJsonString(json, "command");
                case "generate_pdf" -> extractJsonString(json, "outputPath");
                case "create_office_document" -> extractJsonString(json, "documentType");
                default -> "";
            };
        } catch (Exception e) {
            return "";
        }
    }

    private static String extractJsonString(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx == -1) {
            return "";
        }
        int colon = json.indexOf(':', keyIdx + search.length());
        if (colon == -1) {
            return "";
        }
        int afterColon = colon + 1;
        while (afterColon < json.length() && json.charAt(afterColon) == ' ') {
            afterColon++;
        }
        if (afterColon >= json.length()) {
            return "";
        }
        if (json.charAt(afterColon) == '"') {
            int endQuote = json.indexOf('"', afterColon + 1);
            if (endQuote == -1) {
                return "";
            }
            return json.substring(afterColon + 1, endQuote);
        }
        int end = json.indexOf(',', afterColon);
        if (end == -1) {
            end = json.indexOf('}', afterColon);
        }
        if (end == -1) {
            end = json.length();
        }
        return json.substring(afterColon, end).trim();
    }

    private static String extractJsonStringArray(String json, String key) {
        String search = "\"" + key + "\"";
        int keyIdx = json.indexOf(search);
        if (keyIdx == -1) {
            return "";
        }
        int bracketStart = json.indexOf('[', keyIdx);
        int bracketEnd = json.indexOf(']', bracketStart);
        if (bracketStart == -1 || bracketEnd == -1) {
            return "";
        }
        String inner = json.substring(bracketStart + 1, bracketEnd).trim();
        return inner.replace("\"", "").replace(",", " ").replaceAll("\\s+", " ").trim();
    }
}
