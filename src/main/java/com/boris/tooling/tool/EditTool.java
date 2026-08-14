package com.boris.tooling.tool;

import java.io.IOException;
import java.nio.file.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.exceptions.BorisException;
import com.boris.tooling.ToolDefinition;

public class EditTool {

    public static ToolDefinition apply_edit() {
        var pathProp = Map.of("type", "string", "description", "Absolute or relative file path");
        var oldTextProp = Map.of("type", "string", "description", "Exact text to find and replace in the file");
        var newTextProp = Map.of("type", "string", "description", "Replacement text");
        var properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProp);
        properties.put("old_text", oldTextProp);
        properties.put("new_text", newTextProp);
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return ToolDefinition.of(
                "apply_edit",
                "Apply a surgical edit to an existing file by finding old_text and replacing it with new_text. Returns success status and message.",
                schema);
    }

    public static String apply_edit(Map<String, Object> args) {
        @SuppressWarnings("unchecked")
        String pathStr = (String) args.getOrDefault("path", "");
        @SuppressWarnings("unchecked")
        String oldText = (String) args.getOrDefault("old_text", "");
        @SuppressWarnings("unchecked")
        String newText = (String) args.getOrDefault("new_text", "");

        if (pathStr == null || pathStr.isBlank()) {
            return formatOutput(false, "Error: path is required");
        }
        if (oldText == null || oldText.isEmpty()) {
            return formatOutput(false, "Error: old_text is required");
        }

        Path path = Paths.get(pathStr);
        if (!Files.exists(path)) {
            return formatOutput(false, "Error: file not found: " + pathStr);
        }
        if (!Files.isRegularFile(path)) {
            return formatOutput(false, "Error: not a regular file: " + pathStr);
        }

        try {
            String content = Files.readString(path);
            if (!content.contains(oldText)) {
                return formatOutput(false, "Error: old_text not found in file");
            }
            String replacedContent = content.replaceFirst(java.util.regex.Pattern.quote(oldText), replaceEscaped(newText));
            Files.writeString(path, replacedContent);
            long size = Files.size(path);
            return formatOutput(true, "File edited successfully: " + pathStr + " (" + size + " bytes)");
        } catch (IOException e) {
            throw new BorisException("Error editing file: " + pathStr, e);
        }
    }

    public static ToolDefinition multi_edit() {
        var pathProp = Map.of("type", "string", "description", "Absolute or relative file path");
        var editsProp = Map.of("type", "array", "description", "Array of edit objects, each with old_text and new_text fields",
                "items", Map.of("type", "object"));
        var properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProp);
        properties.put("edits", editsProp);
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return ToolDefinition.of(
                "multi_edit",
                "Apply multiple sequential edits to a file. Each edit replaces old_text with new_text. Returns success status and message.",
                schema);
    }

    @SuppressWarnings("unchecked")
    public static String multi_edit(Map<String, Object> args) {
        @SuppressWarnings("unchecked")
        String pathStr = (String) args.getOrDefault("path", "");
        List<Map<String, Object>> editsList = (List<Map<String, Object>>) args.get("edits");

        if (pathStr == null || pathStr.isBlank()) {
            return formatOutput(false, "Error: path is required");
        }
        if (editsList == null || editsList.isEmpty()) {
            return formatOutput(false, "Error: edits array is required and must not be empty");
        }

        Path path = Paths.get(pathStr);
        if (!Files.exists(path)) {
            return formatOutput(false, "Error: file not found: " + pathStr);
        }
        if (!Files.isRegularFile(path)) {
            return formatOutput(false, "Error: not a regular file: " + pathStr);
        }

        try {
            String content = Files.readString(path);
            int totalReplacements = 0;
            for (Map<String, Object> edit : editsList) {
                @SuppressWarnings("unchecked")
                String oldText = (String) edit.get("old_text");
                @SuppressWarnings("unchecked")
                String newText = (String) edit.get("new_text");

                if (oldText == null || oldText.isEmpty()) {
                    return formatOutput(false, "Error: each edit must have non-empty old_text");
                }

                if (!content.contains(oldText)) {
                    return formatOutput(false, "Error: old_text not found in file at edit index: " + editsList.indexOf(edit));
                }
                content = content.replaceFirst(java.util.regex.Pattern.quote(oldText), replaceEscaped(newText != null ? newText : ""));
                totalReplacements++;
            }
            Files.writeString(path, content);
            long size = Files.size(path);
            return formatOutput(true, "File edited successfully: " + pathStr + " (" + size + " bytes, " + totalReplacements + " edits applied)");
        } catch (IOException e) {
            throw new BorisException("Error editing file: " + pathStr, e);
        }
    }

    public static ToolDefinition revert_edit() {
        var pathProp = Map.of("type", "string", "description", "Absolute or relative file path");
        var oldTextProp = Map.of("type", "string", "description", "Exact text to find and replace with new_text (the original content)");
        var newTextProp = Map.of("type", "string", "description", "Replacement text (original value to restore)");
        var properties = new LinkedHashMap<String, Object>();
        properties.put("path", pathProp);
        properties.put("old_text", oldTextProp);
        properties.put("new_text", newTextProp);
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        return ToolDefinition.of(
                "revert_edit",
                "Revert a previous edit by finding the edited text and replacing it with its original value. Returns success status and message.",
                schema);
    }

    public static String revert_edit(Map<String, Object> args) {
        @SuppressWarnings("unchecked")
        String pathStr = (String) args.getOrDefault("path", "");
        @SuppressWarnings("unchecked")
        String oldText = (String) args.getOrDefault("old_text", "");
        @SuppressWarnings("unchecked")
        String newText = (String) args.getOrDefault("new_text", "");

        if (pathStr == null || pathStr.isBlank()) {
            return formatOutput(false, "Error: path is required");
        }
        if (oldText == null || oldText.isEmpty()) {
            return formatOutput(false, "Error: old_text is required");
        }

        Path path = Paths.get(pathStr);
        if (!Files.exists(path)) {
            return formatOutput(false, "Error: file not found: " + pathStr);
        }
        if (!Files.isRegularFile(path)) {
            return formatOutput(false, "Error: not a regular file: " + pathStr);
        }

        try {
            String content = Files.readString(path);
            if (!content.contains(oldText)) {
                return formatOutput(false, "Error: old_text not found in file");
            }
            String revertedContent = content.replaceFirst(java.util.regex.Pattern.quote(oldText), replaceEscaped(newText != null ? newText : ""));
            Files.writeString(path, revertedContent);
            long size = Files.size(path);
            return formatOutput(true, "File edited successfully: " + pathStr + " (" + size + " bytes)");
        } catch (IOException e) {
            throw new BorisException("Error editing file: " + pathStr, e);
        }
    }

    private static String formatOutput(boolean success, String message) {
        try {
            var mapper = new ObjectMapper();
            var node = mapper.createObjectNode();
            node.put("success", success);
            node.put("message", message);
            return mapper.writeValueAsString(node);
        } catch (IOException e) {
            throw new BorisException("Failed to format output", e);
        }
    }

    private static String replaceEscaped(String replacement) {
        if (replacement == null) return "";
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\\\\(.)");
        java.util.regex.Matcher matcher = pattern.matcher(replacement);
        StringBuilder sb = new StringBuilder();
        while (matcher.find()) {
            matcher.appendReplacement(sb, java.util.regex.Matcher.quoteReplacement(matcher.group(1)));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }
}
