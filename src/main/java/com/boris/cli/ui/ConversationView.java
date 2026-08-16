package com.boris.cli.ui;

/**
 * Renders the conversation history in the scroll region above the fixed input bar.
 * All output from ConversationView is confined to the scroll region.
 */
public class ConversationView {

    private static final String VIEW_CLASS = ConversationView.class.getName();

    private final TerminalConfigurator terminal;
    private final ColorPalette palette;
    private final MessageRenderer fallback;
    private boolean usingFallback;

    public ConversationView(TerminalConfigurator terminal, ColorPalette palette) {
        this.terminal = terminal;
        this.palette = palette;
        this.fallback = new MessageRenderer(terminal, palette);
        this.usingFallback = true;
    }

    /**
     * Print the start banner in the scroll region.
     */
    public void printBanner() {
        terminal.out("\n");
        terminal.out(palette.accent());
        terminal.out("Boris");
        terminal.out(palette.reset());
        terminal.out(palette.dim());
        terminal.out("  —  I am invincible\n");
        terminal.out("esc abort  ·  ctrl+c quit\n");
        terminal.out(palette.reset());
        terminal.out("\n");
    }

    /**
     * Start a Boris answer, labeled.
     */
    public void openAnswer() {
        terminal.out(palette.accent());
        terminal.out("\nBoris ");
        terminal.out(palette.dim());
        terminal.out("· ");
        terminal.out(palette.fg());
    }

    /**
     * Print a short status line (e.g. "aborted").
     */
    public void printStatus(String text) {
        terminal.out(palette.warn());
        terminal.out(text + "\n");
        terminal.out(palette.reset());
    }

    /**
     * Print a blank newline separator.
     */
    public void printNewline() {
        terminal.out(palette.reset());
        terminal.out("\n\n");
    }

    /**
     * Append a streaming chunk — the core of rendering streamed responses.
     */
    public void appendChunk(String chunk) {
        terminal.out(chunk);
    }
}
