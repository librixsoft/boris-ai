package com.boris.tooling;

import java.util.ArrayList;
import java.util.List;

public final class ToolBlockProtocol {

    public record ToolBlock(String toolName, String jsonArguments) {}

    private static final String BLOCK_MARKER = "```tool:";

    private ToolBlockProtocol() {
    }

    public static List<ToolBlock> parse(String content) {
        List<ToolBlock> blocks = new ArrayList<>();
        if (content == null || content.isBlank()) {
            return blocks;
        }
        String[] lines = content.split("\n", -1);
        int i = 0;
        while (i < lines.length) {
            String line = lines[i++];
            String trimmed = line.trim();
            if (!trimmed.startsWith(BLOCK_MARKER)) {
                continue;
            }
            String header = trimmed.substring(BLOCK_MARKER.length()).trim();
            String[] headerParts = header.split("\\s+", 2);
            String toolName = headerParts[0];
            if (toolName.isBlank()) {
                throw new IllegalArgumentException("Bloque tool sin nombre de herramienta: " + line);
            }
            StringBuilder args = new StringBuilder();
            if (headerParts.length > 1 && !headerParts[1].isBlank()) {
                args.append(headerParts[1].trim());
            }
            while (i < lines.length) {
                String inner = lines[i];
                i++;
                if (inner.trim().equals("```")) {
                    break;
                }
                if (args.length() > 0) {
                    args.append("\n");
                }
                args.append(inner);
            }
            blocks.add(new ToolBlock(toolName, args.toString()));
        }
        return blocks;
    }
}
