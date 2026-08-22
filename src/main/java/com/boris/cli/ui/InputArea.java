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

    public InputArea(CommandHistory commandHistory, AtomicBoolean waiting, int scrollStep, InputListener listener) {
        super(new BorderLayout());
        this.commandHistory = commandHistory;
        this.waiting = waiting;
        this.scrollStep = scrollStep;
        this.listener = listener;

        Label promptLabel = new Label("❯ ");
        promptLabel.setForegroundColor(UiTheme.USERC);
        addComponent(promptLabel, BorderLayout.Location.LEFT);

        inputBox = new TextBox(new TerminalSize(1, 1), TextBox.Style.SINGLE_LINE);
        addComponent(inputBox, BorderLayout.Location.CENTER);

        inputBox.setInputFilter(this::handleKey);
    }

    public TextBox getTextBox() {
        return inputBox;
    }

    private boolean handleKey(Interactable interactable, KeyStroke keyStroke) {
        KeyType type = keyStroke.getKeyType();

        if (type == KeyType.Enter) {
            if (!waiting.get()) {
                String text = inputBox.getText().trim();
                if (!text.isEmpty()) {
                    inputBox.setText("");
                    listener.onSubmit(text);
                }
            }
            return false;
        }

        if (type == KeyType.Escape) {
            if (waiting.get()) {
                listener.onAbort();
            }
            return false;
        }

        if (type == KeyType.ArrowUp) {
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
