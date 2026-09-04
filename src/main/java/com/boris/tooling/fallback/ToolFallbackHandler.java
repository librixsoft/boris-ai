package com.boris.tooling.fallback;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.boris.tooling.tool.DeleteTool;
import com.boris.tooling.tool.EditTool;
import com.boris.tooling.tool.WriteTool;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Handles fallback execution for models that do not natively invoke tool calls
 * (such as DeepSeek-R1 / Qwen reasoning models), but output JSON blocks, simulated
 * tool payloads, or multi-file structures directly in their text response.
 */
public class ToolFallbackHandler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    // Matches markdown codeblocks containing JSON or tool_call
    private static final Pattern CODE_BLOCK_PATTERN = Pattern.compile(
            "```(?:json|tool_call|tool)?\\s*(\\{[\\s\\S]*?\\})\\s*```",
            Pattern.CASE_INSENSITIVE
    );

    public record FallbackResult(
            boolean executed,
            boolean success,
            String toolName,
            String targetPath,
            String message
    ) {
        public static FallbackResult notExecuted() {
            return new FallbackResult(false, false, "", "", "");
        }
    }

    /**
     * Inspects the model response text and executes any detected fallback tool calls.
     *
     * @param responseText Text returned by the model.
     * @return List of fallback execution results.
     */
    public static List<FallbackResult> handleFallback(String responseText) {
        if (responseText == null || responseText.isBlank()) {
            return Collections.emptyList();
        }

        List<FallbackResult> results = new ArrayList<>();
        List<String> jsonCandidates = extractJsonCandidates(responseText);

        for (String candidate : jsonCandidates) {
            try {
                JsonNode node = MAPPER.readTree(candidate);
                if (!node.isObject()) {
                    continue;
                }

                collectAndExecuteTools(node, results);
            } catch (Exception ignored) {
                // Not a valid JSON or parse error
            }
        }

        return results;
    }

    private static List<String> extractJsonCandidates(String text) {
        List<String> candidates = new ArrayList<>();

        // 1. Try markdown codeblocks first
        Matcher codeBlockMatcher = CODE_BLOCK_PATTERN.matcher(text);
        while (codeBlockMatcher.find()) {
            String block = codeBlockMatcher.group(1).trim();
            if (!candidates.contains(block)) {
                candidates.add(block);
            }
        }

        // 2. Scan text for JSON objects with balanced braces
        int len = text.length();
        for (int i = 0; i < len; i++) {
            if (text.charAt(i) == '{') {
                int depth = 0;
                boolean inString = false;
                boolean escaped = false;
                int start = i;
                int end = -1;

                for (int j = i; j < len; j++) {
                    char c = text.charAt(j);
                    if (escaped) {
                        escaped = false;
                        continue;
                    }
                    if (c == '\\') {
                        escaped = true;
                        continue;
                    }
                    if (c == '"') {
                        inString = !inString;
                        continue;
                    }
                    if (!inString) {
                        if (c == '{') {
                            depth++;
                        } else if (c == '}') {
                            depth--;
                            if (depth == 0) {
                                end = j;
                                break;
                            }
                        }
                    }
                }

                if (end != -1) {
                    String jsonStr = text.substring(start, end + 1).trim();
                    if (!candidates.contains(jsonStr)) {
                        candidates.add(jsonStr);
                    }
                    i = end;
                }
            }
        }

        return candidates;
    }

    private static void collectAndExecuteTools(JsonNode root, List<FallbackResult> results) {
        // 1. Check for "files" or "documents" arrays
        if (root.has("files") && root.get("files").isArray()) {
            for (JsonNode fileNode : root.get("files")) {
                if (isWriteFilePayload(fileNode)) {
                    results.add(executeWriteFile(fileNode));
                }
            }
            return;
        }

        if (root.has("documents") && root.get("documents").isArray()) {
            for (JsonNode fileNode : root.get("documents")) {
                if (isWriteFilePayload(fileNode)) {
                    results.add(executeWriteFile(fileNode));
                }
            }
            return;
        }

        // 2. Check for explicit "tool" or "action" property
        String tool = "";
        if (root.hasNonNull("tool")) {
            tool = root.get("tool").asText().trim();
        } else if (root.hasNonNull("action")) {
            tool = root.get("action").asText().trim();
        }

        JsonNode paramsNode = root.has("params") ? root.get("params")
                : (root.has("parameters") ? root.get("parameters")
                : (root.has("arguments") ? root.get("arguments") : root));

        // 3. Write / Create File Tool
        if ("write_file".equalsIgnoreCase(tool) || "create_file".equalsIgnoreCase(tool) || isWriteFilePayload(paramsNode)) {
            results.add(executeWriteFile(paramsNode));
            return;
        }

        // 4. Apply Edit Tool
        if ("apply_edit".equalsIgnoreCase(tool) || "edit_file".equalsIgnoreCase(tool) || isApplyEditPayload(paramsNode)) {
            results.add(executeApplyEdit(paramsNode));
            return;
        }

        // 5. Delete File Tool
        if ("delete_file".equalsIgnoreCase(tool) || isDeleteFilePayload(paramsNode)) {
            results.add(executeDeleteFile(paramsNode));
            return;
        }

        // 6. Recursively search children if nothing matched
        root.fields().forEachRemaining(entry -> {
            JsonNode child = entry.getValue();
            if (child.isObject()) {
                collectAndExecuteTools(child, results);
            } else if (child.isArray()) {
                for (JsonNode item : child) {
                    if (item.isObject()) {
                        collectAndExecuteTools(item, results);
                    }
                }
            }
        });
    }

    private static boolean isWriteFilePayload(JsonNode node) {
        if (!node.isObject()) return false;
        boolean hasPath = node.hasNonNull("path") || node.hasNonNull("file") || node.hasNonNull("file_path");
        boolean hasContent = node.hasNonNull("content") || node.hasNonNull("code") || node.hasNonNull("body");
        return hasPath && hasContent;
    }

    private static boolean isApplyEditPayload(JsonNode node) {
        if (!node.isObject()) return false;
        boolean hasPath = node.hasNonNull("path") || node.hasNonNull("file");
        boolean hasOld = node.hasNonNull("old_text") || node.hasNonNull("oldText");
        boolean hasNew = node.hasNonNull("new_text") || node.hasNonNull("newText");
        return hasPath && hasOld && hasNew;
    }

    private static boolean isDeleteFilePayload(JsonNode node) {
        if (!node.isObject()) return false;
        return (node.hasNonNull("tool") && "delete_file".equalsIgnoreCase(node.get("tool").asText()))
                || (node.hasNonNull("action") && "delete_file".equalsIgnoreCase(node.get("action").asText()));
    }

    private static FallbackResult executeWriteFile(JsonNode node) {
        String path = getStringField(node, "path", "file", "file_path");
        String content = getStringField(node, "content", "code", "body");

        if (path == null || path.isBlank()) {
            return FallbackResult.notExecuted();
        }

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", path);
        args.put("content", content != null ? content : "");

        try {
            String output = WriteTool.execute(args);
            boolean success = !output.toLowerCase().contains("\"success\":false") && !output.toLowerCase().contains("error");
            return new FallbackResult(
                    true,
                    success,
                    "write_file",
                    path,
                    success ? "Archivo creado/actualizado con éxito: " + path : "Error al escribir archivo: " + output
            );
        } catch (Exception e) {
            return new FallbackResult(true, false, "write_file", path, "Error al escribir archivo: " + e.getMessage());
        }
    }

    private static FallbackResult executeApplyEdit(JsonNode node) {
        String path = getStringField(node, "path", "file");
        String oldText = getStringField(node, "old_text", "oldText");
        String newText = getStringField(node, "new_text", "newText");

        if (path == null || oldText == null || newText == null) {
            return FallbackResult.notExecuted();
        }

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", path);
        args.put("old_text", oldText);
        args.put("new_text", newText);

        try {
            String output = EditTool.apply_edit(args);
            boolean success = !output.toLowerCase().contains("\"success\":false") && !output.toLowerCase().contains("error");
            return new FallbackResult(
                    true,
                    success,
                    "apply_edit",
                    path,
                    success ? "Edición aplicada con éxito en: " + path : "Error al editar archivo: " + output
            );
        } catch (Exception e) {
            return new FallbackResult(true, false, "apply_edit", path, "Error al editar archivo: " + e.getMessage());
        }
    }

    private static FallbackResult executeDeleteFile(JsonNode node) {
        String path = getStringField(node, "path", "file");
        if (path == null || path.isBlank()) {
            return FallbackResult.notExecuted();
        }

        Map<String, Object> args = new LinkedHashMap<>();
        args.put("path", path);

        try {
            String output = DeleteTool.execute(args);
            boolean success = !output.toLowerCase().contains("\"success\":false") && !output.toLowerCase().contains("error");
            return new FallbackResult(
                    true,
                    success,
                    "delete_file",
                    path,
                    success ? "Archivo eliminado con éxito: " + path : "Error al eliminar archivo: " + output
            );
        } catch (Exception e) {
            return new FallbackResult(true, false, "delete_file", path, "Error al eliminar archivo: " + e.getMessage());
        }
    }

    private static String getStringField(JsonNode node, String... fieldNames) {
        for (String field : fieldNames) {
            if (node.hasNonNull(field)) {
                return node.get(field).asText();
            }
        }
        return null;
    }
}
