package com.boris.cli.ui;

import java.io.IOException;
import java.io.InputStream;
import java.util.function.Consumer;

/**
 * Handles raw terminal input reading with history navigation support.
 * Processes user input including escape sequences, arrow keys, and special characters.
 * Notifies a BufferListener on buffer changes instead of writing directly.
 */
public class UserInputReader {

    private final InputStream tty;
    private final TerminalConfigurator terminalConfigurator;
    private final CommandHistory commandHistory;
    private Consumer<String> onBufferChanged;
    // Temporal buffer (thread-local, set before each readLine)
    private StringBuilder bufferRef;

    public UserInputReader(InputStream tty, TerminalConfigurator terminalConfigurator, CommandHistory commandHistory) {
        this.tty = tty;
        this.terminalConfigurator = terminalConfigurator;
        this.commandHistory = commandHistory;
    }

    /**
     * Register a callback that receives the current buffer content on each keystroke.
     */
    public void setOnBufferChanged(Consumer<String> callback) {
        this.onBufferChanged = callback;
    }
    
    /**
     * Check if input is available on the TTY.
     */
    public boolean available() throws IOException {
        return tty.available() > 0;
    }
    
    /**
     * Read a single character from TTY.
     */
    public int read() throws IOException {
        return tty.read();
    }
    
    /**
     * Reads one line of input from /dev/tty in raw mode.
     * Characters are echoed manually. Returns null if ESC (bare) or Ctrl+C is pressed.
     * Supports arrow-key history navigation (↑ previous, ↓ next).
     */
    public String readLine() throws IOException {
        bufferRef = new StringBuilder();
        while (true) {
            int ch = tty.read();
            if (ch < 0) return null;                // EOF

            if (ch == 0x03) {                       // Ctrl+C → exit
                terminalConfigurator.sttyRestore();
                System.exit(0);
            }

            // ── Escape sequence handling ───────────────────────────────────
            if (ch == 0x1B) {
                String result = handleEscapeSequence(bufferRef);
                if (result == null) {
                    return null;
                }
                continue;
            }

            if (ch == '\r' || ch == '\n') break;    // Enter → submit

            if (ch == 0x7F || ch == '\b') {         // Backspace / Del
                if (bufferRef.length() > 0) {
                    bufferRef.deleteCharAt(bufferRef.length() - 1);
                    notifyBufferChanged();
                }
                continue;
            }

            if (ch >= 32) {                         // Printable char — add to buffer
                bufferRef.append((char) ch);
                notifyBufferChanged();
            }
        }
        return bufferRef.toString();
    }

    private void notifyBufferChanged() {
        if (onBufferChanged != null && bufferRef != null) {
            onBufferChanged.accept(bufferRef.toString());
        }
    }
    
    /**
     * Handle escape sequences for arrow keys.
     * Returns null if bare ESC, otherwise continues reading.
     */
    private String handleEscapeSequence(StringBuilder sb) throws IOException {
        // Peek at the next byte (non-blocking with a tiny wait)
        int next = -1;
        long deadline = System.currentTimeMillis() + 50;
        while (System.currentTimeMillis() < deadline) {
            if (tty.available() > 0) { next = tty.read(); break; }
            try { Thread.sleep(5); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }
        if (next != '[' && next != 0x4F) {
            // Bare ESC (or unknown sequence) → cancel current line
            return null;
        }
        // Read the final byte of the CSI sequence
        int arrow = -1;
        deadline = System.currentTimeMillis() + 50;
        while (System.currentTimeMillis() < deadline) {
            if (tty.available() > 0) { arrow = tty.read(); break; }
            try { Thread.sleep(5); } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        if (arrow == 'A') {              // ↑ Arrow Up — go to previous command
            String previous = commandHistory.navigatePrevious();
            if (previous != null) {
                replaceCurrentLine(sb, previous);
            }
        } else if (arrow == 'B') {       // ↓ Arrow Down — go to next command
            String nextCommand = commandHistory.navigateNext();
            if (nextCommand != null) {
                replaceCurrentLine(sb, nextCommand);
            }
        }
        // Ignore other sequences (→, ←, F-keys, etc.)
        return "";
    }
    
    /**
     * Clears the current input on the terminal line and replaces it with newText.
     * Updates sb in-place to reflect the new content.
     */
    private void replaceCurrentLine(StringBuilder sb, String newText) {
        int currentLen = sb.length();
        if (currentLen > 0) {
            String blanks = " ".repeat(currentLen);
            terminalConfigurator.out("\b".repeat(currentLen) + blanks + "\b".repeat(currentLen));
        }
        sb.setLength(0);
        sb.append(newText);
        notifyBufferChanged();
        // Also echo to terminal for arrow-key navigation compatibility
        terminalConfigurator.out(newText);
    }
}
