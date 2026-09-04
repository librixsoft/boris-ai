package com.boris.cli.ui;

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

        inputBox = new TextBox(new TerminalSize(1, 1), TextBox.Style.SINGLE_LINE);
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

    private boolean handleKey(Interactable interactable, KeyStroke keyStroke) {
        KeyType type = keyStroke.getKeyType();

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
