package com.boris.cli;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.jansi.Ansi;
import org.jline.jansi.AnsiConsole;

import com.boris.chat.ChatService;
import com.boris.task.TaskAborter;

/**
 * BorisUI — minimal terminal front-end, in the spirit of modern coding-agent
 * CLIs (Claude Code / opencode): a plain-text name + slogan on startup (no
 * ASCII art), a boxed single-line input prompt, a "Boris ·" label on
 * responses, and a quiet dot-spinner while working.
 */
public class BorisUI {

    // ── Palette (single accent, everything else neutral) ───────────────────
    private static final int[] ACCENT = { 209, 122, 92 };   // prompt glyph / brand dot
    private static final int[] DIM    = { 118, 118, 124 };  // secondary / help text
    private static final int[] FG     = { 225, 225, 228 };  // response text
    private static final int[] WARN   = { 209, 160, 100 };  // aborted
    private static final int[] BORDER = { 235, 235, 238 };  // near-white, used for the input box frame

    private static String savedTermSettings = null;
    private Terminal terminal;

    // ── Command history (like zsh / bash) ──────────────────────────────────
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;   // -1 = not navigating history

    // Width used for the box currently on screen. Fixed once per prompt so
    // the top/side/bottom borders always agree, even if the terminal is
    // resized mid-input.
    private int lastBoxWidth = 0;

    private final ChatService chatService;
    private final TaskAborter taskAborter;
    private final InputStream tty;

    public BorisUI(String settingsPath) throws Exception {
        this.terminal = TerminalBuilder.builder().system(true).build();
        this.chatService = ChatService.withTools(settingsPath, "boris");
        this.taskAborter = this.chatService.getTaskAborter();
        // Open /dev/tty directly — works even when stdin/stdout are redirected
        InputStream t;
        try { t = new FileInputStream("/dev/tty"); }
        catch (Exception e) { t = System.in; }
        this.tty = t;
    }

