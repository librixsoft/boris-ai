package com.boris.cli.ui;

/**
 * Renders the fixed input bar at the bottom of the terminal.
 * Always paints on the last 2 rows, independent of scroll region.
 * 
 * IMPORTANT: STRICT PROHIBITION - Manual ANSI escape sequences are NOT ALLOWED.
 * All terminal operations MUST use JLine3 APIs through TerminalConfigurator.
 * Manual ANSI sequences interfere with JLine3's internal state management and break UI rendering.
 */
public class InputBar {

    private static final int ROW_HEIGHT = 2;

    private final TerminalConfigurator terminal;
    private final ColorPalette palette;

    public InputBar(TerminalConfigurator terminal, ColorPalette palette) {
        this.terminal = terminal;
        this.palette = palette;
    }

    /**
     * Height of the input bar in terminal rows.
     */
    public int getHeight() {
        return ROW_HEIGHT;
    }

    /**
     * Render the prompt bar at the bottom of the terminal.
     *
     * @param currentBuffer  text the user is currently typing
     * @param spinnerFrame   optional spinner frame text (nullable). When present it is drawn
     *                       on the same line as the prompt; the spinner finishes the line and
     *                       the prompt glyph moves to the right of it.
     */
    public void render(String currentBuffer, String spinnerFrame) {
        int terminalRows = rows();
        int terminalCols = cols();
        
        // Ensure we're always rendering within terminal bounds
        int spinnerLineRow = Math.max(1, terminalRows - ROW_HEIGHT + 1);
        int promptLineRow  = Math.max(1, terminalRows);
        
        // --- Spinner line ---
        terminal.moveCursorTo(spinnerLineRow, 1);
        terminal.clearCurrentLine();
        if (spinnerFrame != null && !spinnerFrame.isEmpty()) {
            terminal.out(palette.dimStr());
            terminal.out(spinnerFrame);
            terminal.out(palette.resetStr());
        }

        // --- Prompt line ---
        terminal.moveCursorTo(promptLineRow, 1);
        terminal.clearCurrentLine();
        terminal.out(palette.accentStr());
        terminal.out("› ");
        terminal.out(palette.fgStr());
        int bufLen = currentBuffer == null ? 0 : currentBuffer.length();
        if (currentBuffer != null) {
            // Truncate if buffer exceeds terminal width (minus prompt chars)
            int maxBufferLen = Math.max(0, terminalCols - 3); // 2 for "› " + 1 for cursor
            if (bufLen > maxBufferLen) {
                terminal.out(currentBuffer.substring(0, maxBufferLen));
                bufLen = maxBufferLen;
            } else {
                terminal.out(currentBuffer);
            }
        }
        int cursorCol = 2 + bufLen;
        terminal.moveCursorTo(promptLineRow, Math.min(cursorCol, terminalCols));
        terminal.out(palette.resetStr());
    }

    /**
     * Render without a spinner indicator.
     */
    public void render(String currentBuffer) {
        render(currentBuffer, null);
    }

    /**
     * Clear both input bar lines (used before starting spinner or on exit).
     */
    public void clear() {
        int terminalRows = rows();
        for (int i = Math.max(1, terminalRows - ROW_HEIGHT + 1); i <= terminalRows; i++) {
            terminal.moveCursorTo(i, 1);
            terminal.clearCurrentLine();
        }
    }

    private int rows() {
        return terminal.getTerminalSize()[0];
    }

    private int cols() {
        return terminal.getTerminalSize()[1];
    }
}
