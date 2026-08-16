package com.boris.cli;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicBoolean;

import com.boris.chat.ChatService;
import com.boris.task.TaskAborter;
import com.boris.cli.ui.*;

/**
 * BorisUI — minimal terminal front-end, in the spirit of modern coding-agent
 * CLIs (Claude Code / opencode): a plain-text name + slogan on startup (no
 * ASCII art), a borderless single-line input prompt, a "Boris ·" label on
 * responses, and a quiet dot-spinner while working.
 */
public class BorisUI {

    private final TerminalConfigurator terminalConfigurator;
    private final ColorPalette colorPalette;
    private final CommandHistory commandHistory;
    private final UserInputReader userInputReader;
    private final MessageRenderer messageRenderer;
    private final Spinner spinner;

    private final ChatService chatService;
    private final TaskAborter taskAborter;

    public BorisUI(String settingsPath) throws Exception {
        this.terminalConfigurator = new TerminalConfigurator();
        this.colorPalette = ColorPalette.defaultPalette();
        this.commandHistory = new CommandHistory();
        this.userInputReader = new UserInputReader(terminalConfigurator.getTty(), terminalConfigurator, commandHistory);
        this.messageRenderer = new MessageRenderer(terminalConfigurator, colorPalette);
        this.spinner = new Spinner(terminalConfigurator, colorPalette);
        
        this.chatService = ChatService.withTools(settingsPath, "boris");
        this.taskAborter = this.chatService.getTaskAborter();
    }

    public void start() throws Exception {
        // Install JLine's AnsiConsole so Ansi output works correctly
        terminalConfigurator.installAnsiConsole();
        terminalConfigurator.sttyRaw();
        // Always restore terminal on JVM exit (covers Ctrl+C / SIGTERM)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            terminalConfigurator.sttyRestore();
            terminalConfigurator.close();
            terminalConfigurator.uninstallAnsiConsole();
        }));
        try {
            messageRenderer.printBanner();

            while (true) {
                messageRenderer.printPrompt();

                commandHistory.resetNavigation();
                String input = userInputReader.readLine();
                if (input == null) {
                    // ESC or Ctrl+C at the prompt — just redraw
                    messageRenderer.out("\n");
                    continue;
                }
                messageRenderer.closeInputBox();
                input = input.trim();
                if (input.isEmpty()) continue;

                // Save to history (avoid duplicate consecutive entries)
                commandHistory.addCommand(input);

                // Print thinking indicator with spinner
                AtomicReference<Thread> spinnerRef = new AtomicReference<>(spinner.start());
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
                                            try { sp.interrupt(); sp.join(200); } catch (Exception ignored) {}
                                            terminalConfigurator.out("\033[?25h\n");
                                        }
                                        messageRenderer.openAnswer();
                                    }
                                    synchronized (fullResponse) {
                                        fullResponse.append(chunk);
                                    }
                                    try {
                                        terminalConfigurator.getTerminal().writer().print(chunk);
                                        terminalConfigurator.getTerminal().writer().flush();
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
                // NOTE: streamDone.await(...) must be called unconditionally on every
                // iteration — it's what paces this loop. The previous version used
                // `taskThread.isAlive() || !streamDone.await(...)`, and because `||`
                // short-circuits, await() was never invoked while the thread was
                // alive, turning this into an unthrottled busy-spin for the entire
                // duration of the task (100% CPU on one core, and no fixed cadence
                // for polling /dev/tty).
                boolean aborted = false;
                while (true) {
                    boolean finished = streamDone.await(50, TimeUnit.MILLISECONDS);
                    if (userInputReader.available()) {
                        int ch = userInputReader.read();
                        if (ch == 0x1B) {           // ESC → abort task
                            taskAborter.abort();
                            aborted = true;
                            break;
                        }
                        if (ch == 0x03) {           // Ctrl+C → exit app
                            terminalConfigurator.sttyRestore();
                            System.exit(0);
                        }
                    }
                    if (finished) break;
                }

                if (aborted || taskAborter.isAborted()) {
                    Thread sp = spinnerRef.getAndSet(null);
                    if (sp != null) { try { spinner.stop(); } catch (Exception ignored) {} }
                    messageRenderer.printStatus("aborted");
                    taskAborter.reset();
                    continue;
                }

                if (errorRef.get() != null) {
                    Thread sp = spinnerRef.getAndSet(null);
                    if (sp != null) { try { spinner.stop(); } catch (Exception ignored) {} }
                    throw errorRef.get();
                }

                String response = responseRef.get();
                if (response != null && ChatService.EXIT_COMMAND.equals(response)) {
                    break;
                }
                if (response != null) {
                    messageRenderer.printNewline();
                }
            }

            messageRenderer.out("\n");
        } finally {
            terminalConfigurator.sttyRestore();
            terminalConfigurator.uninstallAnsiConsole();
        }
    }

}