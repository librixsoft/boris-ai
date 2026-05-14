package com.boris.librixsoft.server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TaskParserService {

    private final ObjectMapper objectMapper;

    public TaskParserService() {
        this.objectMapper = new ObjectMapper();
    }

    public Map<String, Object> parseTasksJson(String tasksJson, String projectPath) {
        try {
            String repairedJson = repairJson(tasksJson);
            Map<String, Object> parsed = objectMapper.readValue(repairedJson, new TypeReference<>() {
            });
            Object tasksObj = parsed.get("tasks");
            if (!(tasksObj instanceof List<?> tasks) || tasks.isEmpty()) {
                log.error("El JSON no contiene tareas válidas en 'tasks'");
                return Map.of("tasks", new ArrayList<>());
            }

            LinkedHashMap<String, Map<String, Object>> byFile = new LinkedHashMap<>();

            for (Object taskObj : tasks) {
                if (!(taskObj instanceof Map<?, ?> rawTask)) {
                    continue;
                }

                String description = asString(rawTask.get("description"));
                String file = asString(rawTask.get("file"));
                String code = asString(rawTask.get("code"));

                if (file.isBlank() || code.isBlank()) {
                    continue;
                }

                String resolvedFile = resolveTaskFile(file, projectPath);
                if (resolvedFile == null || !looksLikeFilePath(resolvedFile)) {
                    continue;
                }

                Map<String, Object> cleanTask = new LinkedHashMap<>();
                cleanTask.put("description", description.isBlank() ? "Write file content" : description);
                cleanTask.put("file", resolvedFile);
                cleanTask.put("code", code);
                byFile.put(resolvedFile, cleanTask);
            }

            if (byFile.isEmpty()) {
                log.error("No se encontraron tareas de archivo válidas (file+code)");
                return Map.of("tasks", new ArrayList<>());
            }

            List<Map<String, Object>> cleanedTasks = new ArrayList<>();
            int nextId = 1;
            for (Map<String, Object> task : byFile.values()) {
                task.put("id", nextId++);
                cleanedTasks.add(task);
            }

            return Map.of("tasks", cleanedTasks);
        } catch (Exception e) {
            log.error("Error al parsear JSON: {}", e.getMessage(), e);
            return Map.of("tasks", new ArrayList<>());
        }
    }

    /**
     * Best-effort repair of a truncated JSON string produced by an LLM.
     * Closes any open string literal, then closes open arrays/objects
     * in reverse order so that the result can be parsed by Jackson.
     */
    public String repairJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return "{}";
        }

        String trimmed = raw.trim();
        int firstBrace = trimmed.indexOf('{');

        if (firstBrace != -1) {
            trimmed = trimmed.substring(firstBrace);
        }

        StringBuilder sb = new StringBuilder(trimmed);

        boolean inString = false;
        boolean escaped = false;
        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c == '\\' && inString) {
                escaped = !escaped;
            } else if (c == '"' && !escaped) {
                inString = !inString;
                escaped = false;
            } else {
                escaped = false;
            }
        }

        if (inString) {
            if (escaped) {
                sb.setLength(sb.length() - 1);
            }
            sb.append('"');
        }

        java.util.Deque<Character> stack = new java.util.ArrayDeque<>();
        inString = false;
        escaped = false;

        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (c == '\\' && inString) {
                escaped = !escaped;
            } else if (c == '"' && !escaped) {
                inString = !inString;
                escaped = false;
            } else if (!inString) {
                if (c == '{') stack.push('}');
                else if (c == '[') stack.push(']');
                else if ((c == '}' || c == ']') && !stack.isEmpty()) {
                    stack.pop();
                }
            } else {
                escaped = false;
            }
        }

        while (!stack.isEmpty()) {
            sb.append(stack.pop());
        }

        return sb.toString();
    }

    public String resolveTaskFile(String file, String projectPath) {
        try {
            String trimmedFile = file.trim();
            Path rawPath = Paths.get(trimmedFile);

            if (rawPath.isAbsolute()) {
                return rawPath.normalize().toString().replace('\\', '/');
            }

            if (projectPath != null && !projectPath.isBlank() && !".".equals(projectPath.trim())) {
                Path basePath = Paths.get(projectPath.trim());
                return basePath.resolve(rawPath).normalize().toString().replace('\\', '/');
            }

            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public boolean looksLikeFilePath(String path) {
        String normalized = path.replace("\\", "/");
        int lastSlash = normalized.lastIndexOf('/');
        String name = (lastSlash >= 0) ? normalized.substring(lastSlash + 1) : normalized;
        return !name.isBlank() && name.contains(".") && !name.endsWith(".");
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
