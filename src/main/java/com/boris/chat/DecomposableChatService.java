package com.boris.chat;

import java.util.function.Consumer;

import com.boris.settings.Settings;
import com.boris.settings.SettingsManager;
import com.boris.task.TaskAborter;
import com.boris.task.decomposition.ComplexityEstimator;
import com.boris.task.decomposition.TaskComplexity;
import com.boris.task.decomposition.TaskDecompositionService;
import com.boris.task.decomposition.TaskDecompositionService.DecompositionConfig;
import com.boris.task.decomposition.TaskDecompositionService.DecompositionMode;
import com.boris.task.decomposition.TaskDecompositionService.ProcessingResult;
import com.boris.task.decomposition.TaskPlan;

/**
 * ChatService wrapper que añade capacidades de descomposición de tareas.
 * Integra las opciones 3 (Complexity Gate) y 4 (Agentic Loop).
 */
public class DecomposableChatService {

    private final ChatService delegateChatService;
    private final TaskDecompositionService decompositionService;
    private final ComplexityEstimator estimator;
    private final Consumer<String> progressCallback;
    private DecompositionMode currentMode;
    private boolean autoDecompose;

    public DecomposableChatService(ChatService chatService, Consumer<String> progressCallback) {
        this(chatService, progressCallback, DecompositionConfig.defaults());
    }

    public DecomposableChatService(ChatService chatService,
                                   Consumer<String> progressCallback,
                                   DecompositionConfig config) {
        this.delegateChatService = chatService;
        this.progressCallback = progressCallback;
        this.currentMode = config.mode();
        this.autoDecompose = config.autoDecompose();
        this.estimator = new ComplexityEstimator(this::callLlm);
        this.decompositionService = new TaskDecompositionService(
                this::callLlm,
                progressCallback,
                config
        );
    }

    /**
     * Envía un mensaje con posible descomposición automática
     */
    public String sendMessage(String userMessage) {
        if (userMessage == null || userMessage.isBlank()) {
            return delegateChatService.sendMessage(userMessage);
        }

        // Check for mode override commands
        String processed = checkModeCommands(userMessage);
        if (processed != null) {
            return processed;
        }

        // If decomposition is disabled, delegate directly
        if (currentMode == DecompositionMode.DISABLED) {
            return delegateChatService.sendMessage(userMessage);
        }

        // Estimate complexity first
        if (autoDecompose) {
            ComplexityEstimator.EstimationResult estimation = estimator.estimate(userMessage);

            if (estimation.requiresDecomposition()) {
                progressCallback.accept("🔍 Tarea compleja detectada (" + estimation.complexity().getLabel() + ")");
                return processWithDecomposition(userMessage);
            }
        }

        // Simple task - direct execution
        return delegateChatService.sendMessage(userMessage);
    }

    /**
     * Streaming con descomposición
     */
    public void sendMessageStream(String userMessage, Consumer<String> onChunk, Runnable onComplete) {
        if (userMessage == null || userMessage.isBlank()) {
            delegateChatService.sendMessageStream(userMessage, onChunk, onComplete);
            return;
        }

        // Check for mode commands
        String processed = checkModeCommands(userMessage);
        if (processed != null) {
            onChunk.accept(processed);
            if (onComplete != null) onComplete.run();
            return;
        }

        if (currentMode == DecompositionMode.DISABLED || !autoDecompose) {
            delegateChatService.sendMessageStream(userMessage, onChunk, onComplete);
            return;
        }

        // Estimate complexity
        ComplexityEstimator.EstimationResult estimation = estimator.estimate(userMessage);

        if (!estimation.requiresDecomposition()) {
            delegateChatService.sendMessageStream(userMessage, onChunk, onComplete);
            return;
        }

        // Complex task - process with decomposition
        progressCallback.accept("🔍 Tarea compleja detectada (" + estimation.complexity().getLabel() + ")");

        Thread task = new Thread(() -> {
            try {
                String result = processWithDecomposition(userMessage);
                onChunk.accept(result);
            } finally {
                if (onComplete != null) onComplete.run();
            }
        });
        task.setDaemon(true);
        task.start();
    }

    private String processWithDecomposition(String userMessage) {
        ProcessingResult result = decompositionService.process(userMessage);

        if (result.decomposed() && result.plan() != null) {
            return formatDecomposedResponse(result);
        }

        return result.response();
    }

