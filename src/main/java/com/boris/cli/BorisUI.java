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
    private final ConversationView conversationView;
    private final InputBar inputBar;
    private final StatusUI statusUI;

    private final ChatService chatService;
    private final TaskAborter taskAborter;

    public BorisUI(String settingsPath) throws Exception {
        this.terminalConfigurator = new TerminalConfigurator();
        this.colorPalette = ColorPalette.defaultPalette();
        this.commandHistory = new CommandHistory();
        this.userInputReader = new UserInputReader(terminalConfigurator.getTty(), terminalConfigurator, commandHistory);
        this.conversationView = new ConversationView(terminalConfigurator, colorPalette);
        this.inputBar = new InputBar(terminalConfigurator, colorPalette);
        this.statusUI = new StatusUI(terminalConfigurator, colorPalette);
        this.userInputReader.setOnBufferChanged(buffer -> inputBar.render(buffer));

        this.chatService = ChatService.withTools(settingsPath, "boris");
        this.taskAborter = this.chatService.getTaskAborter();
    }

    public void start() throws Exception {
        terminalConfigurator.installAnsiConsole();
        terminalConfigurator.sttyRaw();

        int[] size = terminalConfigurator.getTerminalSize();
        int rows = size[0];
        terminalConfigurator.setScrollRegion(1, rows - inputBar.getHeight());
        terminalConfigurator.enableScrollLock();
        conversationView.printBanner();
        inputBar.render("");

        // Always restore terminal on JVM exit (covers Ctrl+C / SIGTERM)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { terminalConfigurator.disableScrollLock(); } catch (Exception ignored) {}
            try { terminalConfigurator.resetScrollRegion(); } catch (Exception ignored) {}
            terminalConfigurator.sttyRestore();
            terminalConfigurator.close();
            terminalConfigurator.uninstallAnsiConsole();
        }));
        try {
            while (true) {
                inputBar.render("");

                commandHistory.resetNavigation();
                String input = userInputReader.readLine();
                if (input == null) {
                    continue;
                }
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
                                        conversationView.openAnswer();
                                    }
                                    synchronized (fullResponse) {
                                        fullResponse.append(chunk);
                                    }
                                    try {
                                        conversationView.appendChunk(chunk);
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
                while (true) {
                    boolean finished = streamDone.await(50, TimeUnit.MILLISECONDS);
                    if (userInputReader.available()) {
                        int ch = userInputReader.read();
                        if (ch == 0x1B) {
                            taskAborter.abort();
                            aborted = true;
                            break;
                        }
                        if (ch == 0x03) {
                            terminalConfigurator.sttyRestore();
                            System.exit(0);
                        }
                    }
                    if (finished) break;
                }

                if (aborted || taskAborter.isAborted()) {
                    try { statusUI.stop(); } catch (Exception ignored) {}
                    conversationView.printStatus("aborted");
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
                    conversationView.printNewline();
                }
                terminalConfigurator.moveCursorTo(rows() - 1, 2 + 2);
            }

            inputBar.clear();
            terminalConfigurator.moveCursorTo(rows(), 1);
        } finally {
            terminalConfigurator.disableScrollLock();
            try { terminalConfigurator.resetScrollRegion(); } catch (Exception ignored) {}
            terminalConfigurator.sttyRestore();
            terminalConfigurator.uninstallAnsiConsole();
        }
    }

    private int rows() {
        return terminalConfigurator.getTerminalSize()[0];
    }
}