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
 * responses, and a quiet StatusUI with thinking indicator while working.
 */
public class BorisUI {

    private final TerminalConfigurator terminalConfigurator;
    private final ColorPalette colorPalette;
    private final CommandHistory commandHistory;
    private final UserInputReader userInputReader;
    private final MessageRenderer messageRenderer;
    private final StatusUI statusUI;

    private final ChatService chatService;
    private final TaskAborter taskAborter;

    public BorisUI(String settingsPath) throws Exception {
        this.terminalConfigurator = new TerminalConfigurator();
        this.colorPalette = ColorPalette.defaultPalette();
        this.commandHistory = new CommandHistory();
        this.userInputReader = new UserInputReader(terminalConfigurator.getTty(), terminalConfigurator, commandHistory);
        this.messageRenderer = new MessageRenderer(terminalConfigurator, colorPalette);
        this.statusUI = new StatusUI(terminalConfigurator, colorPalette);
        
        this.chatService = ChatService.withTools(settingsPath, "boris");
        this.taskAborter = this.chatService.getTaskAborter();
    }

    public void start() throws Exception {
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

                // Print thinking indicator with counter
                AtomicBoolean firstChunk = new AtomicBoolean(true);
                statusUI.start();

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
                                        try { statusUI.stop(); } catch (Exception ignored) {}
                                        messageRenderer.openAnswer();
                                    }
                                    synchronized (fullResponse) {
                                        fullResponse.append(chunk);
                                    }
                                    try {
                                        terminalConfigurator.out(chunk);
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
                    try { statusUI.stop(); } catch (Exception ignored) {}
                    messageRenderer.printStatus("aborted");
                    taskAborter.reset();
                    continue;
                }

                if (errorRef.get() != null) {
                    try { statusUI.stop(); } catch (Exception ignored) {}
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