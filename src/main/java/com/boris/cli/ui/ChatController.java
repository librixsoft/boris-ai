package com.boris.cli.ui;

import com.boris.chat.ChatService;
import com.boris.memory.MemoryService;
import com.boris.task.TaskAborter;

import java.util.concurrent.atomic.AtomicBoolean;

public class ChatController implements InputArea.InputListener {

    private final ChatService chatService;
    private final MemoryService memoryService;
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

        String fullPrompt = buildFullPrompt(text);
        int promptTokens = tokenCounter.estimateTokens(fullPrompt);

        if (tokenCounter.wouldExceedLimit(promptTokens)) {
            transcript.appendLine("⚠ Contexto excede límite (" + tokenCounter.formatTokens(promptTokens) + " > " + tokenCounter.formatTokens(tokenCounter.limit()) + "). Recortando historial...");
            fullPrompt = buildTrimmedPrompt(text);
            promptTokens = tokenCounter.estimateTokens(fullPrompt);
        }

        final String finalPrompt = fullPrompt;
        tokenCounter.addTokens(promptTokens);

        commandHistory.record(text);
        transcript.appendLine("❯ " + text);

        wasAborted.set(false);
        taskAborter.reset();
        waiting.set(true);
        spinner.start();

        StringBuilder assistantBuffer = new StringBuilder();
        AtomicBoolean firstChunk = new AtomicBoolean(true);

        Thread task = new Thread(() -> runStream(finalPrompt, text, assistantBuffer, firstChunk));
        task.setDaemon(true);
        task.start();
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
