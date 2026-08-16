package com.boris.cli.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * ChatBuffer — manages scrollable chat content between banner and input bar.
 * Keeps the banner fixed at the top and maintains a scrollable buffer for
 * messages below it.
 * 
 * IMPORTANT: STRICT PROHIBITION - Manual ANSI escape sequences are NOT ALLOWED.
 * All terminal operations MUST use JLine3 APIs through TerminalConfigurator.
 * Manual ANSI sequences interfere with JLine3's internal state management and break UI rendering.
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
    private StringBuilder currentLine;

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
        this.currentLine = new StringBuilder();
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
     * Add multiple lines and scroll to bottom.
     */
    public void addLines(List<String> lines) {
        for (String line : lines) {
            messageLines.add(line);
            totalContentHeight++;
        }
        scrollToBottom();
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
            
            if (lineIndex < messageLines.size()) {
                String line = messageLines.get(lineIndex);
                terminal.out(line);
            }
        }
        
        // Move cursor to input bar area (below scroll region)
        terminal.moveCursorTo(rows - inputBarHeight + 1, 1);
    }

    /**
     * Print banner lines (fixed at top of buffer).
     * This should be called once at startup to display the initial banner.
     * The banner is printed directly to terminal and not stored in messageLines.
     */
    public void printBanner() {
        int[] size = terminal.getTerminalSize();
        int rows = size[0];
        int bannerStartRow = 1;

        // Print banner in the non-scrolling area (top of terminal)
        terminal.moveCursorTo(bannerStartRow, 1);
        terminal.clearCurrentLine();
        terminal.out(palette.accentStr());
        terminal.out("Boris");
        terminal.out(palette.resetStr());
        terminal.out(palette.dimStr());
        terminal.out("  —  I am invincible");
        terminal.out(palette.resetStr());
        
        terminal.moveCursorTo(bannerStartRow + 1, 1);
        terminal.clearCurrentLine();
        terminal.out(palette.dimStr());
        terminal.out("esc abort  ·  ctrl+c quit");
        terminal.out(palette.resetStr());
        
        terminal.moveCursorTo(bannerStartRow + 2, 1);
        terminal.clearCurrentLine();
        
        terminal.moveCursorTo(bannerStartRow + 3, 1);
        terminal.clearCurrentLine();

        totalContentHeight = 0;
        messageLines.clear();
        scrollPosition = 0;
        maxScrollPosition = 0;
        
        // Move cursor to start of scroll region
        terminal.moveCursorTo(bannerLines + 1, 1);
    }

    /**
     * Open a new answer block with Boris label.
     */
    public void openAnswer() {
        // Finish any current line from previous answer
        if (currentLine.length() > 0) {
            messageLines.add(currentLine.toString());
            totalContentHeight++;
            currentLine = new StringBuilder();
        }
        
        // Add a blank line for separation
        messageLines.add("");
        totalContentHeight++;
        
        // Add the Boris label
        String answerHeader = palette.accentStr() + "Boris " + palette.dimStr() + "· " + palette.fgStr();
        messageLines.add(answerHeader);
        totalContentHeight++;
        
        scrollToBottom();
    }

    /**
     * Print a user question block with label.
     */
    public void openQuestion(String text) {
        // Finish any current line from previous answer
        if (currentLine.length() > 0) {
            messageLines.add(currentLine.toString());
            totalContentHeight++;
            currentLine = new StringBuilder();
        }
        
        // Add the user label
        String questionHeader = palette.userStr() + "You " + palette.dimStr() + "· " + palette.fgStr();
        messageLines.add(questionHeader);
        totalContentHeight++;
        
        // Add the user text
        messageLines.add(text);
        totalContentHeight++;
        
        scrollToBottom();
    }

    /**
     * Finish the current line (call when streaming completes).
     */
    public void finishCurrentLine() {
        if (currentLine.length() > 0) {
            messageLines.add(currentLine.toString());
            totalContentHeight++;
            currentLine = new StringBuilder();
            scrollToBottom();
        }
    }

    /**
     * Append a chunk to the current answer with immediate character-by-character display.
     */
    public void appendChunk(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        
        int terminalWidth = terminal.getTerminalSize()[1];
        
        // Process each character in the chunk for immediate display
        for (int i = 0; i < chunk.length(); i++) {
            char c = chunk.charAt(i);
            
            if (c == '\n') {
                // Newline - finish current line and start new one
                if (currentLine.length() > 0) {
                    messageLines.add(currentLine.toString());
                    totalContentHeight++;
                    currentLine = new StringBuilder();
                } else {
                    // Empty line
                    messageLines.add("");
                    totalContentHeight++;
                }
                scrollToBottom();
            } else {
                // Regular character - add to current line
                currentLine.append(c);
                
                // Check if current line needs wrapping
                String plainLine = stripAnsiCodes(currentLine.toString());
                if (plainLine.length() >= terminalWidth) {
                    // Line is full - wrap it
                    messageLines.add(currentLine.toString());
                    totalContentHeight++;
                    currentLine = new StringBuilder();
                    scrollToBottom();
                } else {
                    // Display current partial line immediately for typewriter effect
                    renderCurrentLine();
                }
            }
        }
    }
    
    /**
     * Render the current partial line immediately for typewriter effect.
     */
    private void renderCurrentLine() {
        if (currentLine.length() == 0) {
            return;
        }
        
        int[] size = terminal.getTerminalSize();
        int rows = size[0];
        int contentStartRow = bannerLines + 1;
        int contentEndRow = rows - inputBarHeight;
        
        // Get the position where the current line should be displayed
        int currentLineRow = contentStartRow + messageLines.size();
        
        // Only render if within visible area
        if (currentLineRow <= contentEndRow) {
            terminal.moveCursorTo(currentLineRow, 1);
            terminal.clearCurrentLine();
            terminal.out(currentLine.toString());
            // Move cursor back to input area after rendering
            terminal.moveCursorTo(rows - inputBarHeight + 1, 1);
        }
    }

    /**
     * Wrap text to fit within the specified width, breaking at word boundaries when possible.
     */
    private List<String> wrapText(String text, int width) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            result.add("");
            return result;
        }

        // Remove ANSI color codes for width calculation
        String plainText = stripAnsiCodes(text);
        
        if (plainText.length() <= width) {
            result.add(text);
            return result;
        }

        // Simple word-aware wrapping
        String[] words = text.split("(?<=\\s)"); // Split at whitespace, keep the whitespace
        
        StringBuilder currentLine = new StringBuilder();
        StringBuilder currentPlainLine = new StringBuilder();
        
        for (String word : words) {
            String plainWord = stripAnsiCodes(word);
            
            // Skip empty words
            if (plainWord.isEmpty()) {
                continue;
            }
            
            // Check if adding this word would exceed width
            if (currentPlainLine.length() + plainWord.length() <= width) {
                currentLine.append(word);
                currentPlainLine.append(plainWord);
            } else {
                // Current line is full, add it to result
                if (currentLine.length() > 0) {
                    result.add(currentLine.toString().trim());
                }
                
                // Start new line with this word
                currentLine = new StringBuilder(word);
                currentPlainLine = new StringBuilder(plainWord);
                
                // Handle single word longer than width
                if (plainWord.length() > width) {
                    while (currentPlainLine.length() > width) {
                        int splitPoint = Math.min(width, currentPlainLine.length());
                        result.add(currentLine.substring(0, splitPoint));
                        currentLine.delete(0, splitPoint);
                        currentPlainLine.delete(0, splitPoint);
                    }
                }
            }
        }
        
        // Add remaining content
        if (currentLine.length() > 0) {
            result.add(currentLine.toString().trim());
        }
        
        return result;
    }

    /**
     * Remove ANSI escape codes from text for width calculation.
     */
    private String stripAnsiCodes(String text) {
        return text.replaceAll("\\033\\[[0-9;]*m", "");
    }

    /**
     * Find a safe split point in long text.
     */
    private int findSplitPoint(String text, int maxWidth) {
        if (text.length() <= maxWidth) {
            return text.length();
        }
        return maxWidth;
    }

    /**
     * Get terminal width for text wrapping.
     */
    private int getTerminalWidth() {
        int[] size = terminal.getTerminalSize();
        return size[1];
    }

    /**
     * Print status message.
     */
    public void printStatus(String text) {
        // Add a blank line for separation
        messageLines.add("");
        totalContentHeight++;
        
        // Add the status message
        String statusLine = palette.warnStr() + text + palette.resetStr();
        messageLines.add(statusLine);
        totalContentHeight++;
        
        scrollToBottom();
    }

    /**
     * Print a blank newline separator.
     */
    public void printNewline() {
        messageLines.add("");
        totalContentHeight++;
        scrollToBottom();
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
        currentLine = new StringBuilder();
        render();
    }
}