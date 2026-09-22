package com.boris.cli.ui;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.BorderLayout;
import com.googlecode.lanterna.gui2.Interactable;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.Panel;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;

import java.util.concurrent.atomic.AtomicBoolean;

public class InputArea extends Panel {

    public interface InputListener {

        void onSubmit(String text);

        void onAbort();

        void onLineScroll(int deltaLines);

        void onPageScroll(int direction);
    }

    private final TextBox inputBox;
    private final CommandHistory commandHistory;
    private final AtomicBoolean waiting;
    private final int scrollStep;
    private final InputListener listener;
    private HintBar hintBar;

    public InputArea(CommandHistory commandHistory, AtomicBoolean waiting, int scrollStep, InputListener listener) {
        this(commandHistory, waiting, scrollStep, listener, null);
    }

    public InputArea(CommandHistory commandHistory, AtomicBoolean waiting, int scrollStep, InputListener listener, HintBar hintBar) {
        super(new BorderLayout());
        this.commandHistory = commandHistory;
        this.waiting = waiting;
        this.scrollStep = scrollStep;
        this.listener = listener;
        this.hintBar = hintBar;

        Label promptLabel = new Label("❯ ");
        promptLabel.setForegroundColor(UiTheme.USERC);
        addComponent(promptLabel, BorderLayout.Location.LEFT);

        inputBox = new TextBox(new TerminalSize(1, 1), TextBox.Style.MULTI_LINE);
        addComponent(inputBox, BorderLayout.Location.CENTER);

        inputBox.setTextChangeListener((newText, changedByUser) -> {
            if (this.hintBar != null) {
                if (newText != null && newText.startsWith("/")) {
                    this.hintBar.showMenu(newText);
                } else if (this.hintBar.isMenuVisible()) {
                    this.hintBar.hideMenu();
                }
            }
        });

        inputBox.setInputFilter(this::handleKey);
    }

    public void setHintBar(HintBar hintBar) {
        this.hintBar = hintBar;
    }

    public TextBox getTextBox() {
        return inputBox;
    }

    public void insertTextAtCaret(String toInsert) {
        if (toInsert == null || toInsert.isEmpty()) {
            return;
        }
        String currentText = inputBox.getText();
        TerminalPosition caret = inputBox.getCaretPosition();
        int row = caret != null ? caret.getRow() : 0;
        int col = caret != null ? caret.getColumn() : 0;

        String[] lines = currentText.split("\r?\n", -1);
        if (row < 0 || row >= lines.length) {
            row = Math.max(0, lines.length - 1);
        }
        String line = lines[row];
        if (col < 0) col = 0;
        if (col > line.length()) col = line.length();

        int offset = 0;
        for (int r = 0; r < row; r++) {
            offset += lines[r].length() + 1;
        }
        offset += col;

        String before = currentText.substring(0, Math.min(offset, currentText.length()));
        String after = currentText.substring(Math.min(offset, currentText.length()));
        String newText = before + toInsert + after;

        inputBox.setText(newText);
        if (after.isEmpty() && toInsert.endsWith("\n")) {
            inputBox.addLine("");
        }

        String textUpToCaret = before + toInsert;
        String[] newLines = textUpToCaret.split("\r?\n", -1);
        int newRow = newLines.length - 1;
        int newCol = newLines[newRow].length();
        inputBox.setCaretPosition(newRow, newCol);
    }

    private long lastKeyTime = 0;

