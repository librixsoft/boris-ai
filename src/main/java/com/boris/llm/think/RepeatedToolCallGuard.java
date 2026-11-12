package com.boris.llm.think;

import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Guardia anti-bucle por turno: registra las ejecuciones de herramientas por
 * firma canónica (nombre + argumentos canonicalizados) y detecta cuando una
 * misma firma supera el límite de ejecuciones permitidas en un turno, aunque
 * las repeticiones no sean consecutivas. También rastrea accesos a rutas de
 * directorio para detectar bucles de listado/lectura sobre el mismo path
 * incluso cuando los argumentos varían entre llamadas.
 */
public class RepeatedToolCallGuard {

    public static final int MAX_EXECUTIONS_PER_TOOL_NAME = 2;
    public static final int DEFAULT_MAX_CONSECUTIVE_PER_TOOL = 3;
    public static final int MAX_DIRECTORY_ACCESS_PER_TURN = 2;

    private final int maxExecutionsPerSignature;
    private final int maxConsecutiveExecutionsPerTool;
    private final Map<String, Integer> executionsBySignature = new HashMap<>();
    private final Map<String, Integer> executionsByToolName = new HashMap<>();
    private final Map<String, Integer> directoryAccessCount = new HashMap<>();
    private String lastToolName;
    private int consecutiveExecutionsForCurrentTool;
    private static final ObjectMapper ARG_MAPPER = new ObjectMapper();

    public RepeatedToolCallGuard(int maxExecutionsPerSignature) {
        this(maxExecutionsPerSignature, DEFAULT_MAX_CONSECUTIVE_PER_TOOL);
    }

    public RepeatedToolCallGuard(int maxExecutionsPerSignature, int maxConsecutiveExecutionsPerTool) {
        if (maxExecutionsPerSignature < 1) {
            throw new IllegalArgumentException("maxExecutionsPerSignature debe ser >= 1");
        }
        if (maxConsecutiveExecutionsPerTool < 1) {
            throw new IllegalArgumentException("maxConsecutiveExecutionsPerTool debe ser >= 1");
        }
        this.maxExecutionsPerSignature = maxExecutionsPerSignature;
        this.maxConsecutiveExecutionsPerTool = maxConsecutiveExecutionsPerTool;
    }

    /**
     * Registra una ejecución de herramienta.
     * @return true si la firma canónica superó el límite del turno,
     *         si la herramienta superó el límite total por nombre de tool,
     *         o si la herramienta superó el límite de ejecuciones consecutivas
     */
    public boolean recordExecution(String toolName, String arguments) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName es requerido");
        }
        if (toolName.equals(lastToolName)) {
            consecutiveExecutionsForCurrentTool++;
        } else {
            lastToolName = toolName;
            consecutiveExecutionsForCurrentTool = 1;
        }
        if (consecutiveExecutionsForCurrentTool > maxConsecutiveExecutionsPerTool) {
            return true;
        }
        int executionsForName = executionsByToolName.merge(toolName, 1, Integer::sum);
        if (executionsForName > MAX_EXECUTIONS_PER_TOOL_NAME) {
            return true;
        }
        String normalizedArgs = normalizePathInArguments(arguments);
        String signature = toolName + ":" + ToolCallCanonicalizer.canonicalize(normalizedArgs);
        int executions = executionsBySignature.merge(signature, 1, Integer::sum);
        return executions > maxExecutionsPerSignature;
    }

    /**
     * Verifica si la próxima ejecución de la herramienta excedería algún límite
     * sin incrementar los contadores. Devuelve true si la ejecución debe bloquearse
     * antes de ocurrir.
     */
    public boolean recordExecutionPreCheck(String toolName, String arguments) {
        if (toolName == null || toolName.isBlank()) {
            throw new IllegalArgumentException("toolName es requerido");
        }
        int nextConsecutive = toolName.equals(lastToolName)
                ? consecutiveExecutionsForCurrentTool + 1
                : 1;
        if (nextConsecutive > maxConsecutiveExecutionsPerTool) {
            return true;
        }
        int nextExecutionsForName = executionsByToolName.getOrDefault(toolName, 0) + 1;
        if (nextExecutionsForName > MAX_EXECUTIONS_PER_TOOL_NAME) {
            return true;
        }
        String normalizedArgs = normalizePathInArguments(arguments);
        String signature = toolName + ":" + ToolCallCanonicalizer.canonicalize(normalizedArgs);
        int nextExecutions = executionsBySignature.getOrDefault(signature, 0) + 1;
        return nextExecutions > maxExecutionsPerSignature;
    }

    /**
     * Normaliza la ruta {@code path} en los argumentos JSON a una ruta absoluta
     * para que {@code path: "."} y {@code path: "/abs/path"} generen la misma firma.
     */
    private String normalizePathInArguments(String arguments) {
        if (arguments == null || arguments.isBlank()) {
            return arguments;
        }
        try {
            JsonNode node = ARG_MAPPER.readTree(arguments);
            if (node instanceof JsonNode objectNode && objectNode.has("path") && objectNode.get("path").isTextual()) {
                String rawPath = objectNode.get("path").asText();
                String normalized = normalizeToAbsolutePath(rawPath);
                if (normalized != null && !normalized.equals(rawPath)) {
                    ((ObjectNode) objectNode).put("path", normalized);
                    return ARG_MAPPER.writeValueAsString(objectNode);
                }
            }
            return arguments;
        } catch (Exception e) {
            return arguments;
        }
    }

    private String normalizeToAbsolutePath(String path) {
        if (path == null || path.isBlank()) {
            return null;
        }
        try {
            return Paths.get(path).toAbsolutePath().normalize().toString();
        } catch (Exception e) {
            return path;
        }
    }

    /**
     * Registra un acceso a una ruta de directorio (list_files / batch_read_files).
     * @param directoryPath ruta del directorio accedido (normalizada)
     * @return true si el mismo directorio ha sido accedido más veces de las permitidas en este turno
     */
    public boolean recordDirectoryAccess(String directoryPath) {
        if (directoryPath == null || directoryPath.isBlank()) {
            throw new IllegalArgumentException("directoryPath es requerido");
        }
        int count = directoryAccessCount.merge(directoryPath, 1, Integer::sum);
        return count > MAX_DIRECTORY_ACCESS_PER_TURN;
    }

    public void reset() {
        executionsBySignature.clear();
        executionsByToolName.clear();
        directoryAccessCount.clear();
        consecutiveExecutionsForCurrentTool = 0;
        lastToolName = null;
    }
}