    private String formatDecomposedResponse(ProcessingResult result) {
        StringBuilder sb = new StringBuilder();
        TaskPlan plan = result.plan();

        sb.append("📋 **Plan ejecutado:** ").append(plan.getId()).append("\n");
        sb.append("📊 **Complejidad:** ").append(result.complexity().getLabel()).append("\n");
        sb.append("✅ **Progreso:** ").append(plan.getProgressBar()).append("\n\n");
        sb.append(result.response());

        return sb.toString();
    }

    private String checkModeCommands(String message) {
        String lower = message.toLowerCase().trim();

        if (lower.equals("/decompose on") || lower.equals("/descomponer on")) {
            autoDecompose = true;
            currentMode = DecompositionMode.COMPLEXITY_GATE;
            return "✅ Descomposición automática activada (modo: complexity gate)";
        }

        if (lower.equals("/decompose off") || lower.equals("/descomponer off")) {
            autoDecompose = false;
            currentMode = DecompositionMode.DISABLED;
            return "⏸ Descomposición automática desactivada";
        }

        if (lower.equals("/decompose agentic") || lower.equals("/descomponer agentic")) {
            autoDecompose = true;
            currentMode = DecompositionMode.AGENTIC_LOOP;
            return "✅ Modo agéntico activado (con checkpoints)";
        }

        if (lower.equals("/decompose prompt") || lower.equals("/descomponer prompt")) {
            currentMode = DecompositionMode.PROMPT_ONLY;
            autoDecompose = false;
            return "✅ Modo prompt-only activado (el LLM decide)";
        }

        if (lower.equals("/decompose tool") || lower.equals("/descomponer tool")) {
            currentMode = DecompositionMode.TOOL_BASED;
            autoDecompose = false;
            return "✅ Modo tool-based activado (usar plan_task manualmente)";
        }

        if (lower.equals("/decompose status") || lower.equals("/descomponer status")) {
            return String.format("📊 Estado de descomposición:\n- Modo: %s\n- Auto-detectar: %s\n- Procesando: %s",
                    currentMode, autoDecompose, decompositionService.isProcessing());
        }

        if (lower.startsWith("/estimate ") || lower.startsWith("/estimar ")) {
            String taskToEstimate = message.substring(message.indexOf(' ') + 1);
            ComplexityEstimator.EstimationResult est = estimator.estimate(taskToEstimate);
            return String.format("📊 Estimación:\n- Complejidad: %s\n- Pasos estimados: %d\n- Requiere plan: %s",
                    est.complexity().getLabel(), est.estimatedSteps(), est.requiresDecomposition());
        }

        return null;
    }

    private String callLlm(String prompt) {
        return delegateChatService.sendMessage(prompt);
    }

    // ========== Delegated Methods ==========

    public void clearHistory() {
        delegateChatService.clearHistory();
    }

    public void setThinkingEnabled(boolean enabled) {
        delegateChatService.setThinkingEnabled(enabled);
    }

    public boolean isThinkingEnabled() {
        return delegateChatService.isThinkingEnabled();
    }

    public TaskAborter getTaskAborter() {
        return delegateChatService.getTaskAborter();
    }

    public void abort() {
        decompositionService.abort();
        delegateChatService.getTaskAborter().abort();
    }

    // ========== Mode Control ==========

    public DecompositionMode getCurrentMode() {
        return currentMode;
    }

    public void setMode(DecompositionMode mode) {
        this.currentMode = mode;
        this.autoDecompose = (mode == DecompositionMode.COMPLEXITY_GATE || mode == DecompositionMode.AGENTIC_LOOP);
    }

    public boolean isAutoDecomposeEnabled() {
        return autoDecompose;
    }

    public void setAutoDecompose(boolean enabled) {
        this.autoDecompose = enabled;
    }

    /**
     * Estima la complejidad de una tarea sin ejecutarla
     */
    public ComplexityEstimator.EstimationResult estimateComplexity(String task) {
        return estimator.estimate(task);
    }

    /**
     * Factory method que mantiene compatibilidad con ChatService.withTools
     */
    public static DecomposableChatService withTools(String settingsPath, String botName, Consumer<String> progressCallback) throws Exception {
        ChatService baseService = ChatService.withTools(settingsPath, botName);
        return new DecomposableChatService(baseService, progressCallback);
    }

    public static DecomposableChatService withTools(String settingsPath, String botName,
                                                    Consumer<String> progressCallback,
                                                    DecompositionConfig config) throws Exception {
        ChatService baseService = ChatService.withTools(settingsPath, botName);
        return new DecomposableChatService(baseService, progressCallback, config);
    }
}
