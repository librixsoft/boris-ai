package com.boris.cli.ui;

import java.util.concurrent.atomic.AtomicBoolean;

public class ThinkingSpinner {

    private static final String[] FRAMES = {"⣾", "⣽", "⣻", "⢿", "⡿", "⣟", "⣯", "⣷"};

    private final StatusBar statusBar;
    private final TokenCounter tokenCounter;
    private final AtomicBoolean waiting;
    private final AtomicBoolean wasAborted;
    private final java.util.function.BooleanSupplier thinkingEnabledSupplier;

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

    public void start() {
        Thread thread = new Thread(() -> {
            int i = 0;
            long startTime = System.currentTimeMillis();

            while (waiting.get() && !wasAborted.get()) {
                String frame = FRAMES[i % FRAMES.length];

                long elapsed = System.currentTimeMillis() - startTime;
                int seconds = (int) (elapsed / 1000) % 60;
                int minutes = (int) (elapsed / 1000) / 60;

                statusBar.showThinking(frame, minutes, seconds, tokenCounter, thinkingEnabledSupplier.getAsBoolean());
                i++;

                try {
                    Thread.sleep(80);
                } catch (InterruptedException ignored) {
                    break;
                }
            }

            if (!wasAborted.get()) {
                statusBar.showTokenStatus(tokenCounter);
            }
        });
        thread.setDaemon(true);
        thread.start();
    }
}
