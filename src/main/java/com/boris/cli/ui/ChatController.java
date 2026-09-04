package com.boris.cli.ui;

import com.boris.chat.ChatService;
import com.boris.task.TaskAborter;

import java.util.concurrent.atomic.AtomicBoolean;

public class ChatController implements InputArea.InputListener {

    private final ChatService chatService;
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
            transcript.appendLine(newState ? "● Razonamiento (thinking) activado" : "● Razonamiento (thinking) desactivado");
            transcript.rerender();
            return;
        }

        if (tokenCounter.limitReached()) {
            transcript.appendLine(tokenCounter.limitMessage());
            return;
        }

        commandHistory.record(text);
        transcript.appendLine("❯ " + text);

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
            taskAborter.startTask(Thread.currentThread());

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

        String finalText;
        synchronized (assistantBuffer) {
            finalText = assistantBuffer.toString();
        }

        var fallbackResults = com.boris.tooling.fallback.ToolFallbackHandler.handleFallback(finalText);
        for (var res : fallbackResults) {
            if (res.executed()) {
                if (res.success()) {
                    transcript.appendLine("⚡ [Fallback Tool: " + res.toolName() + "] " + res.message());
                } else {
                    transcript.appendLine("✗ [Fallback Tool Error: " + res.toolName() + "] " + res.message());
                }
            }
        }

        statusBar.showTokenStatus(tokenCounter);

        if (ChatService.EXIT_COMMAND.equals(finalText)) {
            onClose.run();
        }
        taskAborter.reset();
    }
}
