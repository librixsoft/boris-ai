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

    public void showThinking(String frame, int minutes, int seconds, TokenCounter tokens) {
        uiExecutor.run(() -> setText(" " + frame + " pensando... " + minutes + "m " + seconds + "s   " + tokens.plainStatus()));
    }

    public void showAborted() {
        uiExecutor.run(() -> setText(" aborted"));
    }
}
