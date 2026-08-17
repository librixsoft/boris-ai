package com.boris.cli;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.jline.reader.EndOfFileException;
import org.jline.reader.LineReader;
import org.jline.reader.LineReaderBuilder;
import org.jline.reader.UserInterruptException;
import org.jline.terminal.Terminal;
import org.jline.terminal.TerminalBuilder;
import org.jline.utils.AttributedStringBuilder;
import org.jline.utils.AttributedStyle;
import org.jline.utils.InfoCmp.Capability;

import com.boris.chat.ChatService;

/**
 * BorisUI — terminal interface styled after modern agent CLIs
 * (Claude Code / OpenClaw). Built on JLine3: proper line editing,
 * history, spinner while the model "thinks", and a minimal boxed
 * banner. No raw ANSI strings scattered around — everything goes
 * through AttributedStyle so it stays portable across terminals.
 */
public class BorisUI {

    // ---- palette ----------------------------------------------------
    private static final AttributedStyle ACCENT     = AttributedStyle.DEFAULT.foreground(208).bold();   // orange
    private static final AttributedStyle ACCENT_DIM = AttributedStyle.DEFAULT.foreground(208);
    private static final AttributedStyle MUTED      = AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE).faint();
    private static final AttributedStyle TEXT       = AttributedStyle.DEFAULT.foreground(AttributedStyle.WHITE);
    private static final AttributedStyle USER       = AttributedStyle.DEFAULT.foreground(45).bold();    // cyan
    private static final AttributedStyle ERROR      = AttributedStyle.DEFAULT.foreground(196).bold();

    private static final String[] SPINNER_FRAMES =
            {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};

    private final ChatService chatService;
    private final Terminal terminal;
    private final LineReader reader;

    public BorisUI(String settingsPath) throws Exception {
        this.terminal = TerminalBuilder.builder()
                .system(true)
                .build();

        this.reader = LineReaderBuilder.builder()
                .terminal(terminal)
                .appName("boris")
                .build();

        this.chatService = ChatService.withTools(settingsPath, "boris");
    }

    public void start() throws Exception {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                terminal.close();
            } catch (Exception ignored) {
            }
        }));

        clearScreen();
        printBanner();

        try {
            while (true) {
                String input;
                try {
                    input = reader.readLine(prompt());
                } catch (UserInterruptException e) {
                    // Ctrl+C while typing: don't kill the app, just reset the line
                    println("", MUTED);
                    println("  (ctrl+c) type /exit to quit", MUTED);
                    continue;
                } catch (EndOfFileException e) {
                    break; // Ctrl+D
                }

                if (input == null) break;
                input = input.trim();
                if (input.isEmpty()) continue;

                if (input.equals("/exit") || input.equals("/quit")) break;
                if (input.equals("/clear")) {
                    clearScreen();
                    printBanner();
                    continue;
                }

                terminal.writer().println();
                boolean shouldExit = runTurn(input);
                if (shouldExit) break;
            }
        } finally {
            terminal.writer().println();
            println("  goodbye", MUTED);
            terminal.flush();
            terminal.close();
        }
    }

    /** Runs one request/response turn. Returns true if the app should exit. */
    private boolean runTurn(String input) throws Exception {
        AtomicBoolean waiting = new AtomicBoolean(true);
        AtomicReference<String> responseRef = new AtomicReference<>(null);
        AtomicReference<Exception> errorRef = new AtomicReference<>(null);
        StringBuilder fullResponse = new StringBuilder();
        CountDownLatch streamDone = new CountDownLatch(1);

        Thread spinner = startSpinner(waiting);

        Thread task = new Thread(() -> {
            try {
                chatService.sendMessageStream(
                        input,
                        chunk -> {
                            if (chunk != null && !chunk.isEmpty()) {
                                if (waiting.compareAndSet(true, false)) {
                                    clearSpinnerLine();
                                    printAssistantLabel();
                                }
                                synchronized (fullResponse) {
                                    fullResponse.append(chunk);
                                }
                                terminal.writer().print(chunk);
                                terminal.flush();
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
        task.setDaemon(true);
        task.start();

        boolean finished = false;
        while (!finished) {
            finished = streamDone.await(100, TimeUnit.MILLISECONDS);
        }
        waiting.set(false);
        spinner.join(200);

        if (errorRef.get() != null) {
            clearSpinnerLine();
            println("  ✗ error: " + errorRef.get().getMessage(), ERROR);
            terminal.writer().println();
            return false;
        }

        terminal.writer().println();
        terminal.writer().println();
        terminal.flush();

        String response = responseRef.get();
        return response != null && ChatService.EXIT_COMMAND.equals(response);
    }

    // ---- spinner ------------------------------------------------------

    private Thread startSpinner(AtomicBoolean waiting) {
        Thread t = new Thread(() -> {
            int i = 0;
            try {
                while (waiting.get()) {
                    String frame = SPINNER_FRAMES[i % SPINNER_FRAMES.length];
                    AttributedStringBuilder sb = new AttributedStringBuilder();
                    sb.style(ACCENT_DIM).append("  ").append(frame).append(" ");
                    sb.style(MUTED).append("thinking...");
                    terminal.writer().print("\r" + sb.toAnsi(terminal));
                    terminal.flush();
                    i++;
                    Thread.sleep(80);
                }
            } catch (InterruptedException ignored) {
            }
        });
        t.setDaemon(true);
        t.start();
        return t;
    }

    private void clearSpinnerLine() {
        terminal.writer().print("\r\u001B[2K");
        terminal.flush();
    }

    private void printAssistantLabel() {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(ACCENT).append("  ● ");
        terminal.writer().print(sb.toAnsi(terminal));
        terminal.flush();
    }

    // ---- prompt / banner -----------------------------------------------

    private String prompt() {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(USER).append("❯ ");
        return sb.toAnsi(terminal);
    }

    private void clearScreen() {
        terminal.puts(Capability.clear_screen);
        terminal.flush();
    }

    private void printBanner() {
        int width = Math.max(40, Math.min(terminal.getWidth() > 0 ? terminal.getWidth() : 64, 64));
        String top = "╭" + "─".repeat(width - 2) + "╮";
        String bottom = "╰" + "─".repeat(width - 2) + "╯";

        println(top, MUTED);
        printBoxLine(width, "", TEXT);
        printBoxLine(width, "  boris", ACCENT);
        printBoxLine(width, "  your terminal AI agent", MUTED);
        printBoxLine(width, "", TEXT);
        printBoxLine(width, "  /exit    quit", MUTED);
        printBoxLine(width, "  /clear   clear the screen", MUTED);
        printBoxLine(width, "", TEXT);
        println(bottom, MUTED);
        terminal.writer().println();
        terminal.flush();
    }

    private void printBoxLine(int width, String content, AttributedStyle style) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(MUTED).append("│");
        sb.style(style).append(pad(content, width - 2));
        sb.style(MUTED).append("│");
        terminal.writer().println(sb.toAnsi(terminal));
    }

    private String pad(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) sb.append(' ');
        return sb.toString();
    }

    private void println(String text, AttributedStyle style) {
        AttributedStringBuilder sb = new AttributedStringBuilder();
        sb.style(style).append(text);
        terminal.writer().println(sb.toAnsi(terminal));
        terminal.flush();
    }
}