package com.boris.cli.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * ChatBuffer — manages scrollable chat content between banner and input bar.
 * Keeps the banner fixed at the top and maintains a scrollable buffer for
 * messages below it.
 */
public class ChatBuffer {

    private final TerminalConfigurator terminal;
    private final ColorPalette palette;
    private final int bannerLines;
    private final int inputBarHeight;
    
    private List<String> messageLines;
    private int scrollPosition;
    private int totalContentHeight;
    private int maxScrollPosition;
    private int bufferHeight;

    /**
     * Create a new ChatBuffer.
     * 
     * @param terminal the terminal configurator
     * @param palette the color palette
     * @param bannerLines number of lines the banner occupies
     * @param inputBarHeight height of the input bar in rows
     */
    public ChatBuffer(TerminalConfigurator terminal, ColorPalette palette, int bannerLines, int inputBarHeight) {
        this.terminal = terminal;
        this.palette = palette;
        this.bannerLines = bannerLines;
        this.inputBarHeight = inputBarHeight;
        this.messageLines = new ArrayList<>();
        this.scrollPosition = 0;
        this.totalContentHeight = 0;
        this.maxScrollPosition = 0;
        this.bufferHeight = 0;
    }

    /**
     * Initialize the buffer with the terminal size.
     * Must be called before rendering.
     */
    public void initialize() {
        int[] size = terminal.getTerminalSize();
        int rows = size[0];
        bufferHeight = rows - bannerLines - inputBarHeight;
        if (bufferHeight < 1) {
            bufferHeight = 1;
        }
        maxScrollPosition = Math.max(0, totalContentHeight - bufferHeight);
    }

    /**
     * Add a line to the chat buffer and scroll if necessary.
     */
    public void addLine(String line) {
        messageLines.add(line);
        totalContentHeight++;
        ensureVisible();
        render();
    }

    /**
     * Add multiple lines and scroll to bottom.
     */
    public void addLines(List<String> lines) {
        for (String line : lines) {
            addLine(line);
        }
    }

    /**
     * Ensure the newest content is visible (scroll to bottom).
     */
    public void scrollToBottom() {
        scrollPosition = Math.max(0, totalContentHeight - bufferHeight);
        render();
    }

    /**
     * Scroll up by n lines.
     */
    public void scrollUp(int lines) {
        scrollPosition = Math.max(0, scrollPosition - lines);
        render();
    }

    /**
     * Scroll down by n lines.
     */
    public void scrollDown(int lines) {
        scrollPosition = Math.min(maxScrollPosition, scrollPosition + lines);
        render();
    }

    /**
     * Ensure current scroll position shows the latest content.
     */
    private void ensureVisible() {
        if (scrollPosition > maxScrollPosition) {
            scrollPosition = maxScrollPosition;
        }
    }

    /**
     * Render the visible portion of the buffer.
     */
    public void render() {
        int[] size = terminal.getTerminalSize();
        int rows = size[0];
        
        int contentStartRow = bannerLines + 1;
        int contentEndRow = rows - inputBarHeight;
        int visibleLines = contentEndRow - contentStartRow + 1;
        
        if (visibleLines < 1) {
            visibleLines = 1;
        }
        
        maxScrollPosition = Math.max(0, totalContentHeight - visibleLines);
        ensureVisible();
        
        for (int i = 0; i < visibleLines; i++) {
            int lineIndex = scrollPosition + i;
            int row = contentStartRow + i;
            
            terminal.moveCursorTo(row, 1);
            terminal.clearCurrentLine();
            
            if (lineIndex < totalContentHeight) {
                String line = messageLines.get(lineIndex);
                terminal.out(line);
            }
        }
        
        terminal.moveCursorTo(rows, 1);
    }

    /**
     * Print banner lines (fixed at top of buffer).
     * This should be called once at startup to display the initial banner.
     * The banner is printed directly to terminal and not stored in messageLines.
     */
    public void printBanner() {
        int[] size = terminal.getTerminalSize();
        int rows = size[0];
        int contentStartRow = bannerLines + 1;

        terminal.moveCursorTo(contentStartRow, 1);
        terminal.clearCurrentLine();
        terminal.out(palette.accent());
        terminal.out("Boris");
        terminal.out(palette.reset());
        terminal.out(palette.dim());
        terminal.out("  —  I am invincible\n");
        terminal.out("esc abort  ·  ctrl+c quit\n");
        terminal.out(palette.reset());
        terminal.out("\n");

        totalContentHeight = 0;
        messageLines.clear();
        scrollPosition = 0;
        maxScrollPosition = 0;
        render();
    }

    /**
     * Open a new answer block with Boris label.
     */
    public void openAnswer() {
        terminal.out(palette.accent());
        terminal.out("\nBoris ");
        terminal.out(palette.dim());
        terminal.out("· ");
        terminal.out(palette.fg());
        
        String answerHeader = "\nBoris · ";
        for (char c : answerHeader.toCharArray()) {
            if (c == '\n') {
                totalContentHeight++;
            }
        }
        messageLines.add(answerHeader);
        scrollToBottom();
    }

    /**
     * Append a chunk to the current answer.
     */
    public void appendChunk(String chunk) {
        String[] lines = chunk.split("\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            if (i > 0 || !chunk.startsWith("\n")) {
                if (!line.isEmpty() || i < lines.length - 1) {
                    addLine(line);
                }
            }
        }
    }

    /**
     * Print status message.
     */
    public void printStatus(String text) {
        String statusLine = text + "\n";
        addLine(statusLine);
    }

    /**
     * Print a blank newline separator.
     */
    public void printNewline() {
        addLine("\n");
    }

    /**
     * Get current scroll position.
     */
    public int getScrollPosition() {
        return scrollPosition;
    }

    /**
     * Get buffer height.
     */
    public int getBufferHeight() {
        return bufferHeight;
    }

    /**
     * Get total content height.
     */
    public int getTotalContentHeight() {
        return totalContentHeight;
    }

    /**
     * Check if content is scrolled to bottom.
     */
    public boolean isScrolledToBottom() {
        return scrollPosition >= maxScrollPosition;
    }

    /**
     * Clear the buffer (keep banner).
     */
    public void clear() {
        messageLines.clear();
        totalContentHeight = bannerLines;
        scrollPosition = 0;
        render();
    }
}