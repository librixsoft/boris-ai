package com.boris.cli.ui;

import com.boris.chat.ChatService;
import com.boris.memory.MemoryService;
import com.boris.task.QueuedTaskRunner;
import com.boris.task.SubTask;
import com.boris.task.TaskAborter;
import com.boris.task.TaskPlanner;
import com.boris.task.TaskPlanner.Classification;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class ChatController implements InputArea.InputListener {

    private final ChatService chatService;
    private final MemoryService memoryService;
    private final TaskPlanner taskPlanner;
    private final TaskAborter taskAborter = new TaskAborter();
    private final CommandHistory commandHistory;
    private final TokenCounter tokenCounter;
    private final ThinkingSpinner spinner;
    private final StatusBar statusBar;
    private final Transcript transcript;
    private final ChatPanel chatPanel;
    private final AtomicBoolean waiting;
    private final AtomicBoolean wasAborted;
    private final Runnable onClose;

    public ChatController(ChatService chatService,
                          MemoryService memoryService,
                          TaskPlanner taskPlanner,
                          CommandHistory commandHistory,
                          TokenCounter tokenCounter,
                          ThinkingSpinner spinner,
                          StatusBar statusBar,
                          Transcript transcript,
                          ChatPanel chatPanel,
                          AtomicBoolean waiting,
                          AtomicBoolean wasAborted,
                          Runnable onClose) {
        this.chatService = chatService;
        this.memoryService = memoryService;
        this.taskPlanner = taskPlanner;
        this.commandHistory = commandHistory;
        this.tokenCounter = tokenCounter;
        this.spinner = spinner;
        this.statusBar = statusBar;
        this.transcript = transcript;
        this.chatPanel = chatPanel;
        this.waiting = waiting;
        this.wasAborted = wasAborted;
        this.onClose = onClose;
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
        if (text.equals("/exit") || text.equals("/quit")) {
            onClose.run();
            return;
        }
        if (text.equals("/clear")) {
            chatService.clearHistory();
            tokenCounter.resetSession();
            transcript.clear();
            statusBar.clear();
            return;
        }

        if (taskPlanner != null && taskPlanner.isEnabled()) {
            commandHistory.record(text);
            transcript.appendLine("❯ " + text);
            startQueuedTask(text);
            return;
        }

        String fullPrompt = buildPromptFor(text);
        beginStream(fullPrompt, text);
    }

    private String buildPromptFor(String text) {
        String fullPrompt = buildFullPrompt(text);
        int promptTokens = tokenCounter.estimateTokens(fullPrompt);

        if (tokenCounter.wouldExceedLimit(promptTokens)) {
            fullPrompt = buildTrimmedPrompt(text);
            promptTokens = tokenCounter.estimateTokens(fullPrompt);
            tokenCounter.resetTokensOnly();
            transcript.appendLine("ℹ historial guardado en memoria persistente (H2), podés continuar");
        }

        tokenCounter.addTokens(promptTokens);
        return fullPrompt;
    }

    private void beginStream(String fullPrompt, String text) {
        wasAborted.set(false);
        taskAborter.reset();
        waiting.set(true);
        spinner.start();

        StringBuilder assistantBuffer = new StringBuilder();
        AtomicBoolean firstChunk = new AtomicBoolean(true);

        Thread task = new Thread(() -> runStream(fullPrompt, text, assistantBuffer, firstChunk));
        task.setDaemon(true);
        task.start();
    }

    private void startQueuedTask(String text) {
        wasAborted.set(false);
        taskAborter.reset();
        waiting.set(true);
        spinner.start();

        Thread plannerThread = new Thread(() -> runQueued(text));
        plannerThread.setDaemon(true);
        plannerThread.start();
    }

    private void runQueued(String goal) {
        Classification classification;
        try {
            classification = taskPlanner.classify(buildClassificationInput(goal));
        } catch (Exception e) {
            transcript.appendLine("ℹ clasificación no disponible (" + e.getMessage() + "), envío directo");
            beginStream(buildPromptFor(goal), goal);
            return;
        }

        if (!classification.isLargeTask()) {
            beginStream(buildPromptFor(goal), goal);
            return;
        }

        List<SubTask> planned = classification.getSubtasks();
        if (memoryService != null) {
            memoryService.saveUserMessage(goal);
        }
        transcript.appendLine("▸ tarea grande detectada: cola de " + planned.size() + " subtareas");
        for (SubTask t : planned) {
            transcript.appendLine("   " + t.getIndex() + ". " + t.getTitle());
        }

        QueuedTaskRunner runner = new QueuedTaskRunner(
                chatService, memoryService, tokenCounter, taskAborter, taskPlanner, queueCallbacks());
        runner.run(goal, planned);
    }

    private String buildClassificationInput(String goal) {
        if (memoryService == null) {
            return goal;
        }
        List<com.boris.memory.ConversationMessage> recent = memoryService.getRecentMessages(4);
        if (recent.isEmpty()) {
            return goal;
        }
        StringBuilder ctx = new StringBuilder("CONTEXTO RECIENTE:\n");
        for (com.boris.memory.ConversationMessage m : recent) {
            String content = m.getContent();
            if (content.length() > 200) {
                content = content.substring(content.length() - 200);
            }
            ctx.append(m.getRole()).append(": ").append(content).append("\n");
        }
        ctx.append("MENSAJE ACTUAL: ").append(goal);
        return ctx.toString();
    }

    private QueuedTaskRunner.Callbacks queueCallbacks() {
        AtomicBoolean partPrefixPending = new AtomicBoolean(true);
        return new QueuedTaskRunner.Callbacks() {
            @Override
            public void onTaskStarted(SubTask task, int total) {
                partPrefixPending.set(true);
                transcript.appendLine("▶ [" + task.getIndex() + "/" + total + "] " + task.getTitle());
            }

            @Override
            public void onTaskChunk(String chunk) {
                if (partPrefixPending.compareAndSet(true, false)) {
                    transcript.appendAssistantPrefix();
                }
                tokenCounter.addTokens(chunk.length());
                transcript.appendChunk(chunk);
            }

            @Override
            public void onTaskCompleted(SubTask task, int total, String outputTail) {
                transcript.endAssistantResponse();
                transcript.appendLine("✓ [" + task.getIndex() + "/" + total + "] completada");
                statusBar.showTokenStatus(tokenCounter);
            }

            @Override
            public void onTaskFailed(SubTask task, int total, String error) {
                transcript.endAssistantResponse();
                transcript.appendLine("✗ [" + task.getIndex() + "/" + total + "] falló: " + error);
                finishQueue();
            }

            @Override
            public void onQueueCompleted(int completed, int total) {
                transcript.appendLine("■ cola finalizada: " + completed + "/" + total
                        + " subtareas completadas, resultado ensamblado");
                finishQueue();
            }

            @Override
            public void onQueueCancelled(int completed, int total) {
                transcript.appendLine("■ cola cancelada en " + completed + "/" + total + " subtareas");
                finishQueue();
            }

            @Override
            public void onQueueFailed(String error) {
                transcript.appendLine("✗ error en la cola: " + error);
                finishQueue();
            }
        };
    }

    private void finishQueue() {
        waiting.set(false);
        taskAborter.reset();
        statusBar.showTokenStatus(tokenCounter);
    }

    private String buildFullPrompt(String userMessage) {
        if (memoryService == null) {
            return userMessage;
        }
        int tokenBudget = tokenCounter.limit() - tokenCounter.generated();
        if (tokenBudget < 1000) {
            tokenBudget = 2000;
        }
        return memoryService.buildContextPrompt(userMessage, tokenBudget, 15);
    }

    private String buildTrimmedPrompt(String userMessage) {
        if (memoryService == null) {
            return userMessage;
        }
        int tokenBudget = Math.max(2000, tokenCounter.limit() / 2);
        return memoryService.buildContextPrompt(userMessage, tokenBudget, 10);
    }

    private void runStream(String fullPrompt, String userMessage, StringBuilder assistantBuffer, AtomicBoolean firstChunk) {
        try {
            taskAborter.startTask(Thread.currentThread());

            chatService.sendMessageStreamWithPrompt(
                    fullPrompt,
                    userMessage,
                    chunk -> {
                        if (taskAborter.isAborted()) {
                            return;
                        }
                        if (chunk != null && !chunk.isEmpty()) {
                            tokenCounter.addTokens(chunk.length());
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
            transcript.appendLine("✗ error: " + e.getMessage());
            taskAborter.reset();
        }
    }

    private void finishResponse(StringBuilder assistantBuffer) {
        waiting.set(false);
        transcript.endAssistantResponse();
        statusBar.showTokenStatus(tokenCounter);

        String finalText = assistantBuffer.toString();
        if (ChatService.EXIT_COMMAND.equals(finalText)) {
            onClose.run();
        }
        taskAborter.reset();
    }
}
