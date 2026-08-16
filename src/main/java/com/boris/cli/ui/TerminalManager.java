package com.boris.cli.ui;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.jansi.AnsiConsole;

import java.io.FileInputStream;
import java.io.InputStream;

/**
 * Manages terminal configuration and raw mode operations.
 */
public class TerminalManager {
    
    private static String savedTermSettings = null;
    private final Terminal terminal;
    private final InputStream tty;
    
    public TerminalManager() throws Exception {
        this.terminal = TerminalBuilder.builder().system(true).build();
        // Open /dev/tty directly — works even when stdin/stdout are redirected
        InputStream t;
        try { t = new FileInputStream("/dev/tty"); }
        catch (Exception e) { t = System.in; }
        this.tty = t;
    }
    
    /**
     * Install JLine's AnsiConsole so Ansi output works correctly.
     */
    public void installAnsiConsole() {
        AnsiConsole.systemInstall();
    }
    
    /**
     * Uninstall JLine's AnsiConsole.
     */
    public void uninstallAnsiConsole() {
        AnsiConsole.systemUninstall();
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
     * Get the terminal instance.
     */
    public Terminal getTerminal() {
        return terminal;
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
        try {
            terminal.close();
        } catch (Exception ignored) {}
    }
    
    /**
     * Output text to the terminal.
     */
    public void out(String s) {
        terminal.writer().print(s);
        terminal.writer().flush();
    }
}