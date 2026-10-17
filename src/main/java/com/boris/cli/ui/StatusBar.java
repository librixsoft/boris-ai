package com.boris.cli.ui;

import com.googlecode.lanterna.gui2.Label;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class StatusBar extends Label {

    private final UiExecutor uiExecutor;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "StatusBar-Timer");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> pendingClear;
    private String persistentStatus = "";

    public StatusBar(UiExecutor uiExecutor) {
        super("");
        this.uiExecutor = uiExecutor;
        setForegroundColor(UiTheme.ACCENT);
    }

    public void clear() {
        uiExecutor.run(() -> setText(""));
    }

    public void showTokenStatus(TokenCounter tokens) {
        uiExecutor.run(() -> {
            persistentStatus = tokens.statusText();
            setText(persistentStatus);
        });
    }

    public void showTemporaryMessage(String message, int durationSeconds) {
        if (pendingClear != null) {
            pendingClear.cancel(false);
        }
        uiExecutor.run(() -> setText(message));
        pendingClear = scheduler.schedule(() -> {
            uiExecutor.run(() -> setText(persistentStatus));
        }, durationSeconds, TimeUnit.SECONDS);
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

    public void showQueueStatus(int queueSize) {
        uiExecutor.run(() -> setText(" cola: " + queueSize + " mensajes pendientes"));
    }
}
