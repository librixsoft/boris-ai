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
 * responses, and proper scroll region isolation for a clean modern interface.
 * 
 * IMPORTANT: STRICT PROHIBITION - Manual ANSI escape sequences are NOT ALLOWED.
 * All terminal operations MUST use JLine3 APIs through TerminalConfigurator.
 * Manual ANSI sequences interfere with JLine3's internal state management and break UI rendering.
 */
public class BorisUI {

    private final TerminalConfigurator terminalConfigurator;
    private final ColorPalette colorPalette;
    private final CommandHistory commandHistory;
    private final UserInputReader userInputReader;
    private final ConversationView conversationView;
    private final InputBar inputBar;

    private final ChatService chatService;
    private final TaskAborter taskAborter;

    public BorisUI(String settingsPath) throws Exception {
        this.terminalConfigurator = new TerminalConfigurator();
        this.colorPalette = ColorPalette.defaultPalette();
        this.commandHistory = new CommandHistory();
        this.userInputReader = new UserInputReader(terminalConfigurator.getTty(), terminalConfigurator, commandHistory);
        this.inputBar = new InputBar(terminalConfigurator, colorPalette);
        this.conversationView = new ConversationView(terminalConfigurator, colorPalette);
        this.userInputReader.setOnBufferChanged(buffer -> inputBar.render(buffer));

        this.chatService = ChatService.withTools(settingsPath, "boris");
        this.taskAborter = this.chatService.getTaskAborter();
    }

    public void start() throws Exception {
        terminalConfigurator.sttyRaw();

        int[] size = terminalConfigurator.getTerminalSize();
        int rows = size[0];
        int inputBarHeight = inputBar.getHeight();
        int bannerLines = 4;
        int contentHeight = rows - bannerLines - inputBarHeight;
        
        if (contentHeight < 1) {
            contentHeight = 1;
        }
        
        // Set scroll region to exclude banner (top) and input bar (bottom)
        // Scroll region is 1-based, so bannerLines + 1 is first scrollable line
        // rows - inputBarHeight is the last scrollable line
        int scrollTop = bannerLines + 1;
        int scrollBottom = rows - inputBarHeight;
        
        // Validate scroll region boundaries
        if (scrollTop >= scrollBottom) {
            scrollBottom = Math.max(scrollTop + 1, rows);
        }
        
        terminalConfigurator.setScrollRegion(scrollTop, scrollBottom);
        conversationView.initialize(inputBarHeight);
        conversationView.printBanner();
        inputBar.render("");

        // Always restore terminal on JVM exit (covers Ctrl+C / SIGTERM)
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            terminalConfigurator.close();
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

                // Display the user's message in the chat
                conversationView.openQuestion(input);

                // Use streaming to print chunks as they arrive from the model.
                AtomicBoolean firstChunk = new AtomicBoolean(true);
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
                                conversationView.finishCurrentLine();
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
                boolean quit = false;
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
                            taskAborter.abort();
                            quit = true;
                            streamDone.await(200, TimeUnit.MILLISECONDS);
                            break;
                        }
                    }
                    if (finished) break;
                }

                if (quit) {
                    break;
                }

                if (aborted || taskAborter.isAborted()) {
                    conversationView.finishCurrentLine();
                    conversationView.printStatus("aborted");
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
                    conversationView.printNewline();
                }
            }

            inputBar.clear();
            terminalConfigurator.moveCursorTo(terminalConfigurator.getTerminalSize()[0], 1);
        } finally {
            terminalConfigurator.close();
        }
    }
}