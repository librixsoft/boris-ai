package com.boris.cli.ui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.LinearLayout;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class StatusBar extends Panel {

    static final String IDLE_MARK = "●";
    static final String IDLE_TEXT = "en pausa...";
    private static final String THINKING_TEXT = "pensando...";
    private static final int TIME_COLUMN_WIDTH = 6;
    private static final String IDLE_SPINNER_TEXT = " " + IDLE_MARK + " " + IDLE_TEXT;

    private final UiExecutor uiExecutor;
    private final Label spinnerLabel;
    private final Label infoLabel;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "StatusBar-Timer");
        t.setDaemon(true);
        return t;
    });
    private ScheduledFuture<?> pendingClear;
    private String persistentStatus = "";

    public StatusBar(UiExecutor uiExecutor) {
        super(new LinearLayout(Direction.HORIZONTAL));
        this.uiExecutor = uiExecutor;
        this.spinnerLabel = new Label("");
        this.infoLabel = new Label("");
        spinnerLabel.setForegroundColor(UiTheme.ACCENT);
        infoLabel.setForegroundColor(UiTheme.ACCENT);
        spinnerLabel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Beginning));
        infoLabel.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Beginning));
        addComponent(spinnerLabel);
        addComponent(infoLabel);
    }

    public String spinnerText() {
        return spinnerLabel.getText();
    }

    public TextColor spinnerColor() {
        return spinnerLabel.getForegroundColor();
    }

    public String infoText() {
        return infoLabel.getText();
    }

    public TextColor infoColor() {
        return infoLabel.getForegroundColor();
    }

    public void clear() {
        uiExecutor.run(() -> {
            spinnerLabel.setForegroundColor(UiTheme.IDLE);
            spinnerLabel.setText(IDLE_SPINNER_TEXT);
            infoLabel.setText("");
        });
    }

    public void showTokenStatus(TokenCounter tokens) {
        uiExecutor.run(() -> {
            spinnerLabel.setForegroundColor(UiTheme.IDLE);
            spinnerLabel.setText(IDLE_SPINNER_TEXT);
            infoLabel.setForegroundColor(UiTheme.ACCENT);
            persistentStatus = tokens.statusText();
            infoLabel.setText(persistentStatus);
        });
    }

    public void showTemporaryMessage(String message, int durationSeconds) {
        if (pendingClear != null) {
            pendingClear.cancel(false);
        }
        uiExecutor.run(() -> {
            infoLabel.setForegroundColor(UiTheme.ACCENT);
            infoLabel.setText(message);
        });
        pendingClear = scheduler.schedule(() -> {
            uiExecutor.run(() -> infoLabel.setText(persistentStatus));
        }, durationSeconds, TimeUnit.SECONDS);
    }

    public void showThinking(String frame, TextColor color, String text, int minutes, int seconds, TokenCounter tokens) {
        UiText.requireNoAnsiEscape(frame, "frame");
        UiText.requireNoAnsiEscape(text, "text");
        uiExecutor.run(() -> {
            spinnerLabel.setForegroundColor(color);
            spinnerLabel.setText(" " + frame + " " + text);
            infoLabel.setForegroundColor(UiTheme.ACCENT);
            infoLabel.setText(" " + timeColumn(minutes, seconds) + " " + tokens.plainStatus());
        });
    }

    private static String timeColumn(int minutes, int seconds) {
        String time = minutes > 0 ? String.format("%dm%02ds", minutes, seconds) : String.format("%ds", seconds);
        return String.format("%1$-" + TIME_COLUMN_WIDTH + "s", time);
    }

    public void showThinkingState(boolean thinkingEnabled) {
        uiExecutor.run(() -> {
            spinnerLabel.setForegroundColor(UiTheme.IDLE);
            spinnerLabel.setText(IDLE_SPINNER_TEXT);
            infoLabel.setForegroundColor(UiTheme.ACCENT);
            if (thinkingEnabled) {
                infoLabel.setText(" Thinking: activado");
            }
        });
    }

    public void showAborted() {
        uiExecutor.run(() -> {
            spinnerLabel.setForegroundColor(UiTheme.IDLE);
            spinnerLabel.setText(IDLE_SPINNER_TEXT);
            infoLabel.setForegroundColor(UiTheme.ACCENT);
            infoLabel.setText(" aborted");
        });
    }

    public void showQueueStatus(int queueSize) {
        uiExecutor.run(() -> {
            spinnerLabel.setForegroundColor(UiTheme.IDLE);
            spinnerLabel.setText(IDLE_SPINNER_TEXT);
            infoLabel.setForegroundColor(UiTheme.ACCENT);
            infoLabel.setText(" cola: " + queueSize + " mensajes pendientes");
        });
    }
}
