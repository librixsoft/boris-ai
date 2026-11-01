package com.boris.drafter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.boris.tooling.ToolProgressNotifier;
import com.boris.tooling.annotation.Tool;
import com.boris.tooling.annotation.ToolParam;

public class DrafterUnavailableTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final String reason;

    public DrafterUnavailableTool(String reason) {
        this.reason = reason;
    }

    @Tool(
            name = "write_file",
            description = "Create a NEW file or fully overwrite an existing one. You provide ONLY a short specification (spec); the full file content is written by the drafter model. The big model NEVER writes file content. Do NOT use it for partial changes to an existing file; use apply_edit for those.")
    public String write_file(
            @ToolParam(description = "Absolute or relative file path") String path,
            @ToolParam(description = "Short specification of the file: purpose, structure, classes and methods, required imports and conventions") String spec) {
        ToolProgressNotifier.notify("write_file", "write_file no disponible");
        return formatError("write_file no disponible: " + reason
                + ". Boris no escribe archivos con el modelo principal. Configura un modelo drafter con /drafter <nombre> o reporta el problema.");
    }

    private static String formatError(String message) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", false);
            node.put("message", message);
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new com.boris.exceptions.BorisException("Failed to format drafter unavailable output", e);
        }
    }
}
