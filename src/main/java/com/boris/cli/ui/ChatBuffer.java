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
    private StringBuilder currentChunkBuffer;

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
        this.currentChunkBuffer = new StringBuilder();
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
        terminal.out(palette.accent());
        terminal.out("Boris");
        terminal.out(palette.reset());
        terminal.out(palette.dim());
        terminal.out("  —  I am invincible");
        terminal.out(palette.reset());
        
        terminal.moveCursorTo(bannerStartRow + 1, 1);
        terminal.clearCurrentLine();
        terminal.out(palette.dim());
        terminal.out("esc abort  ·  ctrl+c quit");
        terminal.out(palette.reset());
        
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
        // Flush any remaining content from previous answer
        flushChunkBuffer();
        
        // Add a blank line for separation
        messageLines.add("");
        totalContentHeight++;
        
        // Add the Boris label
        String answerHeader = palette.accent() + "Boris " + palette.dim() + "· " + palette.fg();
        messageLines.add(answerHeader);
        totalContentHeight++;
        
        scrollToBottom();
    }

    /**
     * Append a chunk to the current answer with proper text wrapping.
     */
    public void appendChunk(String chunk) {
        if (chunk == null || chunk.isEmpty()) {
            return;
        }
        
        // Add chunk to buffer
        currentChunkBuffer.append(chunk);
        
        // Check if we have complete lines or enough text to wrap
        String bufferContent = currentChunkBuffer.toString();
        
        // If buffer contains newlines, process complete lines
        if (bufferContent.contains("\n")) {
            String[] lines = bufferContent.split("\\n", -1);
            
            // Process all complete lines (except the last one if it doesn't end with \n)
            for (int i = 0; i < lines.length - 1; i++) {
                String line = lines[i];
                if (!line.isEmpty()) {
                    List<String> wrappedLines = wrapText(line, getTerminalWidth());
                    for (String wrappedLine : wrappedLines) {
                        messageLines.add(wrappedLine);
                        totalContentHeight++;
                    }
                } else {
                    // Empty line - add as blank line
                    messageLines.add("");
                    totalContentHeight++;
                }
            }
            
            // Keep the last (possibly incomplete) line in the buffer
            currentChunkBuffer = new StringBuilder(lines[lines.length - 1]);
            scrollToBottom();
        } else {
            // No newlines yet - check if buffer is long enough to wrap
            String plainText = stripAnsiCodes(bufferContent);
            int terminalWidth = getTerminalWidth();
            
            if (plainText.length() >= terminalWidth) {
                // Buffer is long enough to wrap
                List<String> wrappedLines = wrapText(bufferContent, terminalWidth);
                
                // Add all but the last wrapped line (keep it in buffer for more content)
                for (int i = 0; i < wrappedLines.size() - 1; i++) {
                    messageLines.add(wrappedLines.get(i));
                    totalContentHeight++;
                }
                
                // Keep the last wrapped line in buffer
                currentChunkBuffer = new StringBuilder(wrappedLines.get(wrappedLines.size() - 1));
                scrollToBottom();
            }
        }
    }

    /**
     * Flush any remaining content in the chunk buffer.
     * Call this when streaming is complete to ensure all content is displayed.
     */
    public void flushChunkBuffer() {
        if (currentChunkBuffer.length() > 0) {
            String remaining = currentChunkBuffer.toString();
            if (!remaining.isEmpty()) {
                List<String> wrappedLines = wrapText(remaining, getTerminalWidth());
                for (String wrappedLine : wrappedLines) {
                    messageLines.add(wrappedLine);
                    totalContentHeight++;
                }
            }
            currentChunkBuffer = new StringBuilder();
            scrollToBottom();
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
        String statusLine = palette.warn() + text + palette.reset();
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
        currentChunkBuffer = new StringBuilder();
        render();
    }
}