package com.boris.cli.ui;

/**
 * Minimal spinner animation: a single braille dot cycling in the accent color,
 * followed by a quiet elapsed-time label. No background block, no box —
 * just one moving glyph on an otherwise empty line.
 */
public class Spinner {
    
    private final TerminalManager terminalManager;
    private final ColorPalette colorPalette;
    private Thread spinnerThread;
    
    public Spinner(TerminalManager terminalManager, ColorPalette colorPalette) {
        this.terminalManager = terminalManager;
        this.colorPalette = colorPalette;
    }
    
    /**
     * Start the spinner animation.
     * Returns the spinner thread.
     */
    public Thread start() {
        terminalManager.out("\033[?25l"); // hide cursor while spinning

        Thread t = new Thread(() -> {
            String[] frames = { "⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏" };
            int frame = 0;
            long start = System.currentTimeMillis();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    long elapsed = System.currentTimeMillis() - start;
                    int seconds = (int) (elapsed / 1000);
                    terminalManager.out("\r\033[2K");
                    terminalManager.out(colorPalette.accent());
                    terminalManager.out(frames[frame % frames.length]);
                    terminalManager.out(colorPalette.reset());
                    terminalManager.out(colorPalette.dim());
                    terminalManager.out(" " + seconds + "s" + " - tokens: " + "131k"); // TODO: Calculate real tokens, ins, outs
                    terminalManager.out(colorPalette.reset());
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
            terminalManager.out("\033[?25h\n");
        }
    }
    
    /**
     * Get the spinner thread.
     */
    public Thread getThread() {
        return spinnerThread;
    }
}