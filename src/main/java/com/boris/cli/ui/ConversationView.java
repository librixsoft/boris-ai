package com.boris.cli.ui;

/**
 * Renders the conversation history in the scroll region above the fixed input bar.
 * All output from ConversationView is confined to the scroll region.
 */
public class ConversationView {

    private static final String VIEW_CLASS = ConversationView.class.getName();
    private static final int BANNER_LINES = 4;

    private final TerminalConfigurator terminal;
    private final ColorPalette palette;
    private ChatBuffer chatBuffer;

    public ConversationView(TerminalConfigurator terminal, ColorPalette palette) {
        this.terminal = terminal;
        this.palette = palette;
    }

    /**
     * Initialize the conversation view with input bar height.
     */
    public void initialize(int inputBarHeight) {
        this.chatBuffer = new ChatBuffer(terminal, palette, BANNER_LINES, inputBarHeight);
        chatBuffer.initialize();
    }

    /**
     * Print the start banner in the scroll region.
     */
    public void printBanner() {
        if (chatBuffer != null) {
            chatBuffer.printBanner();
        } else {
            fallbackPrintBanner();
        }
    }

    private void fallbackPrintBanner() {
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
        if (chatBuffer != null) {
            chatBuffer.openAnswer();
        } else {
            terminal.out(palette.accent());
            terminal.out("\nBoris ");
            terminal.out(palette.dim());
            terminal.out("· ");
            terminal.out(palette.fg());
        }
    }

    /**
     * Print a short status line (e.g. "aborted").
     */
    public void printStatus(String text) {
        if (chatBuffer != null) {
            chatBuffer.printStatus(text);
        } else {
            terminal.out(palette.warn());
            terminal.out(text + "\n");
            terminal.out(palette.reset());
        }
    }

    /**
     * Print a blank newline separator.
     */
    public void printNewline() {
        if (chatBuffer != null) {
            chatBuffer.printNewline();
        } else {
            terminal.out(palette.reset());
            terminal.out("\n\n");
        }
    }

    /**
     * Append a streaming chunk — the core of rendering streamed responses.
     */
    public void appendChunk(String chunk) {
        if (chatBuffer != null) {
            chatBuffer.appendChunk(chunk);
        } else {
            terminal.out(chunk);
        }
    }

    /**
     * Scroll to bottom of chat buffer.
     */
    public void scrollToBottom() {
        if (chatBuffer != null) {
            chatBuffer.scrollToBottom();
        }
    }

    /**
     * Scroll up by n lines.
     */
    public void scrollUp(int lines) {
        if (chatBuffer != null) {
            chatBuffer.scrollUp(lines);
        }
    }

    /**
     * Scroll down by n lines.
     */
    public void scrollDown(int lines) {
        if (chatBuffer != null) {
            chatBuffer.scrollDown(lines);
        }
    }

    /**
     * Check if content is scrolled to bottom.
     */
    public boolean isScrolledToBottom() {
        return chatBuffer != null && chatBuffer.isScrolledToBottom();
    }

    /**
     * Get current scroll position.
     */
    public int getScrollPosition() {
        return chatBuffer != null ? chatBuffer.getScrollPosition() : 0;
    }

    /**
     * Clear the chat buffer.
     */
    public void clear() {
        if (chatBuffer != null) {
            chatBuffer.clear();
        }
    }
}