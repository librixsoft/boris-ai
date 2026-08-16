package com.boris.cli.ui;

/**
 * StatusUI — displays a simple thinking indicator.
 * Shows minimal status while processing without interfering with scroll region.
 */
public class StatusUI {
    
    private final TerminalConfigurator terminalConfigurator;
    private final ColorPalette colorPalette;
    private Thread statusThread;
    private volatile boolean running = false;
    
    public StatusUI(TerminalConfigurator terminalConfigurator, ColorPalette colorPalette) {
        this.terminalConfigurator = terminalConfigurator;
        this.colorPalette = colorPalette;
    }
    
    /**
     * Start the thinking indicator.
     * Returns the status thread.
     */
    public Thread start() {
        running = true;
        
        Thread t = new Thread(() -> {
            try {
                terminalConfigurator.out("\033[?25l"); // hide cursor
                while (running && !Thread.currentThread().isInterrupted()) {
                    // Just output thinking status once, don't keep refreshing
                    // to avoid scroll region interference
                    if (running) {
                        terminalConfigurator.out(colorPalette.dim());
                        terminalConfigurator.out("thinking");
                        terminalConfigurator.out(colorPalette.reset());
                    }
                    Thread.sleep(100);
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
        running = false;
        if (statusThread != null) {
            statusThread.interrupt();
            statusThread.join(200);
            terminalConfigurator.out("\033[?25h"); // show cursor
        }
    }
    
    /**
     * Get the status thread.
     */
    public Thread getThread() {
        return statusThread;
    }
}