    public void start() throws Exception {
        // Install JLine's AnsiConsole so Ansi output works correctly
        AnsiConsole.systemInstall();
        sttyRaw();
        // Always restore terminal on JVM exit (covers Ctrl+C / SIGTERM)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            sttyRestore();
            try { terminal.close(); } catch (Exception ignored) {}
            AnsiConsole.systemUninstall();
        }));
        try {
            printBanner();

            while (true) {
                printPrompt();

                historyIndex = -1;  // reset navigation before each new prompt
                String input = readLineFromTty();
                if (input == null) {
                    // ESC or Ctrl+C at the prompt — just redraw
                    out("\n");
                    continue;
                }
                closeInputBox();
                input = input.trim();
                if (input.isEmpty()) continue;

                // Save to history (avoid duplicate consecutive entries)
                if (history.isEmpty() || !history.get(history.size() - 1).equals(input)) {
                    history.add(input);
                }

                // Print thinking indicator with spinner
                AtomicReference<Thread> spinnerRef = new AtomicReference<>(startSpinner());
                AtomicBoolean firstChunk = new AtomicBoolean(true);

                // Use streaming to print chunks as they arrive from the model.
                AtomicReference<String> responseRef = new AtomicReference<>(null);
                AtomicReference<Exception> errorRef = new AtomicReference<>(null);
                StringBuilder fullResponse = new StringBuilder();
                CountDownLatch streamDone = new CountDownLatch(1);
                String finalInput = input;
                Thread taskThread = new Thread(() -> {
                    try {
                        chatService.sendMessageStream(
                            finalInput,
                            chunk -> {
                                if (chunk != null && !chunk.isEmpty()) {
                                    if (firstChunk.compareAndSet(true, false)) {
                                        Thread sp = spinnerRef.getAndSet(null);
                                        if (sp != null) {
                                            try { stopSpinner(sp); } catch (Exception ignored) {}
                                        }
                                        openAnswer();
                                    }
                                    synchronized (fullResponse) {
                                        fullResponse.append(chunk);
                                    }
                                    try {
                                        terminal.writer().print(chunk);
                                        terminal.writer().flush();
                                    } catch (Exception ignored) {}
                                }
                            },
                            () -> {
                                synchronized (fullResponse) {
                                    responseRef.set(fullResponse.toString());
                                }
                                streamDone.countDown();
                            }
                        );
                    } catch (Exception e) {
                        errorRef.set(e);
                        streamDone.countDown();
                    }
                });
                taskThread.setDaemon(true);
                taskAborter.reset();
                taskAborter.startTask(taskThread);
                taskThread.start();

                // Main thread polls /dev/tty for ESC while the task is running.
                boolean aborted = false;
                while (taskThread.isAlive() || !streamDone.await(50, TimeUnit.MILLISECONDS)) {
                    if (tty.available() > 0) {
                        int ch = tty.read();
                        if (ch == 0x1B) {           // ESC → abort task
                            taskAborter.abort();
                            aborted = true;
                            break;
                        }
                        if (ch == 0x03) {           // Ctrl+C → exit app
                            sttyRestore();
                            System.exit(0);
                        }
                    }
                }

                if (aborted || taskAborter.isAborted()) {
                    Thread sp = spinnerRef.getAndSet(null);
                    if (sp != null) { try { stopSpinner(sp); } catch (Exception ignored) {} }
                    print(WARN, "aborted");
                    taskAborter.reset();
                    continue;
                }

                if (errorRef.get() != null) {
                    Thread sp = spinnerRef.getAndSet(null);
                    if (sp != null) { try { stopSpinner(sp); } catch (Exception ignored) {} }
                    throw errorRef.get();
                }

                String response = responseRef.get();
                if (response != null && ChatService.EXIT_COMMAND.equals(response)) {
                    break;
                }
                if (response != null) {
                    out(reset());
                    out("\n\n");
                }
            }

            out("\n");
        } finally {
            sttyRestore();
            AnsiConsole.systemUninstall();
        }
    }

    // ── Chrome / framing (kept deliberately quiet) ─────────────────────────

    /**
     * Single output channel for all chrome (banner, box, prompts, status
     * lines). Everything used to go through System.out while streamed
     * responses went through terminal.writer(); Jansi's AnsiConsole and
     * JLine's writer don't always agree on ANSI state until the writer has
     * been used at least once, which is why the border color used to look
     * "wrong" until the first message came back. Routing everything through
     * terminal.writer() keeps colors consistent from the very first frame.
     */
    private void out(String s) {
        terminal.writer().print(s);
        terminal.writer().flush();
    }

    /** Plain-text startup mark: name + slogan, no glyphs, no banner art. */
    private void printBanner() {
        out("\n");
        out(rgb(ACCENT));
        out("Boris");
        out(reset());
        out(rgb(DIM));
        out("  —  I am invincible\n");
        out("esc abort  ·  ctrl+c quit\n");
        out(reset());
        out("\n");
    }

    /**
     * Real terminal column count. Tries JLine's own {@code terminal.getWidth()}
     * first — it works in the vast majority of real terminals and costs no
     * subprocess spawn. Falls back to {@code stty size} on /dev/tty only if
     * JLine reports something implausible (0, or a "dumb terminal" default),
     * and finally to a conservative fixed value if both fail. This order
     * matters: silently trusting a fixed 80-column fallback (the old
     * behavior) is what caused the box to be drawn wider than the real
     * terminal and get wrapped/cut by the emulator whenever `stty size`
     * couldn't reach a real tty (common in IDE-integrated terminals,
     * some containers, and other non-interactive-tty setups).
     */
    private int queryTerminalColumns() {
        int jlineWidth = terminal.getWidth();
        if (jlineWidth > 20) {
            return jlineWidth;
        }
        try {
            Process p = new ProcessBuilder("sh", "-c", "stty size </dev/tty")
                .redirectErrorStream(true)
                .start();
            String out = new String(p.getInputStream().readAllBytes()).trim();
            p.waitFor();
            String[] parts = out.split("\\s+");
            if (parts.length == 2) {
                int cols = Integer.parseInt(parts[1]);
                if (cols > 20) return cols;
            }
        } catch (Exception ignored) {}
        return 60; // conservative fallback — better a smaller box than a cut one
    }

    /**
     * Width of the input box. A line that fills the terminal's full column
     * count edge-to-edge causes some terminals to insert an implicit
     * line-wrap before our own trailing newline, which is what made the box
     * look "cut in half" / folded onto an extra line. Reserving 3 columns
     * (2 border chars + 1 margin) instead of 2 avoids ever touching the
     * last column.
     */
    private int boxWidth() {
        int cols = queryTerminalColumns();
        return Math.max(cols - 3, 20);
    }

    /**
     * Boxed input prompt, Claude Code / opencode style. Unlike the previous
     * version — which only drew the top rule and left border up front, and
     * only closed the box with a bottom rule after Enter — this draws the
     * ENTIRE box (top, sides, bottom) immediately, then moves the cursor
     * back up into the input line. That's what fixes the "cut in half"
     * look: the box was never incomplete on screen, you were just typing
     * into a box whose bottom (and right side) hadn't been drawn yet.
     */
    private void printPrompt() {
        int w = boxWidth();
        lastBoxWidth = w;

        out(rgb(BORDER));
        out("╭" + "─".repeat(w) + "╮\n");

        // Input line: "│ › " then padding then the right border.
        out("│ ");
        out(rgb(ACCENT));
        out("›");
        out(" ");
        out(rgb(BORDER));
        int prefixLen = 4; // "│ › " is 4 visible columns
        out(" ".repeat(Math.max(w - prefixLen, 0)) + "│\n");

        out("╰" + "─".repeat(w) + "╯\n");

        // Move cursor back up 2 lines and to column 5 (right after "│ › ")
        // so the user types inside the already-complete box.
        out("\033[2A\033[5G");
        out(rgb(FG));
    }

    /** Moves the cursor below the already-complete box after Enter is pressed. */
    private void closeInputBox() {
        out("\033[2B\r");
        out(reset());
    }

    /** Starts the answer on its own line, labeled, in the neutral fg tone. */
    private void openAnswer() {
        out(rgb(ACCENT));
        out("\nBoris ");
        out(rgb(DIM));
        out("· ");
        out(rgb(FG));
    }

    /** Short status line reusing the prompt glyph in a different tone (e.g. aborted). */
    private void print(int[] color, String text) {
        out(rgb(color));
        out(text + "\n");
        out(reset());
    }

    /**
     * Reads one line of input from /dev/tty in raw mode.
     * Characters are echoed manually. Returns null if ESC (bare) or Ctrl+C is pressed.
     * Supports arrow-key history navigation (↑ previous, ↓ next).
     *
     * NOTE: this still echoes characters left-to-right without wrapping
     * inside the box frame. If typed input exceeds {@code lastBoxWidth}
     * columns it will overflow past the right border (a separate,
     * unrelated limitation from the "cut in half" bug — true in-box
     * line-wrapping would need to redraw the box on every keystroke).
     */
    private String readLineFromTty() throws IOException {
        StringBuilder sb = new StringBuilder();
        while (true) {
            int ch = tty.read();
            if (ch < 0) return null;                // EOF

            if (ch == 0x03) {                       // Ctrl+C → exit
                sttyRestore();
                System.exit(0);
            }

            // ── Escape sequence handling ───────────────────────────────────
            if (ch == 0x1B) {
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
                    if (!history.isEmpty()) {
                        if (historyIndex < 0) historyIndex = history.size();
                        if (historyIndex > 0) {
                            historyIndex--;
                            replaceCurrentLine(sb, history.get(historyIndex));
                        }
                    }
                } else if (arrow == 'B') {       // ↓ Arrow Down — go to next command
                    if (historyIndex >= 0) {
                        historyIndex++;
                        if (historyIndex >= history.size()) {
                            historyIndex = history.size(); // past-end = empty line
                            replaceCurrentLine(sb, "");
                        } else {
                            replaceCurrentLine(sb, history.get(historyIndex));
                        }
                    }
                }
                // Ignore other sequences (→, ←, F-keys, etc.)
                continue;
            }

            if (ch == '\r' || ch == '\n') break;    // Enter → submit

            if (ch == 0x7F || ch == '\b') {         // Backspace / Del
                if (sb.length() > 0) {
                    sb.deleteCharAt(sb.length() - 1);
                    out("\b \b");
                }
                continue;
            }

            if (ch >= 32) {                         // Printable char — echo it
                sb.append((char) ch);
                out(String.valueOf((char) ch));
            }
        }
        return sb.toString();
    }

    /**
     * Clears the current input on the terminal line and replaces it with {@code newText}.
     * Updates {@code sb} in-place to reflect the new content.
     */
    private void replaceCurrentLine(StringBuilder sb, String newText) {
        // Erase what is currently displayed
        int currentLen = sb.length();
        if (currentLen > 0) {
            // Move cursor back to start of typed text, overwrite with spaces, move back again
            String blanks = " ".repeat(currentLen);
            out("\b".repeat(currentLen) + blanks + "\b".repeat(currentLen));
        }
        // Write the history entry
        sb.setLength(0);
        sb.append(newText);
        out(newText);
    }

    // ── Color helpers ───────────────────────────────────────────────────

    private static String rgb(int[] c) {
        return Ansi.ansi().fgRgb(c[0], c[1], c[2]).toString();
    }

    private static String reset() {
        return Ansi.ansi().reset().toString();
    }

    /** Put terminal into raw mode: one char at a time, no echo. */
    private static void sttyRaw() {
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

    /** Restore the terminal to its saved state. Safe to call multiple times. */
    private static void sttyRestore() {
        try {
            String cmd = (savedTermSettings != null && !savedTermSettings.isEmpty())
                ? "stty " + savedTermSettings + " </dev/tty"
                : "stty sane </dev/tty";
            new ProcessBuilder("sh", "-c", cmd)
                .inheritIO().start().waitFor();
        } catch (Exception ignored) {}
    }

    /**
     * Minimal spinner: a single braille dot cycling in the accent color,
     * followed by a quiet elapsed-time label. No background block, no box —
     * just one moving glyph on an otherwise empty line.
     */
    private Thread startSpinner() throws Exception {
        out("\033[?25l"); // hide cursor while spinning

        Thread t = new Thread(() -> {
            String[] frames = { "⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏" };
            int frame = 0;
            long start = System.currentTimeMillis();
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    long elapsed = System.currentTimeMillis() - start;
                    int seconds = (int) (elapsed / 1000);
                    out("\r\033[2K");
                    out(rgb(ACCENT));
                    out(frames[frame % frames.length]);
                    out(reset());
                    out(rgb(DIM));
                    out(" " + seconds + "s");
                    out(reset());
                    frame++;
                    Thread.sleep(80);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    /** Stops the spinner and clears its line; restores cursor visibility. */
    private void stopSpinner(Thread spinnerThread) throws InterruptedException {
        spinnerThread.interrupt();
        spinnerThread.join(200);
        out("\r\033[2K\033[?25h");
    }
}