package com.boris.task;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

public class TaskPlanner {

    public static final int DEFAULT_MAX_SUB_TASKS = 5;
    public static final int DEFAULT_RESERVE_RESPONSE_TOKENS = 2500;

    private final Function<String, String> llmCall;
    private final ObjectMapper mapper = new ObjectMapper();
    private final boolean enabled;
    private final int maxSubTasks;
    private final int reserveResponseTokens;

    public TaskPlanner(Function<String, String> llmCall, boolean enabled, Integer maxSubTasks,
                       Integer reserveResponseTokens) {
        this.llmCall = llmCall;
        this.enabled = enabled;
        this.maxSubTasks = maxSubTasks != null && maxSubTasks > 0 ? maxSubTasks : DEFAULT_MAX_SUB_TASKS;
        this.reserveResponseTokens = reserveResponseTokens != null && reserveResponseTokens > 0
                ? reserveResponseTokens : DEFAULT_RESERVE_RESPONSE_TOKENS;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public int getMaxSubTasks() {
        return maxSubTasks;
    }

    public int getReserveResponseTokens() {
        return reserveResponseTokens;
    }

    public Classification classify(String goal) {
        if (llmCall == null) {
            throw new com.boris.exceptions.BorisException("TaskPlanner has no LLM call configured");
        }
        String raw = llmCall.apply(buildClassificationPrompt(goal));
        return parseClassification(raw);
    }

    private String buildClassificationPrompt(String goal) {
        return "Sos un clasificador de tareas para un asistente con ventana de contexto limitada.\n"
                + "Mensaje del usuario: \"" + goal + "\"\n"
                + "Decidi si responder eso bien requiere generar tanto contenido que convenga partirlo "
                + "en varias respuestas cortas y consecutivas (codigo largo, documentos extensos, paginas completas).\n"
                + "Conversacion casual, preguntas, dudas puntuales o cambios chicos NO son tareas grandes.\n"
                + "Respondes UNICAMENTE con JSON valido, sin markdown, sin explicaciones, con este formato exacto:\n"
                + "{\"esTareaGrande\": true|false, \"subtareas\": [\"parte 1\", \"parte 2\", ...]}\n"
                + "Si esTareaGrande es false, \"subtareas\" debe ser [].\n"
                + "Si es true, devuelve entre 2 y " + maxSubTasks + " subtareas cortas, ordenadas y "
                + "autocontenidas, donde cada parte continua el trabajo de la anterior sin repetirlo.";
    }

    private Classification parseClassification(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new com.boris.exceptions.BorisException("Respuesta vacia del clasificador");
        }
        String json = extractJsonObject(stripCodeFences(raw));
        try {
            JsonNode root = mapper.readTree(json);
            if (!root.isObject()) {
                throw new com.boris.exceptions.BorisException("El clasificador no devolvio un objeto JSON");
            }
            boolean largeTask = root.path("esTareaGrande").asBoolean(false);

            List<SubTask> tasks = new ArrayList<>();
            JsonNode items = root.path("subtareas");
            if (items.isArray()) {
                for (JsonNode item : items) {
                    String title = item.asText("").trim();
                    if (!title.isEmpty()) {
                        tasks.add(new SubTask(tasks.size() + 1, title));
                    }
                    if (tasks.size() >= maxSubTasks) {
                        break;
                    }
                }
            }

            if (largeTask && tasks.size() <= 1) {
                throw new com.boris.exceptions.BorisException(
                        "el clasifico como tarea grande pero devolvio menos de 2 subtareas");
            }
            return new Classification(largeTask, tasks);
        } catch (com.boris.exceptions.BorisException e) {
            throw e;
        } catch (Exception e) {
            throw new com.boris.exceptions.BorisException("No se pudo interpretar la clasificacion del modelo", e);
        }
    }

    private String stripCodeFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline > 0) {
                trimmed = trimmed.substring(firstNewline + 1);
            }
            int closing = trimmed.lastIndexOf("```");
            if (closing >= 0) {
                trimmed = trimmed.substring(0, closing);
            }
        }
        return trimmed.trim();
    }

    private String extractJsonObject(String text) {
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new com.boris.exceptions.BorisException("El clasificador no devolvio un objeto JSON");
        }
        return text.substring(start, end + 1);
    }

    public static final class Classification {
        private final boolean largeTask;
        private final List<SubTask> subtasks;

        Classification(boolean largeTask, List<SubTask> subtasks) {
            this.largeTask = largeTask;
            this.subtasks = List.copyOf(subtasks);
        }

        public boolean isLargeTask() {
            return largeTask;
        }

        public List<SubTask> getSubtasks() {
            return subtasks;
        }
    }
}
