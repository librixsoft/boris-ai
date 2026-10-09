package com.boris.cli.ui;

import com.googlecode.lanterna.gui2.Label;

public class StatusBar extends Label {

    private final UiExecutor uiExecutor;

    public StatusBar(UiExecutor uiExecutor) {
        super("");
        this.uiExecutor = uiExecutor;
        setForegroundColor(UiTheme.ACCENT);
    }

    public void clear() {
        uiExecutor.run(() -> setText(""));
    }

    public void showTokenStatus(TokenCounter tokens) {
        uiExecutor.run(() -> setText(tokens.statusText()));
    }

    public void showThinking(String frame, int minutes, int seconds, TokenCounter tokens, boolean thinkingEnabled) {
        uiExecutor.run(() -> {
            String time = minutes > 0 ? String.format("%dm%02ds", minutes, seconds) : String.format("%ds", seconds);
            if (thinkingEnabled) {
                setText(" [T] " + frame + " pensando... " + time + "   " + tokens.plainStatus());
            } else {
                setText(" " + frame + " pensando... " + time + "   " + tokens.plainStatus());
            }
        });
    }

    public void showThinkingState(boolean thinkingEnabled) {
        uiExecutor.run(() -> {
            if (thinkingEnabled) {
                setText(" [T] Thinking: activado");
            }
        });
    }

    public void showAborted() {
        uiExecutor.run(() -> setText(" aborted"));
    }
}