    private boolean handleKey(Interactable interactable, KeyStroke keyStroke) {
        long now = System.currentTimeMillis();
        long elapsed = (lastKeyTime > 0) ? (now - lastKeyTime) : 1000;
        lastKeyTime = now;

        KeyType type = keyStroke.getKeyType();

        // Ctrl+V paste support
        if (type == KeyType.Character && keyStroke.isCtrlDown() && keyStroke.getCharacter() != null
                && (keyStroke.getCharacter() == 'v' || keyStroke.getCharacter() == 'V')) {
            String pasteText = ClipboardUtil.paste();
            if (pasteText != null && !pasteText.isEmpty()) {
                insertTextAtCaret(pasteText);
            }
            return false;
        }

        // Multiline newline insertion (Ctrl+Enter, Alt+Enter, Shift+Enter, Ctrl+J / LF '\n', or fast paste burst Enter)
        boolean isExplicitCtrlEnter = (type == KeyType.Enter && (keyStroke.isCtrlDown() || keyStroke.isAltDown() || keyStroke.isShiftDown()))
                || (type == KeyType.Character && keyStroke.getCharacter() != null && keyStroke.getCharacter() == '\n')
                || (type == KeyType.Character && keyStroke.isCtrlDown() && keyStroke.getCharacter() != null && (keyStroke.getCharacter() == 'j' || keyStroke.getCharacter() == 'J'));

        boolean isPasteBurstEnter = (type == KeyType.Enter && elapsed <= 25);

        if (isExplicitCtrlEnter || isPasteBurstEnter) {
            insertTextAtCaret("\n");
            return false;
        }

        if (type == KeyType.Enter) {
            if (!waiting.get()) {
                if (hintBar != null && hintBar.isMenuVisible()) {
                    CommandItem selected = hintBar.getSelectedCommand();
                    hintBar.hideMenu();
                    if (selected != null) {
                        inputBox.setText("");
                        listener.onSubmit(selected.getCommand());
                        return false;
                    }
                }

                String text = inputBox.getText().trim();
                if (!text.isEmpty()) {
                    inputBox.setText("");
                    if (hintBar != null) {
                        hintBar.hideMenu();
                    }
                    listener.onSubmit(text);
                }
            }
            return false;
        }

        if (type == KeyType.Escape) {
            if (hintBar != null && hintBar.isMenuVisible()) {
                hintBar.hideMenu();
                if (inputBox.getText().startsWith("/")) {
                    inputBox.setText("");
                }
                return false;
            }
            if (waiting.get()) {
                listener.onAbort();
            }
            return false;
        }

        if (type == KeyType.Tab) {
            if (hintBar != null && hintBar.isMenuVisible()) {
                hintBar.selectNext();
                return false;
            }
        }

        if (type == KeyType.ArrowUp) {
            if (hintBar != null && hintBar.isMenuVisible()) {
                hintBar.selectPrevious();
                return false;
            }

            TerminalPosition caret = inputBox.getCaretPosition();
            if (caret != null && caret.getRow() > 0) {
                return true;
            }

            if (commandHistory.hasEntries()) {
                commandHistory.beginNavigation(inputBox.getText());
                if (commandHistory.canGoOlder()) {
                    inputBox.setText(commandHistory.goOlder());
                }
            } else {
                listener.onLineScroll(-scrollStep);
            }
            return false;
        }

        if (type == KeyType.ArrowDown) {
            if (hintBar != null && hintBar.isMenuVisible()) {
                hintBar.selectNext();
                return false;
            }

            TerminalPosition caret = inputBox.getCaretPosition();
            String[] lines = inputBox.getText().split("\r?\n", -1);
            if (caret != null && caret.getRow() < lines.length - 1) {
                return true;
            }

            if (commandHistory.canGoNewer()) {
                inputBox.setText(commandHistory.goNewer());
            } else if (commandHistory.navigating()) {
                inputBox.setText(commandHistory.restoreDraft());
            } else {
                listener.onLineScroll(scrollStep);
            }
            return false;
        }

        if (type == KeyType.PageUp) {
            listener.onPageScroll(-1);
            return false;
        }
        if (type == KeyType.PageDown) {
            listener.onPageScroll(1);
            return false;
        }

        if (commandHistory.navigating() && type != KeyType.ArrowUp && type != KeyType.ArrowDown) {
            commandHistory.resetNavigation();
        }

        return true;
    }
}
