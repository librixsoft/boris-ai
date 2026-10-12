package com.boris.cli.ui;

import com.boris.chat.ChatService;
import com.boris.llm.ollama.OllamaModelService;
import com.boris.queue.ChatMessage;
import com.boris.queue.MessageQueueService;
import com.boris.settings.SettingsManager;
import com.boris.skill.Skill;
import com.boris.skill.SkillManager;
import com.boris.task.TaskAborter;
import com.boris.tooling.ToolResultNotifier;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ChatController implements InputArea.InputListener {

    private final ChatService chatService;
    private final TaskAborter taskAborter;
    private final CommandHistory commandHistory;
    private final TokenCounter tokenCounter;
    private final ThinkingSpinner spinner;
    private final StatusBar statusBar;
    private final Transcript transcript;
    private final ChatPanel chatPanel;
    private final AtomicBoolean waiting;
    private final AtomicBoolean wasAborted;
    private final Runnable onClose;
    private final SkillManager skillManager;
    private final OllamaModelService ollamaModelService;
    private final HeaderBar headerBar;
    private final SettingsManager settingsManager;
    private final Runnable onModelSelected;
    private final AtomicLong sessionEpoch = new AtomicLong(0);
    private final MessageQueueService messageQueue;

    public ChatController(ChatService chatService,
                          SkillManager skillManager,
                          TaskAborter taskAborter,
                          CommandHistory commandHistory,
                          TokenCounter tokenCounter,
                          ThinkingSpinner spinner,
                          StatusBar statusBar,
                          Transcript transcript,
                          ChatPanel chatPanel,
                          AtomicBoolean waiting,
                          AtomicBoolean wasAborted,
                          Runnable onClose,
                          OllamaModelService ollamaModelService) {
        this(chatService, skillManager, taskAborter, commandHistory, tokenCounter, spinner, statusBar, transcript, chatPanel, waiting, wasAborted, onClose, ollamaModelService, null, null);
    }

    public ChatController(ChatService chatService,
                          SkillManager skillManager,
                          TaskAborter taskAborter,
                          CommandHistory commandHistory,
                          TokenCounter tokenCounter,
                          ThinkingSpinner spinner,
                          StatusBar statusBar,
                          Transcript transcript,
                          ChatPanel chatPanel,
                          AtomicBoolean waiting,
                          AtomicBoolean wasAborted,
                          Runnable onClose,
                          OllamaModelService ollamaModelService,
                          HeaderBar headerBar,
                          SettingsManager settingsManager) {
        this(chatService, skillManager, taskAborter, commandHistory, tokenCounter, spinner, statusBar, transcript, chatPanel, waiting, wasAborted, onClose, ollamaModelService, headerBar, settingsManager, null);
    }

    public ChatController(ChatService chatService,
                          SkillManager skillManager,
                          TaskAborter taskAborter,
                          CommandHistory commandHistory,
                          TokenCounter tokenCounter,
                          ThinkingSpinner spinner,
                          StatusBar statusBar,
                          Transcript transcript,
                          ChatPanel chatPanel,
                          AtomicBoolean waiting,
                          AtomicBoolean wasAborted,
                          Runnable onClose,
                          OllamaModelService ollamaModelService,
                          HeaderBar headerBar,
                          SettingsManager settingsManager,
                          Runnable onModelSelected) {
        this.chatService = chatService;
        this.skillManager = skillManager;
        this.taskAborter = taskAborter;
        this.commandHistory = commandHistory;
        this.tokenCounter = tokenCounter;
        this.spinner = spinner;
        this.statusBar = statusBar;
        this.transcript = transcript;
        this.chatPanel = chatPanel;
        this.waiting = waiting;
        this.wasAborted = wasAborted;
        this.onClose = onClose;
        this.ollamaModelService = ollamaModelService;
        this.headerBar = headerBar;
        this.settingsManager = settingsManager;
        this.onModelSelected = onModelSelected != null ? onModelSelected : () -> {};

        this.messageQueue = new MessageQueueService();
        initializeMessageQueue();
        initializeToolTokenListener();
    }

    private void initializeToolTokenListener() {
        ToolResultNotifier.addTokenListener(usage -> {
            tokenCounter.addInputTokens(usage.inputTokens());
            tokenCounter.addOutputTokens(usage.outputTokens());
            if (!spinner.isRunning()) {
                statusBar.showTokenStatus(tokenCounter);
            }
        });
    }

    private void initializeMessageQueue() {
        messageQueue.setOnAbortCallback(() -> taskAborter.abort());
        messageQueue.setProcessor((message, onChunk, onComplete) -> {
            StringBuilder assistantBuffer = new StringBuilder();
            AtomicBoolean firstChunk = new AtomicBoolean(true);
            long taskEpoch = sessionEpoch.get();

            if (taskAborter.isAborted() || messageQueue.isAborted()) {
                onComplete.run();
                return;
            }

            chatService.sendMessageStream(
                message.content(),
                chunk -> {
                    if (taskEpoch != sessionEpoch.get() || taskAborter.isAborted() || messageQueue.isAborted()) {
                        return;
                    }
                    if (chunk != null && !chunk.isEmpty()) {
                        synchronized (assistantBuffer) {
                            assistantBuffer.append(chunk);
                        }
                        tokenCounter.addOutputTokens(chunk);
                        if (firstChunk.compareAndSet(true, false)) {
                            transcript.appendAssistantPrefix();
                        }
                        transcript.appendChunk(chunk);
                    }
                },
                () -> {
                    if (taskEpoch != sessionEpoch.get() || taskAborter.isAborted() || messageQueue.isAborted()) {
                        onComplete.run();
                        return;
                    }
                    finishResponse(assistantBuffer);
                    onComplete.run();
                }
            );
        });

        messageQueue.addListener(new MessageQueueService.QueueListener() {
            @Override
            public void onMessageEnqueued(ChatMessage message, int queueSize) {
                if (queueSize > 1) {
                    statusBar.showQueueStatus(queueSize);
                }
            }

            @Override
            public void onMessageStarted(ChatMessage message) {
                transcript.activateQueuedMessage(message.content());
                waiting.set(true);
                spinner.start();
            }

            @Override
            public void onMessageCompleted(ChatMessage message) {
                if (!messageQueue.hasMessages()) {
                    waiting.set(false);
                }
            }

            @Override
            public void onMessageFailed(ChatMessage message, Exception error) {
                transcript.appendLine("[x] error: " + error.getMessage());
                if (!messageQueue.hasMessages()) {
                    waiting.set(false);
                }
            }

            @Override
            public void onQueueEmpty() {
                waiting.set(false);
                statusBar.showTokenStatus(tokenCounter);
            }
        });

        messageQueue.start();
    }

    public SkillManager getSkillManager() {
        return skillManager;
    }

    public void setChatModel(String modelName) {
        if (ollamaModelService != null) {
            ollamaModelService.selectModel(modelName);
        }
    }

    @Override
    public void onSubmit(String text) {
        handleSubmit(text);
    }

    @Override
    public void onAbort() {
        if (!waiting.get() && !messageQueue.isProcessing() && !messageQueue.hasMessages()) {
            return;
        }
        // Clear queue first to stop pending messages
        messageQueue.clear();
        // Then abort current Ollama generation
        taskAborter.abort();
        wasAborted.set(true);
        waiting.set(false);
        statusBar.showAborted();
        Thread resetThread = new Thread(() -> {
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            taskAborter.reset();
            messageQueue.resetAbort();
        });
        resetThread.setDaemon(true);
        resetThread.start();
    }

    @Override
    public void onLineScroll(int deltaLines) {
        chatPanel.scroll(deltaLines);
    }

    @Override
    public void onPageScroll(int direction) {
        int rows = chatPanel.visibleRows();
        chatPanel.scroll(direction * rows);
    }

    private void handleSubmit(String text) {
        String trimmed = text.trim();
        String lower = trimmed.toLowerCase();
        if (lower.equals("/exit") || lower.equals("/quit")) {
            onClose.run();
            return;
        }
        if (lower.equals("/clear")) {
            handleClearCommand();
            return;
        }
        if (lower.equals("/thinking") || lower.equals("/think") || lower.equals("/reasoning")
                || lower.startsWith("/thinking ") || lower.startsWith("/think ") || lower.startsWith("/reasoning ")) {
            boolean current = chatPanel.isThinkingEnabled();
            boolean newState;
            if (lower.contains(" on") || lower.contains(" true") || lower.contains(" activar")) {
                newState = true;
            } else if (lower.contains(" off") || lower.contains(" false") || lower.contains(" desactivar")) {
                newState = false;
            } else {
                newState = !current;
            }
            chatPanel.setThinkingEnabled(newState);
            chatService.setThinkingEnabled(newState);
            transcript.appendLine(newState ? "* Razonamiento (thinking) activado" : "* Razonamiento (thinking) desactivado");
            transcript.rerender();
            return;
        }
        if (lower.equals("/effort") || lower.startsWith("/effort ") || lower.equals("/esfuerzo") || lower.startsWith("/esfuerzo ")) {
            handleEffortCommand(trimmed);
            return;
        }
        if (lower.equals("/skills")) {
            listSkills();
            return;
        }
        if (lower.startsWith("/model")) {
            handleModelCommand(trimmed);
            return;
        }
        if (isValidCommand(trimmed)) {
            String skillCmd = trimmed.substring(1);
            String skillName = skillCmd.split("\\s+")[0];
            if (skillManager.hasSkill(skillName)) {
                executeSkill(skillName);
                return;
            }
        }

        if (tokenCounter.limitReached()) {
            transcript.appendLine(tokenCounter.limitMessage());
            return;
        }

        commandHistory.record(text);
        tokenCounter.addInputTokens(text);
        statusBar.showTokenStatus(tokenCounter);

        int queueSize = messageQueue.getQueueSize();
        if (queueSize > 0 || messageQueue.isProcessing()) {
            transcript.appendQueuedMessage(text, queueSize + 1);
        } else {
            transcript.appendLine(">> " + text);
        }

        wasAborted.set(false);
        taskAborter.reset();
        messageQueue.resetAbort();

        messageQueue.enqueue(text);
    }

    /**
     * Handle /clear command: abort everything, clear all state, start fresh.
     */
    private void handleClearCommand() {
        // Phase 1: Increment epoch FIRST to invalidate any pending callbacks
        long newEpoch = sessionEpoch.incrementAndGet();

        // Phase 2: Abort immediately (synchronous)
        taskAborter.abort();
        wasAborted.set(true);

        // Phase 3: Abort message queue (interrupts current processing thread)
        messageQueue.clear();

        // Phase 4: Clear all state
        waiting.set(false);
        chatService.clearHistory();
        tokenCounter.resetSession();
        transcript.clear();
        chatPanel.clear();
        statusBar.clear();

        // Phase 5: Reset flags AFTER a delay to ensure pending responses are ignored
        Thread resetThread = new Thread(() -> {
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            // Only reset if epoch hasn't changed (no new clear/abort happened)
            if (sessionEpoch.get() == newEpoch) {
                taskAborter.reset();
                messageQueue.resetAbort();
                wasAborted.set(false);
            }
        });
        resetThread.setDaemon(true);
        resetThread.start();
    }

    private void finishResponse(StringBuilder assistantBuffer) {
        // Epoch guard: don't finalize if session was cleared
        if (wasAborted.get() || taskAborter.isAborted()) {
            return;
        }
        waiting.set(false);
        transcript.endAssistantResponse();

        String finalText;
        synchronized (assistantBuffer) {
            finalText = assistantBuffer.toString();
        }

        statusBar.showTokenStatus(tokenCounter);

        if (ChatService.EXIT_COMMAND.equals(finalText)) {
            onClose.run();
        }
        taskAborter.reset();
    }

    private boolean isValidCommand(String text) {
        if (text == null || !text.startsWith("/") || text.length() < 2) {
            return false;
        }
        String cmd = text.substring(1).split("\\s+")[0].toLowerCase();
        if (cmd.isEmpty() || cmd.contains("/") || cmd.contains(".")) {
            return false;
        }
        if (!cmd.matches("^[a-z][a-z0-9_-]*$")) {
            return false;
        }
        return isBuiltinCommand(cmd) || skillManager.hasSkill(cmd);
    }

    private boolean isBuiltinCommand(String cmd) {
        return cmd.equals("exit") || cmd.equals("quit") || cmd.equals("clear") ||
               cmd.equals("thinking") || cmd.equals("think") || cmd.equals("reasoning") ||
               cmd.equals("skills") || cmd.equals("effort") || cmd.equals("esfuerzo") ||
               cmd.equals("model") || cmd.equals("models");
    }

    /**
     * Apply model selection: rebuild ChatClient, update services, HeaderBar, and persist to settings.
     */
    public void applyModelSelection(String selectedModel) {
        if (selectedModel == null || selectedModel.isBlank()) return;

        // 1. Rebuild ChatClient with new model (this also updates modelName)
        try {
            chatService.rebuildClient(selectedModel);
        } catch (Exception e) {
            transcript.appendLine("[x] Error al cambiar modelo: " + e.getMessage());
            transcript.rerender();
            return;
        }

        // 2. Update OllamaModelService if available
        if (ollamaModelService != null) {
            ollamaModelService.selectModel(selectedModel);
        }

        // 3. Update HeaderBar visually
        if (headerBar != null) {
            headerBar.updateModel(selectedModel);
        }

        // 4. Persist to ~/.boris/settings.json
        try {
            if (settingsManager != null) {
                settingsManager.updateModelName(selectedModel);
            }
        } catch (Exception e) {
            transcript.appendLine("[!] Advertencia: No se pudo guardar en settings.json: " + e.getMessage());
        }

        // 5. Confirm in transcript and rerender
        transcript.appendLine("[✓] Modelo cambiado a: " + selectedModel);
        transcript.rerender();
    }

    private void handleModelCommand(String text) {
        String trimmed = text.trim();
        // If command is exactly /model or /models, the InputArea shows the ModelSelector
        // This method only handles /model <modelname> syntax
        if (trimmed.equals("/model") || trimmed.equals("/models")) {
            // Model selector is handled by InputArea
            return;
        }

        // Extract model name from "/model <name>" - fix the bug where trimmed still contains "/model "
        String requestedModel = trimmed;
        if (trimmed.startsWith("/model")) {
            requestedModel = trimmed.substring("/model".length()).trim();
        }

        if (requestedModel.isEmpty()) {
            // No model specified after /model command - should open selector via InputArea
            return;
        }

        if (ollamaModelService == null || !ollamaModelService.isOllamaAvailable()) {
            transcript.appendLine("[x] Ollama no está disponible. Asegúrate de que Ollama esté corriendo.");
            transcript.rerender();
            return;
        }

        List<String> models = ollamaModelService.getModels();
        if (models.isEmpty()) {
            transcript.appendLine("[x] No se encontraron modelos en Ollama.");
            transcript.rerender();
            return;
        }

        // Check if the provided model name matches any available model
        boolean found = false;
        for (String model : models) {
            if (model.equals(requestedModel) || model.contains(requestedModel)) {
                applyModelSelection(requestedModel);
                found = true;
                break;
            }
        }
        if (!found) {
            transcript.appendLine("[x] Modelo no encontrado: " + requestedModel);
            transcript.appendLine("Modelos disponibles:");
            for (int i = 0; i < Math.min(models.size(), 10); i++) {
                transcript.appendLine("  " + (i + 1) + ". " + models.get(i));
            }
        }
        transcript.rerender();
    }

    private void listSkills() {
        transcript.appendLine("Skills disponibles:");
        for (Skill skill : skillManager.getSkills()) {
            String desc = skill.getDescription() != null ? skill.getDescription() : "";
            transcript.appendLine("  /" + skill.getName() + " - " + desc);
        }
    }

    private void executeSkill(String skillName) {
        Skill skill = skillManager.getSkill(skillName);
        if (skill == null) {
            transcript.appendLine("[x] Skill no encontrada: " + skillName);
            return;
        }

        transcript.appendLine("[skill] Ejecutando /" + skillName + "...");

        Thread skillThread = new Thread(() -> {
            skillManager.execute(skillName, line -> {
                transcript.appendLine(line);
            });
            transcript.appendLine("[skill] /" + skillName + " completado");
        });
        skillThread.setDaemon(true);
        skillThread.start();
    }

    private void handleEffortCommand(String text) {
        String[] parts = text.split("\\s+", 2);

        if (parts.length < 2) {
            String currentMode = chatService.getThinkingMode();
            boolean enabled = chatService.isThinkingEnabled();
            StringBuilder help = new StringBuilder();
            help.append("📊 Nivel de esfuerzo actual: ");
            if (!enabled) {
                help.append("desactivado");
            } else {
                help.append(currentMode != null ? currentMode : "medium");
            }
            help.append("\n\nUso: /effort <nivel>\n");
            help.append("Niveles disponibles:\n");
            help.append("  • high (alto)  - Razonamiento profundo, más lento\n");
            help.append("  • medium (medio) - Balance entre velocidad y profundidad\n");
            help.append("  • low (bajo)   - Respuestas rápidas, menos reflexión\n");
            help.append("  • off (none)   - Desactivar razonamiento extendido\n");
            transcript.appendLine(help.toString());
            transcript.rerender();
            return;
        }

        String level = parts[1].toLowerCase().trim();
        String normalizedLevel = switch (level) {
            case "high", "alto", "max", "máximo" -> "high";
            case "medium", "medio", "med", "normal" -> "medium";
            case "low", "bajo", "min", "mínimo" -> "low";
            case "off", "none", "disabled", "desactivado" -> "off";
            default -> null;
        };

        if (normalizedLevel == null) {
            transcript.appendLine("[x] Nivel no válido: " + level + ". Usa: high, medium, low, off");
            return;
        }

        chatService.setThinkingMode(normalizedLevel);

        String emoji = switch (normalizedLevel) {
            case "high" -> "🧠";
            case "medium" -> "💭";
            case "low" -> "⚡";
            case "off" -> "🔇";
            default -> "📊";
        };

        String message = switch (normalizedLevel) {
            case "high" -> "Esfuerzo ALTO - Razonamiento profundo activado";
            case "medium" -> "Esfuerzo MEDIO - Balance activado";
            case "low" -> "Esfuerzo BAJO - Respuestas rápidas";
            case "off" -> "Razonamiento extendido DESACTIVADO";
            default -> "Nivel configurado: " + normalizedLevel;
        };

        transcript.appendLine(emoji + " " + message);

        if (normalizedLevel.equals("off")) {
            chatPanel.setThinkingEnabled(false);
        } else {
            chatPanel.setThinkingEnabled(true);
        }

        transcript.rerender();
    }
}
