package com.boris.cli;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicReference;

import org.jline.jansi.Ansi;
import org.jline.jansi.AnsiConsole;

import com.boris.chat.ChatService;
import com.boris.task.TaskAborter;

public class BorisUI {

    private volatile boolean spinnerRunning = false;
    private static String savedTermSettings = null;

    private final ChatService chatService;
    private final TaskAborter taskAborter;
    private final InputStream tty;

    public BorisUI(String settingsPath) throws Exception {
        this.chatService = ChatService.withTools(settingsPath, "boris");
        this.taskAborter = this.chatService.getTaskAborter();
        // Open /dev/tty directly — works even when stdin/stdout are redirected
        InputStream t;
        try { t = new FileInputStream("/dev/tty"); }
        catch (Exception e) { t = System.in; }
        this.tty = t;
    }

    public void start() throws Exception {
        AnsiConsole.systemInstall();
        sttyRaw();
        // Always restore terminal on JVM exit (covers Ctrl+C / SIGTERM)
        Runtime.getRuntime().addShutdownHook(new Thread(BorisUI::sttyRestore));
        try {
            System.out.println();
            printGreen("boris");
            printlnGray("I'm an invisible");
            System.out.println();

            while (true) {
                System.out.print(Ansi.ansi().fgGreen());
                System.out.print("boris> ");
                System.out.flush();

                String input = readLineFromTty();
                if (input == null) {
                    // ESC or Ctrl+C at the prompt — just redraw
                    System.out.println();
                    continue;
                }
                input = input.trim();
                if (input.isEmpty()) continue;

                spinnerRunning = true;
                Thread spinnerThread = new Thread(this::startSpinner);
                spinnerThread.setDaemon(true);
                spinnerThread.start();

                // sendMessage() makes a blocking HTTP call that can't be interrupted,
                // so we run it in a daemon thread and bail out of the wait on ESC.
                AtomicReference<String> responseRef = new AtomicReference<>(null);
                AtomicReference<Exception> errorRef = new AtomicReference<>(null);
                String finalInput = input;
                Thread taskThread = new Thread(() -> {
                    try {
                        responseRef.set(chatService.sendMessage(finalInput));
                    } catch (Exception e) {
                        errorRef.set(e);
                    }
                });
                taskThread.setDaemon(true);
                taskAborter.reset();
                taskAborter.startTask(taskThread);
                taskThread.start();

                // Main thread polls /dev/tty for ESC while the task is running.
                // /dev/tty is in raw mode so each keystroke is immediately available.
                boolean aborted = false;
                while (taskThread.isAlive()) {
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
                    taskThread.join(50);
                }

                // Clear spinner line and return cursor to start of line
                stopSpinner();

                if (aborted || taskAborter.isAborted()) {
                    printlnGray("*Aborted*");
                    taskAborter.reset();
                    continue;
                }

                if (errorRef.get() != null) {
                    throw errorRef.get();
                }

                String response = responseRef.get();
                if (response != null && ChatService.EXIT_COMMAND.equals(response)) {
                    break;
                }
                if (response != null) {
                    for (String line : response.split("\\n", -1)) {
                        printlnGray(line);
                        System.out.println();
                    }
                }
            }

            System.out.println();
        } finally {
            sttyRestore();
            AnsiConsole.systemUninstall();
        }
    }

    /**
     * Reads one line of input from /dev/tty in raw mode.
     * Characters are echoed manually. Returns null if ESC or Ctrl+C is pressed.
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
            if (ch == 0x1B) return null;            // ESC → back to prompt
            if (ch == '\r' || ch == '\n') break;    // Enter → submit
            if (ch == 0x7F || ch == '\b') {         // Backspace / Del
                if (sb.length() > 0) {
                    sb.deleteCharAt(sb.length() - 1);
                    System.out.print("\b \b");
                    System.out.flush();
                }
                continue;
            }
            if (ch >= 32) {                         // Printable char — echo it
                sb.append((char) ch);
                System.out.print((char) ch);
                System.out.flush();
            }
        }
        System.out.println();
        return sb.toString();
    }

    private void printGreen(String text) {
        System.out.print(Ansi.ansi().fgRgb(74, 191, 85));
        System.out.println(text + Ansi.ansi().reset());
    }

    private void printlnGray(String text) {
        System.out.print(Ansi.ansi().fgRgb(255, 255, 255));
        System.out.println(text + Ansi.ansi().reset());
    }

    private final String[] SPINNER = {"\u280B", "\u2819", "\u2838", "\u2834", "\u2826", "\u2807", "\u2809", "\u2808"};
    private int spinnerIndex = 0;

    private void startSpinner() {
        spinnerRunning = true;
        while (spinnerRunning) {
            System.out.print("\r" + Ansi.ansi().fgRgb(255, 255, 255).bold());
            System.out.print(SPINNER[spinnerIndex] + " spinning.." + Ansi.ansi().reset());
            spinnerIndex = (spinnerIndex + 1) % SPINNER.length;
            try {
                Thread.sleep(80);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new com.boris.exceptions.BorisException("Spinner interrupted", e);
            }
        }
    }

    private void stopSpinner() {
        spinnerRunning = false;
        System.out.print("\r" + " ".repeat(40) + "\r");
        System.out.flush();
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
}
