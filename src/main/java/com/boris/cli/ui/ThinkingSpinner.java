package com.boris.cli.ui;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

public class ThinkingSpinner {

    private static final String[] FRAMES = {"⣾", "⣽", "⣻", "⢿", "⡿", "⣟", "⣯", "⣷"};

    private static final String[] COLORS = {
        "\u001b[38;2;255;0;0m",    // Rojo puro
        "\u001b[38;2;255;69;0m",   // Rojo-naranja
        "\u001b[38;2;255;140;0m",  // Naranja
        "\u001b[38;2;255;200;0m",  // Naranja-amarillo
        "\u001b[38;2;255;255;0m",  // Amarillo
        "\u001b[38;2;255;215;0m",  // Dorado
    };
    private static final String RESET = "\u001b[0m";

    private final StatusBar statusBar;
    private final TokenCounter tokenCounter;
    private final AtomicBoolean waiting;
    private final AtomicBoolean wasAborted;
    private final java.util.function.BooleanSupplier thinkingEnabledSupplier;
    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicLong messageStartTime = new AtomicLong(0);

    public boolean isRunning() {
        return running.get();
    }

    public ThinkingSpinner(StatusBar statusBar, TokenCounter tokenCounter, AtomicBoolean waiting, AtomicBoolean wasAborted) {
        this(statusBar, tokenCounter, waiting, wasAborted, () -> true);
    }

    public ThinkingSpinner(StatusBar statusBar, TokenCounter tokenCounter, AtomicBoolean waiting, AtomicBoolean wasAborted, java.util.function.BooleanSupplier thinkingEnabledSupplier) {
        this.statusBar = statusBar;
        this.tokenCounter = tokenCounter;
        this.waiting = waiting;
        this.wasAborted = wasAborted;
        this.thinkingEnabledSupplier = thinkingEnabledSupplier;
    }

    public void resetTimer() {
        messageStartTime.set(System.currentTimeMillis());
    }

    public void start() {
        resetTimer();

        if (running.compareAndSet(false, true)) {
            Thread thread = new Thread(() -> {
                int i = 0;
                int colorIndex = 0;

                while (waiting.get() && !wasAborted.get()) {
                    String frame = FRAMES[i % FRAMES.length];
                    String color = COLORS[colorIndex % COLORS.length];

                    String coloredFrame = color + frame + RESET;
                    String coloredText = color + "pensando..." + RESET;

                    long elapsed = System.currentTimeMillis() - messageStartTime.get();
                    int seconds = (int) (elapsed / 1000) % 60;
                    int minutes = (int) (elapsed / 1000) / 60;

                    statusBar.showThinking(coloredFrame, coloredText, minutes, seconds, tokenCounter, thinkingEnabledSupplier.getAsBoolean());
                    i++;
                    colorIndex++;

                    try {
                        Thread.sleep(80);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                }

                running.set(false);
                if (!wasAborted.get()) {
                    statusBar.showTokenStatus(tokenCounter);
                }
            });
            thread.setDaemon(true);
            thread.start();
        }
    }
}
