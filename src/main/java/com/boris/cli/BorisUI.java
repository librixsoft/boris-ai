package com.boris.cli;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import com.boris.cli.ui.BorisTerminalFactory;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Borders;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.SeparateTextGUIThread;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowListenerAdapter;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.MouseCaptureMode;
import com.googlecode.lanterna.terminal.Terminal;

import com.boris.chat.ChatService;
import com.boris.cli.ui.CommandItem;
import com.boris.settings.Settings;
import com.boris.skill.SkillManager;
import com.boris.task.TaskAborter;
import com.boris.tooling.DiffLineNotifier;
import com.boris.tooling.ToolProgressNotifier;
import com.boris.tooling.ToolResultNotifier;
import com.boris.context.SessionLogWriter;

import com.boris.cli.ui.BorisWindow;
import com.boris.cli.ui.ChatController;
import com.boris.cli.ui.ChatPanel;
import com.boris.cli.ui.CommandHistory;
import com.boris.cli.ui.CommandMenu;
import com.boris.cli.ui.ModelSelector;
import com.boris.cli.ui.HeaderBar;
import com.boris.cli.ui.HintBar;
import com.boris.cli.ui.InputArea;
import com.boris.cli.ui.SessionIdBar;
import com.boris.cli.ui.StatusBar;
import com.boris.cli.ui.ThinkingSpinner;
import com.boris.cli.ui.ToastPopup;
import com.boris.cli.ui.TokenCounter;
import com.boris.cli.ui.Transcript;
import com.boris.cli.ui.UiExecutor;
import com.boris.cli.ui.UiTheme;

import com.boris.settings.SettingsManager;

public class BorisUI {

    private static final int SCROLL_STEP = 3;

    private final ChatService chatService;
    private final SkillManager skillManager;
    private final TaskAborter taskAborter;
    private final TokenCounter tokenCounter;
    private final Settings settings;
    private SessionLogWriter sessionLogWriter;

    private Screen screen;
    private MultiWindowTextGUI gui;
    private Window window;

    public BorisUI(ChatService chatService,
                   SkillManager skillManager,
                   TaskAborter taskAborter,
                   Settings settings) {
        this.chatService = chatService;
        this.skillManager = skillManager;
        this.taskAborter = taskAborter;
        this.settings = settings;
        int contextWindowLimit = 262000;
        if (this.settings != null && this.settings.getContextWindow() != null) {
            contextWindowLimit = this.settings.getContextWindow();
        }
        this.tokenCounter = new TokenCounter(contextWindowLimit);
    }

    public void setSessionLogWriter(SessionLogWriter sessionLogWriter) {
        this.sessionLogWriter = sessionLogWriter;
    }

