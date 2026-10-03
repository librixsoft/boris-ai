package com.boris.cli.ui;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.gui2.TextBox;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class InputAreaTest {

    private CommandHistory commandHistory;
    private AtomicBoolean waiting;
    private List<String> submitted;
    private InputArea.InputListener listener;
    private InputArea inputArea;

    @BeforeEach
    void setUp() {
        commandHistory = new CommandHistory();
        waiting = new AtomicBoolean(false);
        submitted = new ArrayList<>();
        listener = new InputArea.InputListener() {
            @Override
            public void onSubmit(String text) {
                submitted.add(text);
            }

            @Override
            public void onAbort() {
            }

            @Override
            public void onLineScroll(int deltaLines) {
            }

            @Override
            public void onPageScroll(int direction) {
            }
        };

        inputArea = new InputArea(commandHistory, waiting, 3, listener);
    }

    @Test
    void enter_submitsTextAndClearsInput() {
        TextBox box = inputArea.getTextBox();
        box.setText("hello world");

        KeyStroke enterKey = new KeyStroke(KeyType.Enter);
        box.handleInput(enterKey);

        assertEquals(1, submitted.size());
        assertEquals("hello world", submitted.get(0));
        assertEquals("", box.getText());
    }

    @Test
    void ctrlEnter_insertsNewlineWithoutSubmitting() {
        TextBox box = inputArea.getTextBox();
        box.setText("line1");
        box.setCaretPosition(0, 5);

        KeyStroke ctrlEnter = new KeyStroke(KeyType.Enter, true, false);
        box.handleInput(ctrlEnter);

        assertEquals(0, submitted.size(), "Ctrl+Enter should not submit");
        assertTrue(box.getText().contains("\n"), "Text should contain newline");
        assertEquals("line1\n", box.getText());
    }

    @Test
    void multilinePaste_insertsFullTextAtCaret() {
        TextBox box = inputArea.getTextBox();
        box.setText("start end");
        box.setCaretPosition(0, 5);

        inputArea.insertTextAtCaret("middle1\nmiddle2 ");

        assertEquals("startmiddle1\nmiddle2  end", box.getText());
        assertEquals(0, submitted.size(), "Pasting should not trigger immediate submit");
    }

    @Test
    void submit_withMultilineInput_submitsEntireMultilineContent() {
        TextBox box = inputArea.getTextBox();
        box.setText("line1\nline2\nline3");

        KeyStroke enterKey = new KeyStroke(KeyType.Enter);
        box.handleInput(enterKey);

        assertEquals(1, submitted.size());
        assertEquals("line1\nline2\nline3", submitted.get(0));
        assertEquals("", box.getText());
    }

    @Test
    void pasteBurst_withNewlines_doesNotSubmitPrematurely() {
        TextBox box = inputArea.getTextBox();
        box.setText("");

        // Simulate rapid keystrokes coming from a terminal paste stream
        box.handleInput(new KeyStroke('E', false, false));
        box.handleInput(new KeyStroke('j', false, false));
        // Rapid Enter (paste burst)
        box.handleInput(new KeyStroke(KeyType.Enter));
        box.handleInput(new KeyStroke('1', false, false));

        assertEquals(0, submitted.size(), "Paste burst Enter should NOT submit prematurely");
        assertTrue(box.getText().contains("\n"), "Text should contain newline from paste");
    }
}
