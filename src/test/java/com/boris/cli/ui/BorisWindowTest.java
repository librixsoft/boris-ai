package com.boris.cli.ui;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.input.MouseActionType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BorisWindowTest {

    private ChatPanel chatPanel;
    private AtomicInteger scrolled;
    private BorisWindow window;

    @BeforeEach
    void setUp() {
        chatPanel = new ChatPanel();
        chatPanel.setPreferredSize(new TerminalSize(80, 20));
        chatPanel.setText("First line\nSecond line\nThird line");
        scrolled = new AtomicInteger(0);
        window = new BorisWindow(chatPanel, 3, delta -> scrolled.addAndGet(delta));
    }

    @Test
    void testScrollEvents() {
        MouseAction scrollUp = new MouseAction(MouseActionType.SCROLL_UP, 0, new TerminalPosition(10, 5));
        boolean handledUp = window.handleInput(scrollUp);
        assertTrue(handledUp);
        assertEquals(-3, scrolled.get());

        MouseAction scrollDown = new MouseAction(MouseActionType.SCROLL_DOWN, 0, new TerminalPosition(10, 5));
        boolean handledDown = window.handleInput(scrollDown);
        assertTrue(handledDown);
        assertEquals(0, scrolled.get());
    }

    @Test
    void testMouseDragSelectionDelegation() {
        MouseAction clickDown = new MouseAction(MouseActionType.CLICK_DOWN, 1, new TerminalPosition(0, 0));
        boolean handledDown = window.handleInput(clickDown);
        assertTrue(handledDown);
        assertTrue(chatPanel.isSelecting());

        MouseAction drag = new MouseAction(MouseActionType.DRAG, 1, new TerminalPosition(5, 0));
        boolean handledDrag = window.handleInput(drag);
        assertTrue(handledDrag);
        assertTrue(chatPanel.isSelecting());
        assertEquals("First", chatPanel.getSelectedText());

        MouseAction release = new MouseAction(MouseActionType.CLICK_RELEASE, 0, new TerminalPosition(5, 0));
        boolean handledRelease = window.handleInput(release);
        assertTrue(handledRelease);
        assertFalse(chatPanel.isSelecting());
        assertTrue(chatPanel.hasSelection());
        assertEquals("First", chatPanel.getSelectedText());
    }

    @Test
    void testMoveDoesNotCancelSelectionWhileSelecting() {
        MouseAction clickDown = new MouseAction(MouseActionType.CLICK_DOWN, 1, new TerminalPosition(0, 0));
        window.handleInput(clickDown);
        MouseAction drag = new MouseAction(MouseActionType.DRAG, 1, new TerminalPosition(5, 0));
        window.handleInput(drag);

        // Movement event should not cancel selection
        MouseAction move = new MouseAction(MouseActionType.MOVE, 0, new TerminalPosition(5, 0));
        window.handleInput(move);
        assertTrue(chatPanel.isSelecting());
        assertTrue(chatPanel.hasSelection());

        MouseAction release = new MouseAction(MouseActionType.CLICK_RELEASE, 0, new TerminalPosition(5, 0));
        window.handleInput(release);
        assertFalse(chatPanel.isSelecting());
        assertTrue(chatPanel.hasSelection());
        assertEquals("First", chatPanel.getSelectedText());
    }

    @Test
    void testEscapeCancelsSelection() {
        chatPanel.onMouseDown(new TerminalPosition(0, 0));
        chatPanel.onMouseDrag(new TerminalPosition(5, 0));
        chatPanel.onMouseUp();
        assertTrue(chatPanel.hasSelection());

        KeyStroke escape = new KeyStroke(KeyType.Escape);
        window.handleInput(escape);
        assertFalse(chatPanel.hasSelection());
    }
}
