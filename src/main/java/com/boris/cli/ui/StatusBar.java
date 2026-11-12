package com.boris.cli.ui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.gui2.LinearLayout;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.EnumSet;

public class StatusBar extends Panel {

    static final String IDLE_MARK = "●";
    static final String IDLE_TEXT = "en pausa...";
    private static final String THINKING_TEXT = "pensando...";
    private static final int TIME_COLUMN_WIDTH = 6;
    private static final String IDLE_SPINNER_TEXT = " " + IDLE_MARK + " " + IDLE_TEXT;
    private static final String THINKING_EMOJI = "💭";

    private final UiExecutor uiExecutor;
    private String spinnerText = "";
    private TextColor spinnerColor = UiTheme.ACCENT;
    private String infoText = "";
    private TextColor infoColor = UiTheme.ACCENT;
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
        setRenderer(new StatusBarRenderer());
    }

    public String spinnerText() {
        return spinnerText;
    }

    public TextColor spinnerColor() {
        return spinnerColor;
    }

    public String infoText() {
        return infoText;
    }

    public TextColor infoColor() {
        return infoColor;
    }

    private static int displayWidth(String text) {
        return RenderUtils.displayWidth(text);
    }

    private void updatePreferredSize() {
        int spinnerWidth = displayWidth(spinnerText);
        int infoWidth = displayWidth(infoText);
        int spacing = (spinnerWidth > 0 && infoWidth > 0) ? 1 : 0;
        int totalWidth = spinnerWidth + infoWidth + spacing;
        setPreferredSize(new TerminalSize(Math.max(1, totalWidth), 1));
    }

    public void clear() {
        uiExecutor.run(() -> {
            spinnerColor = UiTheme.IDLE;
            spinnerText = IDLE_SPINNER_TEXT;
            infoText = "";
            updatePreferredSize();
        });
    }

    public void showTokenStatus(TokenCounter tokens) {
        uiExecutor.run(() -> {
            spinnerColor = UiTheme.IDLE;
            spinnerText = IDLE_SPINNER_TEXT;
            infoColor = UiTheme.ACCENT;
            persistentStatus = tokens.statusText();
            infoText = persistentStatus;
            updatePreferredSize();
        });
    }

    public void showTemporaryMessage(String message, int durationSeconds) {
        if (pendingClear != null) {
            pendingClear.cancel(false);
        }
        uiExecutor.run(() -> {
            infoColor = UiTheme.ACCENT;
            infoText = message;
            updatePreferredSize();
        });
        pendingClear = scheduler.schedule(() -> {
            uiExecutor.run(() -> {
                infoText = persistentStatus;
                updatePreferredSize();
            });
        }, durationSeconds, TimeUnit.SECONDS);
    }

    public void showThinking(String frame, TextColor color, String text, int minutes, int seconds, TokenCounter tokens) {
        UiText.requireNoAnsiEscape(frame, "frame");
        UiText.requireNoAnsiEscape(text, "text");
        uiExecutor.run(() -> {
            spinnerColor = color;
            spinnerText = " " + frame + " " + text;
            infoColor = UiTheme.ACCENT;
            infoText = " " + timeColumn(minutes, seconds) + " " + tokens.plainStatus();
            updatePreferredSize();
        });
    }

    private static String timeColumn(int minutes, int seconds) {
        String time = minutes > 0 ? String.format("%dm%02ds", minutes, seconds) : String.format("%ds", seconds);
        return String.format("%1$-" + TIME_COLUMN_WIDTH + "s", time);
    }

    public void showThinkingState(boolean thinkingEnabled) {
        uiExecutor.run(() -> {
            spinnerColor = UiTheme.IDLE;
            spinnerText = IDLE_SPINNER_TEXT;
            infoColor = UiTheme.ACCENT;
            if (thinkingEnabled) {
                infoText = THINKING_EMOJI + " Thinking: activado";
            } else {
                infoText = "";
            }
            updatePreferredSize();
        });
    }

    public void showAborted() {
        uiExecutor.run(() -> {
            spinnerColor = UiTheme.IDLE;
            spinnerText = IDLE_SPINNER_TEXT;
            infoColor = UiTheme.ACCENT;
            infoText = " aborted";
            updatePreferredSize();
        });
    }

    public void showQueueStatus(int queueSize) {
        uiExecutor.run(() -> {
            spinnerColor = UiTheme.IDLE;
            spinnerText = IDLE_SPINNER_TEXT;
            infoColor = UiTheme.ACCENT;
            infoText = " cola: " + queueSize + " mensajes pendientes";
            updatePreferredSize();
        });
    }

    private static class StatusBarRenderer implements ComponentRenderer<Panel> {

        @Override
        public TerminalSize getPreferredSize(Panel component) {
            StatusBar bar = (StatusBar) component;
            int spinnerWidth = displayWidth(bar.spinnerText);
            int infoWidth = displayWidth(bar.infoText);
            int spacing = (spinnerWidth > 0 && infoWidth > 0) ? 1 : 0;
            return new TerminalSize(Math.max(1, spinnerWidth + infoWidth + spacing), 1);
        }

        @Override
        public void drawComponent(TextGUIGraphics graphics, Panel component) {
            StatusBar bar = (StatusBar) component;
            TerminalSize size = graphics.getSize();
            if (size == null) {
                return;
            }

            int width = size.getColumns();
            int height = size.getRows();

            for (int row = 0; row < height; row++) {
                for (int col = 0; col < width; col++) {
                    graphics.setCharacter(col, row, new com.googlecode.lanterna.TextCharacter(' ', UiTheme.BG, UiTheme.BG));
                }
            }

            int textWidth = width;

            if (!bar.spinnerText.isEmpty()) {
                CodepointAwareTextRenderer.renderWithStyle(graphics, bar.spinnerText, 0, 0, textWidth,
                        bar.spinnerColor, UiTheme.BG, EnumSet.noneOf(SGR.class));
                int spinnerWidth = displayWidth(bar.spinnerText);
                if (!bar.infoText.isEmpty() && spinnerWidth + 1 < textWidth) {
                    CodepointAwareTextRenderer.renderWithStyle(graphics, bar.infoText, 0, spinnerWidth + 1, textWidth,
                            bar.infoColor, UiTheme.BG, EnumSet.noneOf(SGR.class));
                }
            } else if (!bar.infoText.isEmpty()) {
                CodepointAwareTextRenderer.renderWithStyle(graphics, bar.infoText, 0, 0, textWidth,
                        bar.infoColor, UiTheme.BG, EnumSet.noneOf(SGR.class));
            }
        }
    }
}
