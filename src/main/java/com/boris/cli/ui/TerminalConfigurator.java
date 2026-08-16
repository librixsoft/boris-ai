package com.boris.cli.ui;

import org.jline.terminal.Attributes;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.InfoCmp;
import org.jline.utils.AttributedString;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;

import java.io.IOException;
import java.io.PrintStream;
import java.io.InputStream;

/**
 * Manages terminal configuration using JLine3 library.
 * Handles terminal setup, alternate screen buffer, and raw mode operations.
 * 
 * IMPORTANT: STRICT PROHIBITION - Manual ANSI escape sequences are NOT ALLOWED.
 * All terminal operations MUST use JLine3 APIs (InfoCmp.Capability, terminal.puts(), etc.)
 * Manual ANSI sequences interfere with JLine3's internal state management and break UI rendering.
 */
public class TerminalConfigurator {
    
    private static String savedTermSettings = null;
    private volatile boolean closed = false;
    private Terminal terminal;
    private Attributes originalAttributes;
    private final PrintStream output;
    
    public TerminalConfigurator() throws Exception {
        // Build JLine3 terminal
        this.terminal = TerminalBuilder.builder()
            .system(true)
            .build();
        this.output = System.out;
    }
    

    
    /**
     * Put terminal into raw mode: one char at a time, no echo.
     */
    public void sttyRaw() {
        try {
            originalAttributes = terminal.enterRawMode();
        } catch (Exception e) {
            System.err.println("[boris] Warning: could not set raw terminal mode: " + e.getMessage());
        }
    }
    
    /**
     * Restore the terminal to its saved state.
     */
    public void sttyRestore() {
        try {
            if (terminal != null) {
                terminal.close();
            }
        } catch (Exception ignored) {}
    }
    
    /**
     * Get the TTY input stream.
     */
    public InputStream getTty() {
        return terminal != null ? terminal.input() : System.in;
    }
    
    /**
     * Close the terminal: reset scroll region, restore original attributes, then close.
     * Idempotent.
     */
    public void close() {
        if (closed) return;
        closed = true;
        try {
            resetScrollRegion();
        } catch (Exception ignored) {}
        try {
            if (terminal != null && originalAttributes != null) {
                terminal.setAttributes(originalAttributes);
            }
        } catch (Exception ignored) {}
        try {
            if (terminal != null) {
                terminal.close();
            }
        } catch (Exception ignored) {}
    }
    
    /**
     * Output text to the terminal using JLine3 writer.
     */
    public void out(String s) {
        if (terminal != null) {
            terminal.writer().print(s);
            terminal.writer().flush();
        } else {
            output.print(s);
            output.flush();
        }
    }

    // ─── ANSI utilities ────────────────────────────────────────────────

    /**
     * Returns [rows, cols] of the terminal.
     */
    public int[] getTerminalSize() {
        if (terminal != null) {
            return new int[] { terminal.getHeight(), terminal.getWidth() };
        }
        return new int[] { 24, 80 };
    }

    /**
     * Set scroll region using JLine3 capabilities.
     */
    public void setScrollRegion(int top, int bottom) {
        if (terminal != null) {
            terminal.puts(InfoCmp.Capability.change_scroll_region, top, bottom);
            terminal.writer().flush();
        }
    }

    /**
     * Reset scroll region using JLine3 capabilities.
     */
    public void resetScrollRegion() {
        if (terminal != null) {
            terminal.puts(InfoCmp.Capability.change_scroll_region, 1, terminal.getHeight());
            terminal.writer().flush();
        }
    }

    /**
     * Move cursor to absolute position using JLine3.
     */
    public void moveCursorTo(int row, int col) {
        if (terminal != null) {
            terminal.puts(InfoCmp.Capability.cursor_address, row, col);
            terminal.writer().flush();
        }
    }

    /**
     * Clear current line using JLine3.
     */
    public void clearCurrentLine() {
        if (terminal != null) {
            terminal.puts(InfoCmp.Capability.clr_eol);
            terminal.writer().flush();
        }
    }

    /**
     * Save cursor position using JLine3.
     */
    public void saveCursor() {
        if (terminal != null) {
            terminal.puts(InfoCmp.Capability.save_cursor);
            terminal.writer().flush();
        }
    }

    /**
     * Restore cursor position using JLine3.
     */
    public void restoreCursor() {
        if (terminal != null) {
            terminal.puts(InfoCmp.Capability.restore_cursor);
            terminal.writer().flush();
        }
    }


}
