package com.boris.cli.ui;

import com.googlecode.lanterna.TerminalSize;

public class Transcript {

    private final StringBuilder raw = new StringBuilder();
    private final ChatPanel chatPanel;
    private final UiExecutor uiExecutor;

    public Transcript(ChatPanel chatPanel, UiExecutor uiExecutor) {
        this.chatPanel = chatPanel;
        this.uiExecutor = uiExecutor;
    }

    public void appendLine(String text) {
        uiExecutor.run(() -> {
            if (raw.length() > 0) raw.append("\n");
            raw.append(text);
            renderWrapped();
            scrollToBottom();
        });
    }

    public void appendAssistantPrefix() {
        uiExecutor.run(() -> {
            if (raw.length() > 0) raw.append("\n");
            raw.append("● ");
            renderWrapped();
            scrollToBottom();
        });
    }

    public void appendChunk(String chunk) {
        uiExecutor.run(() -> {
            raw.append(chunk);
            renderWrapped();
            scrollToBottom();
        });
    }

    public void endAssistantResponse() {
        uiExecutor.run(() -> {
            raw.append("\n");
            renderWrapped();
            scrollToBottom();
        });
    }

    public void clear() {
        uiExecutor.run(() -> {
            raw.setLength(0);
            chatPanel.setText("");
        });
    }

    public void rerender() {
        uiExecutor.run(this::renderWrapped);
    }

    private void renderWrapped() {
        int width = usableChatWidth();
        String wrapped = TextWrap.wrap(raw.toString(), width);
        boolean wasAtBottom = chatPanel.isAtBottom();
        chatPanel.setText(wrapped);
        if (wasAtBottom) {
            chatPanel.scrollToBottom();
        }
    }

    private void scrollToBottom() {
        chatPanel.scrollToBottom();
    }

    private int usableChatWidth() {
        TerminalSize size = chatPanel.getSize();
        return Math.max(10, size.getColumns() - 3);
    }
}
