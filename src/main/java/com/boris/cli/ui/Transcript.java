package com.boris.cli.ui;

import com.googlecode.lanterna.TerminalSize;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class Transcript {

    private enum EntryType { NORMAL, TOOL_DETAIL, STATS }

    private record Entry(EntryType type, String content) {}

    private final List<Entry> entries = new ArrayList<>();
    private final ChatPanel chatPanel;
    private final UiExecutor uiExecutor;

    private final AtomicBoolean showToolDetails = new AtomicBoolean(false);
    private Consumer<Integer> hiddenCountListener;

    public Transcript(ChatPanel chatPanel, UiExecutor uiExecutor) {
        this.chatPanel = chatPanel;
        this.uiExecutor = uiExecutor;
    }

    public void setHiddenCountListener(Consumer<Integer> listener) {
        this.hiddenCountListener = listener;
    }

    private void notifyHiddenCountChanged() {
        if (hiddenCountListener != null) {
            hiddenCountListener.accept(getHiddenDetailsCount());
        }
    }

    public boolean isShowingToolDetails() {
        return showToolDetails.get();
    }

    public void toggleToolDetails() {
        boolean newState = !showToolDetails.get();
        showToolDetails.set(newState);
        uiExecutor.run(() -> {
            notifyHiddenCountChanged();
            renderWrapped();
            scrollToBottom();
        });
    }

    public void appendToolDetail(String toolName, String message) {
        String formatted = "[" + toolName + "] " + message;
        uiExecutor.run(() -> {
            entries.add(new Entry(EntryType.TOOL_DETAIL, formatted));
            if (!showToolDetails.get()) {
                notifyHiddenCountChanged();
            }
            renderWrapped();
            scrollToBottom();
        });
    }

    public int getHiddenDetailsCount() {
        if (showToolDetails.get()) {
            return 0;
        }
        return (int) entries.stream()
                .filter(e -> e.type() == EntryType.TOOL_DETAIL)
                .count();
    }

    public void appendLine(String text) {
        uiExecutor.run(() -> {
            entries.add(new Entry(EntryType.NORMAL, text));
            renderWrapped();
            scrollToBottom();
        });
    }

    public void appendQueuedMessage(String text, int queuePosition) {
        uiExecutor.run(() -> {
            String content = ">> " + text + " <queued:#" + queuePosition + ">";
            entries.add(new Entry(EntryType.NORMAL, content));
            renderWrapped();
            scrollToBottom();
        });
    }

    public void activateQueuedMessage(String messageContent) {
        uiExecutor.run(() -> {
            String pattern = ">> " + messageContent + " <queued:#\\d+>";
            String replacement = ">> " + messageContent;
            for (int i = 0; i < entries.size(); i++) {
                Entry e = entries.get(i);
                if (e.type() == EntryType.NORMAL && e.content().matches(pattern)) {
                    entries.set(i, new Entry(EntryType.NORMAL, replacement));
                    break;
                }
            }
            renderWrapped();
        });
    }

    public void appendAssistantPrefix() {
        uiExecutor.run(() -> {
            entries.add(new Entry(EntryType.NORMAL, ":: "));
            renderWrapped();
            scrollToBottom();
        });
    }

    public void appendChunk(String chunk) {
        uiExecutor.run(() -> {
            if (!entries.isEmpty()) {
                int lastIdx = entries.size() - 1;
                Entry last = entries.get(lastIdx);
                if (last.type() == EntryType.NORMAL) {
                    entries.set(lastIdx, new Entry(EntryType.NORMAL, last.content() + chunk));
                } else {
                    entries.add(new Entry(EntryType.NORMAL, chunk));
                }
            } else {
                entries.add(new Entry(EntryType.NORMAL, chunk));
            }
            renderWrapped();
            scrollToBottom();
        });
    }

    public void endAssistantResponse() {
        uiExecutor.run(() -> {
            if (!entries.isEmpty()) {
                int lastIdx = entries.size() - 1;
                Entry last = entries.get(lastIdx);
                if (last.type() == EntryType.NORMAL) {
                    entries.set(lastIdx, new Entry(EntryType.NORMAL, last.content() + "\n"));
                }
            }
            renderWrapped();
            scrollToBottom();
        });
    }

    public void appendResponseStats(long durationMs, int inputTokens, int outputTokens) {
        uiExecutor.run(() -> {
            String timeStr = formatDuration(durationMs);
            String stats = timeStr + " · in: " + inputTokens + " tokens · out: " + outputTokens + " tokens";
            entries.add(new Entry(EntryType.STATS, stats));
            renderWrapped();
            scrollToBottom();
        });
    }

    private String formatDuration(long ms) {
        if (ms < 1000) {
            return ms + "ms";
        } else if (ms < 60000) {
            return String.format("%.1fs", ms / 1000.0);
        } else {
            long minutes = ms / 60000;
            long seconds = (ms % 60000) / 1000;
            return minutes + "m" + seconds + "s";
        }
    }

    public void clear() {
        uiExecutor.run(() -> {
            entries.clear();
            chatPanel.setText("");
            notifyHiddenCountChanged();
        });
    }

    public void rerender() {
        uiExecutor.run(this::renderWrapped);
    }

    private String buildRawContent() {
        StringBuilder sb = new StringBuilder();
        boolean showDetails = showToolDetails.get();
        for (Entry e : entries) {
            if (e.type() == EntryType.TOOL_DETAIL && !showDetails) {
                continue;
            }
            if (sb.length() > 0 && !sb.toString().endsWith("\n") && !e.content().startsWith("\n")) {
                sb.append("\n");
            }
            if (e.type() == EntryType.STATS) {
                sb.append("[stats]").append(e.content());
            } else if (e.type() == EntryType.TOOL_DETAIL) {
                // Mark each line with [tool] prefix so renderer can apply correct color
                String[] lines = e.content().split("\n", -1);
                for (int i = 0; i < lines.length; i++) {
                    if (i > 0) {
                        sb.append("\n");
                    }
                    sb.append("[tool]").append(lines[i]);
                }
            } else {
                sb.append(e.content());
            }
        }
        return sb.toString();
    }

    private void renderWrapped() {
        int width = usableChatWidth();
        String raw = buildRawContent();
        String wrapped = TextWrap.wrap(raw, width);
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
