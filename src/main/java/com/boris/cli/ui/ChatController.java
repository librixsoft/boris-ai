package com.boris.cli.ui;

import com.boris.chat.ChatService;
import com.boris.llm.ollama.OllamaModelService;
import com.boris.skill.Skill;
import com.boris.skill.SkillManager;
import com.boris.task.TaskAborter;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private final Runnable onModelSelected;

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
        this(chatService, skillManager, taskAborter, commandHistory, tokenCounter, spinner, statusBar, transcript, chatPanel, waiting, wasAborted, onClose, ollamaModelService, null);
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
        this.onModelSelected = onModelSelected != null ? onModelSelected : () -> {};
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
        if (!waiting.get()) {
            return;
        }
        taskAborter.abort();
        wasAborted.set(true);
        waiting.set(false);
        statusBar.showAborted();
        Thread resetThread = new Thread(() -> {
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            taskAborter.reset();
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
            chatService.clearHistory();
            tokenCounter.resetSession();
            transcript.clear();
            statusBar.clear();
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
        transcript.appendLine(">> " + text);

        wasAborted.set(false);
        taskAborter.reset();
        waiting.set(true);
        spinner.start();

        StringBuilder assistantBuffer = new StringBuilder();
        AtomicBoolean firstChunk = new AtomicBoolean(true);

        Thread task = new Thread(() -> runStream(text, assistantBuffer, firstChunk));
        task.setDaemon(true);
        task.start();
    }

    private void runStream(String text, StringBuilder assistantBuffer, AtomicBoolean firstChunk) {
        try {
            chatService.sendMessageStream(
                    text,
                    chunk -> {
                        if (taskAborter.isAborted()) {
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
                    () -> finishResponse(assistantBuffer)
            );
        } catch (Exception e) {
            waiting.set(false);
            if (!taskAborter.isAborted()) {
                transcript.appendLine("[x] error: " + e.getMessage());
            }
            taskAborter.reset();
        }
    }

    private void finishResponse(StringBuilder assistantBuffer) {
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

    private void handleModelCommand(String text) {
        String trimmed = text.trim();
        // If command is exactly /model or /models, the InputArea shows the ModelSelector
        // This method only handles /model <modelname> syntax
        if (trimmed.equals("/model") || trimmed.equals("/models")) {
            // Model selector is handled by InputArea
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
        String requestedModel = trimmed;
        boolean found = false;
        for (String model : models) {
            if (model.equals(requestedModel) || model.contains(requestedModel)) {
                ollamaModelService.selectModel(requestedModel);
                transcript.appendLine("[✓] Modelo cambiado a: " + requestedModel);
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
