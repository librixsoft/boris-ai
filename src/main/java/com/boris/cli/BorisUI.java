package com.boris.cli;

import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.Border;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Borders;
import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.MultiWindowTextGUI;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.SeparateTextGUIThread;
import com.googlecode.lanterna.gui2.Window;
import com.googlecode.lanterna.gui2.WindowListenerAdapter;
import com.googlecode.lanterna.screen.Screen;
import com.googlecode.lanterna.screen.TerminalScreen;
import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.MouseCaptureMode;
import com.googlecode.lanterna.terminal.Terminal;

import com.boris.chat.ChatService;
import com.boris.cli.ui.CommandItem;
import com.boris.settings.Settings;
import com.boris.skill.SkillManager;
import com.boris.task.TaskAborter;

import com.boris.cli.ui.BorisWindow;
import com.boris.cli.ui.ChatController;
import com.boris.cli.ui.ChatPanel;
import com.boris.cli.ui.CommandHistory;
import com.boris.cli.ui.CommandMenu;
import com.boris.cli.ui.HeaderBar;
import com.boris.cli.ui.HintBar;
import com.boris.cli.ui.InputArea;
import com.boris.cli.ui.StatusBar;
import com.boris.cli.ui.ThinkingSpinner;
import com.boris.cli.ui.TokenCounter;
import com.boris.cli.ui.Transcript;
import com.boris.cli.ui.UiExecutor;
import com.boris.cli.ui.UiTheme;

public class BorisUI {

    private static final int SCROLL_STEP = 3;

    private final ChatService chatService;
    private final SkillManager skillManager;
    private final TaskAborter taskAborter;
    private final TokenCounter tokenCounter;
    private final Settings settings;

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
        int contextWindowLimit = 10000;
        if (this.settings != null && this.settings.getContextWindow() != null) {
            contextWindowLimit = this.settings.getContextWindow();
        }
        this.tokenCounter = new TokenCounter(contextWindowLimit);
    }

    public void start() throws Exception {
        DefaultTerminalFactory factory = new DefaultTerminalFactory();
        factory.setMouseCaptureMode(MouseCaptureMode.CLICK_RELEASE_DRAG_MOVE);

        Terminal terminal = factory.createTerminal();
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
        StatusBar statusBar = new StatusBar(uiExecutor);
        ThinkingSpinner spinner = new ThinkingSpinner(statusBar, tokenCounter, waiting, wasAborted, () -> chatPanel.isThinkingEnabled());

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
                () -> window.close()
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
        String modelName = this.settings != null && this.settings.getModel() != null ? this.settings.getModel().getName() : null;
        root.addComponent(new HeaderBar(modelName), BorderLayout.Location.TOP);
        root.addComponent(chatPanel.withBorder(Borders.singleLine()), BorderLayout.Location.CENTER);

        Panel footer = new Panel(new LinearLayout(Direction.VERTICAL));
        footer.addComponent(statusBar);

        CommandMenu commandMenu = new CommandMenu(CommandItem.withSkills(skillManager));
        footer.addComponent(commandMenu);

        HintBar hintBar = new HintBar();
        InputArea inputArea = new InputArea(commandHistory, waiting, SCROLL_STEP, controller, hintBar);
        inputArea.setCommandMenu(commandMenu);
        Border borderedInput = inputArea.withBorder(Borders.singleLine());
        borderedInput.setLayoutData(LinearLayout.createLayoutData(LinearLayout.Alignment.Fill));
        footer.addComponent(borderedInput);
        footer.addComponent(hintBar);

        root.addComponent(footer, BorderLayout.Location.BOTTOM);

        window.setComponent(root);
        window.setFocusedInteractable(inputArea.getTextBox());
        gui.addWindow(window);

        ((SeparateTextGUIThread) gui.getGUIThread()).start();

        transcript.appendLine("boris listo. Escribe tu mensaje.");
        statusBar.showTokenStatus(tokenCounter);

        try {
            window.waitUntilClosed();
        } finally {
            screen.stopScreen();
        }
    }
}
