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
}
