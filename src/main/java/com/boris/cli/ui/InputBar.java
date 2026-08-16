package com.boris.cli.ui;

/**
 * Renders the fixed input bar at the bottom of the terminal.
 * Always paints on the last 2 rows, independent of scroll region.
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
        terminal.saveCursor();

        int spinnerLineRow = rows() - ROW_HEIGHT;
        int promptLineRow  = rows() - 1;
        int hintLineRow    = rows();

        // --- Spinner line ---
        terminal.moveCursorTo(spinnerLineRow, 1);
        terminal.clearCurrentLine();
        if (spinnerFrame != null && !spinnerFrame.isEmpty()) {
            terminal.out(palette.dim());
            terminal.out(spinnerFrame);
            terminal.out(palette.reset());
        }

        // --- Prompt line ---
        terminal.moveCursorTo(promptLineRow, 1);
        terminal.clearCurrentLine();
        terminal.out(palette.accent());
        terminal.out("› ");
        terminal.out(palette.fg());
        int bufLen = currentBuffer == null ? 0 : currentBuffer.length();
        if (currentBuffer != null) {
            terminal.out(currentBuffer);
        }
        int cursorCol = 2 + bufLen;
        terminal.moveCursorTo(promptLineRow, Math.min(cursorCol, cols()));
        terminal.out(palette.reset());

        // --- Hint line ---
        if (cols() >= 30) {
            terminal.moveCursorTo(hintLineRow, 1);
            terminal.clearCurrentLine();
            terminal.out(palette.dim());
            terminal.out("esc abort  ·  ctrl+c quit");
            terminal.out(palette.reset());
        }

        terminal.restoreCursor();
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
        for (int i = rows() - ROW_HEIGHT; i <= rows(); i++) {
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
