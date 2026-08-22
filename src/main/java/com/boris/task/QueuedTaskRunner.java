package com.boris.task;

import java.util.List;

import com.boris.chat.ChatService;
import com.boris.memory.MemoryService;
import com.boris.cli.ui.TokenCounter;

public class QueuedTaskRunner {

    public interface Callbacks {
        void onTaskStarted(SubTask task, int total);
        void onTaskChunk(String chunk);
        void onTaskCompleted(SubTask task, int total, String outputTail);
        void onTaskFailed(SubTask task, int total, String error);
        void onQueueCompleted(int completed, int total);
        void onQueueCancelled(int completed, int total);
        void onQueueFailed(String error);
    }

    private static final int CONTINUITY_TAIL_CHARS = 600;
    private static final int CONTEXT_MESSAGES = 10;
    private static final int TOOL_SCHEMA_RESERVE_TOKENS = 1800;

    private final ChatService chatService;
    private final MemoryService memoryService;
    private final TokenCounter tokenCounter;
    private final TaskAborter taskAborter;
    private final TaskPlanner planner;
    private final Callbacks callbacks;

    public QueuedTaskRunner(ChatService chatService, MemoryService memoryService, TokenCounter tokenCounter,
                            TaskAborter taskAborter, TaskPlanner planner, Callbacks callbacks) {
        this.chatService = chatService;
        this.memoryService = memoryService;
        this.tokenCounter = tokenCounter;
        this.taskAborter = taskAborter;
        this.planner = planner;
        this.callbacks = callbacks;
    }

    public void run(String goal, List<SubTask> tasks) {
        if (tasks == null || tasks.isEmpty()) {
            throw new com.boris.exceptions.BorisException("Cannot run an empty task queue");
        }
        executeNext(goal, tasks, 0, 0, "");
    }

    private void executeNext(String goal, List<SubTask> tasks, int position, int completedSoFar, String previousTail) {
        if (taskAborter.isAborted()) {
            callbacks.onQueueCancelled(completedSoFar, tasks.size());
            return;
        }
        if (position >= tasks.size()) {
            callbacks.onQueueCompleted(completedSoFar, tasks.size());
            return;
        }

        SubTask task = tasks.get(position);
        if (!task.markRunning()) {
            task.markFailed();
            callbacks.onQueueFailed("la subtarea " + task.getIndex() + " ya fue iniciada");
            return;
        }
        int total = tasks.size();
        callbacks.onTaskStarted(task, total);

        String partPrompt = buildPartPrompt(goal, tasks, position, previousTail);
        String userLabel = "[tarea " + task.getIndex() + "/" + total + "] " + task.getTitle();

        StringBuilder partOutput = new StringBuilder();

        try {
            String fullPrompt = applyTokenBudget(partPrompt);

            chatService.sendMessageStreamWithPrompt(
                    fullPrompt,
                    userLabel,
                    chunk -> {
                        if (taskAborter.isAborted()) {
                            return;
                        }
                        if (chunk != null && !chunk.isEmpty()) {
                            partOutput.append(chunk);
                            callbacks.onTaskChunk(chunk);
                        }
                    },
                    () -> onPartComplete(goal, tasks, position, completedSoFar,
                            tailOf(partOutput.toString())),
                    error -> {
                        task.markFailed();
                        if (!taskAborter.isAborted()) {
                            callbacks.onTaskFailed(task, total, error.getMessage());
                        }
                    }
            );
        } catch (Exception e) {
            task.markFailed();
            String message = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
            if (taskAborter.isAborted()) {
                callbacks.onQueueCancelled(completedSoFar, total);
            } else {
                callbacks.onTaskFailed(task, total, message);
            }
        }
    }

    private void onPartComplete(String goal, List<SubTask> tasks, int position, int completedSoFar, String tail) {
        int total = tasks.size();
        SubTask task = tasks.get(position);
        if (taskAborter.isAborted()) {
            callbacks.onQueueCancelled(completedSoFar, total);
            return;
        }
        task.markDone();
        int completed = completedSoFar + 1;
        callbacks.onTaskCompleted(task, total, tail);
        try {
            executeNext(goal, tasks, position + 1, completed, tail);
        } catch (Exception e) {
            callbacks.onQueueFailed(e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName());
        }
    }

    private String buildPartPrompt(String goal, List<SubTask> tasks, int position, String previousTail) {
        SubTask current = tasks.get(position);
        int total = tasks.size();

        StringBuilder sb = new StringBuilder();
        sb.append("===== TAREA POR PARTES =====\n");
        sb.append("OBJETIVO GENERAL: ").append(goal).append("\n");
        sb.append("PLAN COMPLETO (").append(total).append(" partes):\n");
        for (SubTask t : tasks) {
            String marker = t.getStatus() == SubTask.Status.DONE ? "[COMPLETADA]" : "[PENDIENTE]";
            sb.append(marker).append(" Parte ").append(t.getIndex()).append("/").append(total)
                    .append(": ").append(t.getTitle()).append("\n");
        }
        sb.append("PARTE ACTUAL: ").append(current.getIndex()).append("/").append(total)
                .append(" — ").append(current.getTitle()).append("\n");
        if (previousTail != null && !previousTail.isBlank()) {
            sb.append("FINAL DE LA SALIDA PREVIA (continúa desde ahí, no la repitas):\n")
                    .append(previousTail).append("\n");
        }
        sb.append("REGLAS:\n");
        sb.append("- Ejecuta SOLO la parte actual del plan.\n");
        sb.append("- NO repitas contenido de partes anteriores; continúa exactamente donde quedaron.\n");
        sb.append("- Si el objetivo implica archivos, escribe los archivos de esta parte con las herramientas disponibles.\n");
        sb.append("- Tu última línea debe ser exactamente: TAREA ").append(current.getIndex())
                .append(" DE ").append(total).append(" COMPLETADA\n");

        return sb.toString();
    }

    private String applyTokenBudget(String partPrompt) {
        if (memoryService == null) {
            return partPrompt;
        }
        int effectiveLimit = tokenCounter.limit();
        int memoryLimit = memoryService.getMaxContextTokens();
        if (memoryLimit > 0) {
            effectiveLimit = Math.min(effectiveLimit, memoryLimit);
        }

        int overheadTokens = TOOL_SCHEMA_RESERVE_TOKENS + chatService.getSystemPromptTokens();
        int available = effectiveLimit - tokenCounter.generated()
                - planner.getReserveResponseTokens()
                - overheadTokens;

        int historyBudget = available - tokenCounter.estimateTokens(partPrompt);
        if (historyBudget < 0) {
            historyBudget = 0;
        }

        String fullPrompt = memoryService.buildContextPrompt(partPrompt, historyBudget, CONTEXT_MESSAGES);

        int estimated = tokenCounter.estimateTokens(fullPrompt);
        int projectedTotal = estimated + tokenCounter.generated() + planner.getReserveResponseTokens()
                + overheadTokens;
        if (projectedTotal > effectiveLimit) {
            throw new com.boris.exceptions.BorisException(
                    "la subtarea excede la ventana de contexto incluso sin historial");
        }
        return fullPrompt;
    }

    private String tailOf(String output) {
        if (output == null || output.length() <= CONTINUITY_TAIL_CHARS) {
            return output == null ? "" : output;
        }
        return output.substring(output.length() - CONTINUITY_TAIL_CHARS);
    }
}
