package com.boris.cli.ui;

/**
 * Renders the conversation history in the scroll region above the fixed input bar.
 * All output from ConversationView is confined to the scroll region.
 * 
 * IMPORTANT: STRICT PROHIBITION - Manual ANSI escape sequences are NOT ALLOWED.
 * All terminal operations MUST use JLine3 APIs through TerminalConfigurator.
 * Manual ANSI sequences interfere with JLine3's internal state management and break UI rendering.
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
        terminal.out(palette.accentStr());
        terminal.out("Boris");
        terminal.out(palette.resetStr());
        terminal.out(palette.dimStr());
        terminal.out("  —  I am invincible\n");
        terminal.out("esc abort  ·  ctrl+c quit\n");
        terminal.out(palette.resetStr());
        terminal.out("\n");
    }

    /**
     * Start a Boris answer, labeled.
     */
    public void openAnswer() {
        if (chatBuffer != null) {
            chatBuffer.openAnswer();
        } else {
            terminal.out(palette.accentStr());
            terminal.out("\nBoris ");
            terminal.out(palette.dimStr());
            terminal.out("· ");
            terminal.out(palette.fgStr());
            terminal.out("\n");
        }
    }

    /**
     * Print a user question, labeled.
     */
    public void openQuestion(String text) {
        if (chatBuffer != null) {
            chatBuffer.openQuestion(text);
        } else {
            terminal.out(palette.userStr());
            terminal.out("You " + palette.dimStr() + "· " + palette.fgStr());
            terminal.out(text);
            terminal.out(palette.resetStr());
            terminal.out("\n");
        }
    }

    /**
     * Print a short status line (e.g. "aborted").
     */
    public void printStatus(String text) {
        if (chatBuffer != null) {
            chatBuffer.printStatus(text);
        } else {
            terminal.out(palette.warnStr());
            terminal.out(text);
            terminal.out(palette.resetStr());
            terminal.out("\n");
        }
    }

    /**
     * Print a blank newline separator.
     */
    public void printNewline() {
        if (chatBuffer != null) {
            chatBuffer.printNewline();
        } else {
            terminal.out(palette.resetStr());
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

    /**
     * Finish the current line (call when streaming completes).
     */
    public void finishCurrentLine() {
        if (chatBuffer != null) {
            chatBuffer.finishCurrentLine();
        }
    }
}