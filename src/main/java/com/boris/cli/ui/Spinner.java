package com.boris.cli.ui;

/**
 * Minimal spinner animation: a single braille dot cycling in the accent color,
 * followed by a quiet elapsed-time label. No background block, no box —
 * just one moving glyph on an otherwise empty line.
 */
public class Spinner {
    
    private final TerminalConfigurator terminalConfigurator;
    private final ColorPalette colorPalette;
    private Thread spinnerThread;
    
    public Spinner(TerminalConfigurator terminalConfigurator, ColorPalette colorPalette) {
        this.terminalConfigurator = terminalConfigurator;
        this.colorPalette = colorPalette;
    }
    
    /**
     * Start the spinner animation.
     * Returns the spinner thread.
     */
    public Thread start() {
        terminalConfigurator.out("\033[?25l"); // hide cursor while spinning

        Thread t = new Thread(() -> {
            String[] frames = { "⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏" };
            int frame = 0;
            long start = System.currentTimeMillis();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    long elapsed = System.currentTimeMillis() - start;
                    int seconds = (int) (elapsed / 1000);
                    terminalConfigurator.out("\r\033[2K");
                    terminalConfigurator.out(colorPalette.accent());
                    terminalConfigurator.out(frames[frame % frames.length]);
                    terminalConfigurator.out(colorPalette.reset());
                    terminalConfigurator.out(colorPalette.dim());
                    terminalConfigurator.out(" " + seconds + "s" + " - tokens: " + "131k"); // TODO: Calculate real tokens, ins, outs
                    terminalConfigurator.out(colorPalette.reset());
                    frame++;
                    Thread.sleep(80);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        t.setDaemon(true);
        t.start();
        this.spinnerThread = t;
        return t;
    }
    
    /**
     * Stop the spinner but leave its last frame on screen — the elapsed
     * "Ns" label freezes in place instead of being erased, so the user can
     * see how long the request took. Restores cursor visibility and moves
     * to a fresh line for whatever gets printed next (answer, "aborted",
     * etc.), which no longer needs to add its own leading newline.
     */
    public void stop() throws InterruptedException {
        if (spinnerThread != null) {
            spinnerThread.interrupt();
            spinnerThread.join(200);
            terminalConfigurator.out("\033[?25h\n");
        }
    }
    
    /**
     * Get the spinner thread.
     */
    public Thread getThread() {
        return spinnerThread;
    }
}
