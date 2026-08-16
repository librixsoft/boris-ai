package com.boris.cli.ui;

import java.io.FileInputStream;
import java.io.InputStream;
import java.io.PrintStream;

/**
 * Manages terminal configuration and raw mode operations.
 * Handles terminal setup, ANSI console installation, and stty mode switching.
 */
public class TerminalConfigurator {
    
    private static String savedTermSettings = null;
    private final InputStream tty;
    private final PrintStream output;
    
    public TerminalConfigurator() throws Exception {
        // Open /dev/tty directly — works even when stdin/stdout are redirected
        InputStream t;
        try { t = new FileInputStream("/dev/tty"); }
        catch (Exception e) { t = System.in; }
        this.tty = t;
        this.output = System.out;
    }
    
    /**
     * Install ANSI console support.
     */
    public void installAnsiConsole() {
        // No-op: ANSI sequences work directly with System.out
    }
    
    /**
     * Uninstall ANSI console support.
     */
    public void uninstallAnsiConsole() {
        // No-op
    }
    
    /**
     * Put terminal into raw mode: one char at a time, no echo.
     */
    public void sttyRaw() {
        try {
            // Save current settings so we can restore them exactly later
            Process p = new ProcessBuilder("sh", "-c", "stty -g </dev/tty")
                .redirectErrorStream(true)
                .start();
            savedTermSettings = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            new ProcessBuilder("sh", "-c", "stty -icanon -echo </dev/tty")
                .inheritIO().start().waitFor();
        } catch (Exception e) {
            System.err.println("[boris] Warning: could not set raw terminal mode: " + e.getMessage());
        }
    }
    
    /**
     * Restore the terminal to its saved state. Safe to call multiple times.
     */
    public void sttyRestore() {
        try {
            String cmd = (savedTermSettings != null && !savedTermSettings.isEmpty())
                ? "stty " + savedTermSettings + " </dev/tty"
                : "stty sane </dev/tty";
            new ProcessBuilder("sh", "-c", cmd)
                .inheritIO().start().waitFor();
        } catch (Exception ignored) {}
    }
    
    /**
     * Get the TTY input stream.
     */
    public InputStream getTty() {
        return tty;
    }
    
    /**
     * Close the terminal.
     */
    public void close() {
        // No-op with System.out
    }
    
    /**
     * Output text to the terminal.
     */
    public void out(String s) {
        output.print(s);
        output.flush();
    }

    // ─── ANSI utilities ────────────────────────────────────────────────

    /**
     * Returns [rows, cols] of the terminal by running "stty size /dev/tty".
     */
    public int[] getTerminalSize() {
        try {
            Process p = new ProcessBuilder("sh", "-c", "stty size </dev/tty")
                .redirectErrorStream(true)
                .start();
            String raw = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            String[] parts = raw.split("\\s+");
            if (parts.length >= 2) {
                return new int[] { Integer.parseInt(parts[0]), Integer.parseInt(parts[1]) };
            }
        } catch (Exception ignored) {
            // fallback
        }
        // Safe default for environments where stty fails
        return new int[] { 24, 80 };
    }

    /**
     * Set scroll region: DECSTBM via ESC[<top>;<bottom>r.
     * Rows are 1-based.
     */
    public void setScrollRegion(int top, int bottom) {
        out("\033[" + top + ";" + bottom + "r");
    }

    /**
     * Reset scroll region: ESC[r.
     */
    public void resetScrollRegion() {
        out("\033[r");
    }

    /**
     * Move cursor to absolute position: ESC[row;colH (1-based).
     */
    public void moveCursorTo(int row, int col) {
        out("\033[" + row + ";" + col + "H");
    }

    /**
     * Clear current line: ESC[2K.
     */
    public void clearCurrentLine() {
        out("\033[2K");
    }

    /**
     * Save cursor position: ESC7.
     */
    public void saveCursor() {
        out("\0337");
    }

    /**
     * Restore cursor position: ESC8.
     */
    public void restoreCursor() {
        out("\0338");
    }

    /**
     * Enable scroll lock (VT340): prevents the terminal window from
     * scrolling physically. Content written inside the scroll region
     * will not push the viewport beyond the scroll region boundaries.
     */
    public void enableScrollLock() {
        out("\033[?1007h");
    }

    /**
     * Disable scroll lock.
     */
    public void disableScrollLock() {
        out("\033[?1007l");
    }
}
