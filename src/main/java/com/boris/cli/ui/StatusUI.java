package com.boris.cli.ui;

/**
 * StatusUI — displays a thinking indicator with elapsed time counter.
 * Shows "thinking - Xs" in dimmed colors while processing, and handles
 * cleanup when done or aborted.
 */
public class StatusUI {
    
    private final TerminalConfigurator terminalConfigurator;
    private final ColorPalette colorPalette;
    private Thread statusThread;
    
    public StatusUI(TerminalConfigurator terminalConfigurator, ColorPalette colorPalette) {
        this.terminalConfigurator = terminalConfigurator;
        this.colorPalette = colorPalette;
    }
    
    /**
     * Start the thinking indicator with elapsed time counter.
     * Returns the status thread.
     */
    public Thread start() {
        long startTime = System.currentTimeMillis();
        
        Thread t = new Thread(() -> {
            try {
                terminalConfigurator.out("\033[?25l"); // hide cursor
                while (!Thread.currentThread().isInterrupted()) {
                    long elapsed = System.currentTimeMillis() - startTime;
                    int seconds = (int) (elapsed / 1000);
                    terminalConfigurator.out("\r\033[2K");
                    terminalConfigurator.out(colorPalette.dim());
                    terminalConfigurator.out("thinking" + " - " + seconds + "s");
                    terminalConfigurator.out(colorPalette.reset());
                    Thread.sleep(80);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        t.setDaemon(true);
        t.start();
        this.statusThread = t;
        return t;
    }
    
    /**
     * Stop the thinking indicator and restore cursor visibility.
     */
    public void stop() throws InterruptedException {
        if (statusThread != null) {
            statusThread.interrupt();
            statusThread.join(200);
            terminalConfigurator.out("\033[?25h\n");
        }
    }
    
    /**
     * Get the status thread.
     */
    public Thread getThread() {
        return statusThread;
    }
}