    public void start() throws Exception {
        Terminal terminal = BorisTerminalFactory.create();
        if (terminal instanceof com.googlecode.lanterna.terminal.ExtendedTerminal) {
            ((com.googlecode.lanterna.terminal.ExtendedTerminal) terminal)
                    .setMouseCaptureMode(MouseCaptureMode.CLICK_RELEASE_DRAG_MOVE);
        }

        screen = new TerminalScreen(terminal);
        screen.startScreen();

        gui = new MultiWindowTextGUI(new SeparateTextGUIThread.Factory(), screen);
        gui.setTheme(UiTheme.darkTheme());
        UiExecutor uiExecutor = gui.getGUIThread()::invokeLater;

        AtomicBoolean waiting = new AtomicBoolean(false);
        AtomicBoolean wasAborted = new AtomicBoolean(false);
        CommandHistory commandHistory = new CommandHistory();

        ChatPanel chatPanel = new ChatPanel();
        boolean thinkingEnabled = this.settings != null && this.settings.getThinkingEnabled() != null ? this.settings.getThinkingEnabled() : true;
        Transcript transcript = new Transcript(chatPanel, uiExecutor);

        ToolResultNotifier.addListener(result -> {
            if (result.diff() != null && !result.diff().isEmpty()) {
                String diff = result.diff();
                // Strip markdown code fences for terminal display
                if (diff.startsWith("```diff\n")) {
                    diff = diff.substring(8);
                }
                if (diff.endsWith("\n```")) {
                    diff = diff.substring(0, diff.length() - 4);
                } else if (diff.endsWith("```")) {
                    diff = diff.substring(0, diff.length() - 3);
                }
                transcript.appendLine("\n" + diff);
            }
        });

        // Progress notifications from tools (can be toggled with Ctrl+O)
        ToolProgressNotifier.addListener((toolName, message) -> {
            transcript.appendToolDetail(toolName, message);
        });

        // Diff line notifications - rendered with colors, not as tool details
        DiffLineNotifier.addListener(line -> {
            transcript.appendLine(line);
        });

        StatusBar statusBar = new StatusBar(uiExecutor);
        chatPanel.setClipboardCopyListener(() -> {
            ToastPopup toast = new ToastPopup("Texto copiado al portapapeles", uiExecutor);
            toast.showFor(gui, 2);
        });
        ThinkingSpinner spinner = new ThinkingSpinner(statusBar, tokenCounter, waiting, wasAborted, () -> chatPanel.isThinkingEnabled());

        // Create HeaderBar and SettingsManager upfront so we can wire them to ChatController
        String modelName = this.settings != null && this.settings.getModel() != null ? this.settings.getModel().getName() : null;
        HeaderBar headerBar = new HeaderBar(modelName);
        SettingsManager settingsManager = new SettingsManager();

        ChatController controller = new ChatController(
                chatService,
                skillManager,
                taskAborter,
                commandHistory,
                tokenCounter,
                spinner,
                statusBar,
                transcript,
                chatPanel,
                waiting,
                wasAborted,
                () -> window.close(),
                headerBar,
                settingsManager,
                null
        );
        window = new BorisWindow(chatPanel, SCROLL_STEP, delta -> uiExecutor.run(() -> chatPanel.scroll(delta)));
        window.setHints(Arrays.asList(Window.Hint.FULL_SCREEN, Window.Hint.NO_DECORATIONS));
        window.addWindowListener(new WindowListenerAdapter() {
            @Override
            public void onResized(Window w, TerminalSize oldSize, TerminalSize newSize) {
                transcript.rerender();
            }
        });

        Panel root = new Panel(new BorderLayout());
        Panel topPanel = new Panel(new BorderLayout());
        topPanel.addComponent(headerBar, BorderLayout.Location.LEFT);
        SessionIdBar sessionIdBar = new SessionIdBar();
        topPanel.addComponent(sessionIdBar, BorderLayout.Location.RIGHT);
        root.addComponent(topPanel, BorderLayout.Location.TOP);
        root.addComponent(chatPanel, BorderLayout.Location.CENTER);

        Panel footer = new Panel(new LinearLayout(Direction.VERTICAL));
        footer.addComponent(createVerticalSpacer());
        footer.addComponent(statusBar);
        footer.addComponent(createVerticalSpacer());

        CommandMenu commandMenu = new CommandMenu(CommandItem.withSkills(skillManager));
        footer.addComponent(commandMenu);

        // Create ModelSelector with supplier to fetch models dynamically
        final ModelSelector[] modelSelectorHolder = new ModelSelector[1];
        modelSelectorHolder[0] = new ModelSelector(
                () -> new ArrayList<>(),
                () -> {
                    // When a model is selected via Enter (legacy callback - now handled by InputArea)
                    String selectedModel = modelSelectorHolder[0].getSelectedModel();
                    if (selectedModel != null) {
                        controller.applyModelSelection(selectedModel);
                    }
                }
        );
        ModelSelector modelSelector = modelSelectorHolder[0];
        footer.addComponent(modelSelector);

        HintBar hintBar = new HintBar();
        transcript.setHiddenCountListener(count -> {
            uiExecutor.run(() -> hintBar.showHiddenDetailsHint(count));
        });
        InputArea inputArea = new InputArea(commandHistory, waiting, SCROLL_STEP, controller, hintBar);
        inputArea.setCommandMenu(commandMenu);
        inputArea.setModelSelector(modelSelector);
        inputArea.setTranscript(transcript);
        inputArea.setToolDetailsToggleListener(showDetails -> {
            hintBar.setToolDetailsVisible(showDetails);
            hintBar.showHiddenDetailsHint(showDetails ? 0 : transcript.getHiddenDetailsCount());
        });
        inputArea.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
        footer.addComponent(inputArea);
        footer.addComponent(createVerticalSpacer());
        footer.addComponent(hintBar);

        root.addComponent(footer, BorderLayout.Location.BOTTOM);

        window.setComponent(root.withBorder(Borders.singleLine()));
        window.setFocusedInteractable(inputArea.getTextBox());
        gui.addWindow(window);

        ((SeparateTextGUIThread) gui.getGUIThread()).start();

        transcript.appendLine(" boris listo 🤖. Escribe tu mensaje.");
        transcript.appendBlankLine();
        statusBar.showTokenStatus(tokenCounter);

        try {
            window.waitUntilClosed();
        } finally {
            if (sessionLogWriter != null) {
                sessionLogWriter.logSessionEnd();
            }
            screen.stopScreen();
        }
    }

    private static Label createVerticalSpacer() {
        return new Label(" ");
    }
}
