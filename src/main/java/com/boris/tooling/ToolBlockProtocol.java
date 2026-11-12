package com.boris.tooling;

import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public final class ToolBlockProtocol {

    public record ToolBlock(String toolName, String jsonArguments) {}

    private static final String BLOCK_MARKER = "```tool:";
    private static final String BLOCK_END = "```";
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private ToolBlockProtocol() {
    }

    /**
     * Devuelve el índice exacto (exclusivo) en {@code content} donde termina el primer bloque
     * ```` ```tool:...``` ```` cerrado. Si no hay ningún bloque cerrado, devuelve -1.
     * Soporta variantes compactas emitidas por modelos locales (ej. ```` ```tool:bash{"command":"ls"} ````
     * sin espacio entre el nombre y la llave de apertura).
     */
    public static int endOfFirstClosedBlock(String content) {
        if (content == null || content.isBlank()) {
            return -1;
        }
        int markerIdx = indexOfToolMarker(content);
        if (markerIdx < 0) {
            return -1;
        }
        // Buscar el cierre ``` después del marcador
        int searchStart = markerIdx + BLOCK_MARKER.length();
        int endIdx = content.indexOf(BLOCK_END, searchStart);
        if (endIdx < 0) {
            return -1;
        }
        return endIdx + BLOCK_END.length();
    }

    private static int indexOfToolMarker(String content) {
        int idx = content.indexOf(BLOCK_MARKER);
        if (idx < 0) {
            // Variante compacta: ```tool:bash{...} sin espacio
            int compactIdx = content.indexOf("```tool:");
            if (compactIdx >= 0) {
                return compactIdx;
            }
            return -1;
        }
        return idx;
    }

    /**
     * Extrae todos los bloques ```` ```tool:...``` ```` de {@code content}.
     * Valida el JSON de argumentos antes de devolver; si un bloque está truncado
     * o tiene JSON inválido, se devuelve un {@link ToolBlock} con argumentos vacíos
     * y el nombre de herramienta, para que el llamador pueda devolver un error estructurado.
     */
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
                // Variante compacta: ```tool:bash{...}
                int compactIdx = trimmed.indexOf(BLOCK_MARKER);
                if (compactIdx >= 0 && compactIdx > 0) {
                    // Verificar que lo que precede sea solo ``` (no texto anterior en la línea)
                    String before = trimmed.substring(0, compactIdx).trim();
                    if (!before.isEmpty()) {
                        continue;
                    }
                    String afterMarker = trimmed.substring(compactIdx + BLOCK_MARKER.length()).trim();
                    String toolName = extractToolNameCompact(afterMarker);
                    if (toolName == null || toolName.isBlank()) {
                        continue;
                    }
                    StringBuilder args = new StringBuilder();
                    // El JSON puede estar en la misma línea después del nombre
                    String rest = afterMarker.substring(toolName.length()).trim();
                    if (!rest.isEmpty() && rest.startsWith("{")) {
                        args.append(rest);
                    }
                    while (i < lines.length) {
                        String inner = lines[i];
                        i++;
                        if (inner.trim().equals(BLOCK_END)) {
                            break;
                        }
                        if (args.length() > 0) {
                            args.append("\n");
                        }
                        args.append(inner);
                    }
                    blocks.add(new ToolBlock(toolName, args.toString()));
                    continue;
                }
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
                if (inner.trim().equals(BLOCK_END)) {
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

    private static String extractToolNameCompact(String afterMarker) {
        // El nombre de la herramienta va seguido de { o espacio o fin de línea
        int braceIdx = afterMarker.indexOf('{');
        int spaceIdx = afterMarker.indexOf(' ');
        int endIdx = -1;
        if (braceIdx >= 0) {
            endIdx = braceIdx;
        }
        if (spaceIdx >= 0 && (endIdx < 0 || spaceIdx < endIdx)) {
            endIdx = spaceIdx;
        }
        if (endIdx < 0) {
            return afterMarker.trim();
        }
        return afterMarker.substring(0, endIdx).trim();
    }

    /**
     * Valida que los argumentos JSON de un bloque tool sean parseables.
     * @return true si el JSON es válido o está vacío; false si está truncado/inválido.
     */
    public static boolean validateJson(String jsonArguments) {
        if (jsonArguments == null || jsonArguments.isBlank()) {
            return true;
        }
        String trimmed = jsonArguments.trim();
        if (trimmed.isEmpty()) {
            return true;
        }
        try {
            MAPPER.readTree(trimmed);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Extrae el nombre de herramienta y valida JSON de un bloque crudo (línea de apertura + contenido).
     * Se usa para validación previa antes de ejecutar.
     */
    public static ToolBlock extractAndValidate(String openingLine, StringBuilder contentSoFar) {
        String trimmed = openingLine.trim();
        String toolName;
        String args;
        int compactIdx = trimmed.indexOf(BLOCK_MARKER);
        if (compactIdx >= 0) {
            String afterMarker = trimmed.substring(compactIdx + BLOCK_MARKER.length()).trim();
            toolName = extractToolNameCompact(afterMarker);
            String rest = afterMarker.substring(toolName.length()).trim();
            args = rest.isEmpty() ? "" : rest;
        } else {
            String header = trimmed.substring(BLOCK_MARKER.length()).trim();
            String[] headerParts = header.split("\\s+", 2);
            toolName = headerParts[0];
            args = headerParts.length > 1 ? headerParts[1].trim() : "";
        }
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("Bloque tool sin nombre de herramienta: " + openingLine);
        }
        if (!validateJson(args)) {
            throw new IllegalArgumentException("Argumentos JSON inválidos o truncados en bloque tool: " + toolName);
        }
        return new ToolBlock(toolName, args);
    }
}
