package com.boris.cli.ui;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatPanelSelectionTest {

    private ChatPanel chatPanel;

    @BeforeEach
    void setUp() {
        chatPanel = new ChatPanel();
        chatPanel.setPreferredSize(new TerminalSize(80, 20));
        chatPanel.setText("Hello world\nThis is a test line\nAnother line for selection\nFourth line");
    }

    @Test
    void testInitialState() {
        assertFalse(chatPanel.isSelecting());
        assertFalse(chatPanel.hasSelection());
        assertNull(chatPanel.normalizedRange());
        assertEquals("", chatPanel.getSelectedText());
    }

    @Test
    void testSingleLineSelection() {
        // Line 0: "Hello world"
        // Select from col 0 to col 5 ("Hello")
        chatPanel.onMouseDown(new TerminalPosition(0, 0));
        assertTrue(chatPanel.isSelecting());
        chatPanel.onMouseDrag(new TerminalPosition(5, 0));

        ChatPanel.NormalizedRange range = chatPanel.normalizedRange();
        assertNotNull(range);
        assertEquals(0, range.getStartLine());
        assertEquals(0, range.getStartCol());
        assertEquals(0, range.getEndLine());
        assertEquals(5, range.getEndCol());

        assertEquals("Hello", chatPanel.getSelectedText());

        chatPanel.onMouseUp();
        assertFalse(chatPanel.isSelecting());
        assertTrue(chatPanel.hasSelection());
        assertEquals("Hello", chatPanel.getSelectedText());
    }

    @Test
    void testReverseSingleLineSelection() {
        // Drag from col 5 to col 0 on line 0
        chatPanel.onMouseDown(new TerminalPosition(5, 0));
        chatPanel.onMouseDrag(new TerminalPosition(0, 0));

        ChatPanel.NormalizedRange range = chatPanel.normalizedRange();
        assertNotNull(range);
        assertEquals(0, range.getStartLine());
        assertEquals(0, range.getStartCol());
        assertEquals(0, range.getEndLine());
        assertEquals(5, range.getEndCol());

        assertEquals("Hello", chatPanel.getSelectedText());
    }

    @Test
    void testMultiLineSelection() {
        // From line 0 col 6 ("world") to line 1 col 4 ("This")
        chatPanel.onMouseDown(new TerminalPosition(6, 0));
        chatPanel.onMouseDrag(new TerminalPosition(4, 1));
        chatPanel.onMouseUp();

        assertTrue(chatPanel.hasSelection());
        assertEquals("world\nThis", chatPanel.getSelectedText());
    }

    @Test
    void testMultiLineReverseSelection() {
        // Drag from line 2 col 7 up to line 1 col 5
        chatPanel.onMouseDown(new TerminalPosition(7, 2));
        chatPanel.onMouseDrag(new TerminalPosition(5, 1));
        chatPanel.onMouseUp();

        assertTrue(chatPanel.hasSelection());
        assertEquals("is a test line\nAnother", chatPanel.getSelectedText());
    }

    @Test
    void testCancelSelection() {
        chatPanel.onMouseDown(new TerminalPosition(0, 0));
        chatPanel.onMouseDrag(new TerminalPosition(5, 0));
        assertTrue(chatPanel.isSelecting());

        chatPanel.cancelSelection();
        assertFalse(chatPanel.isSelecting());
        assertFalse(chatPanel.hasSelection());
        assertEquals("", chatPanel.getSelectedText());
    }

    @Test
    void testMouseDownClearsPreviousSelection() {
        chatPanel.onMouseDown(new TerminalPosition(0, 0));
        chatPanel.onMouseDrag(new TerminalPosition(5, 0));
        chatPanel.onMouseUp();
        assertTrue(chatPanel.hasSelection());

        // New mouse down
        chatPanel.onMouseDown(new TerminalPosition(2, 1));
        assertTrue(chatPanel.isSelecting());
        // Since drag hasn't happened yet, range start == end
        ChatPanel.NormalizedRange range = chatPanel.normalizedRange();
        assertNotNull(range);
        assertEquals(1, range.getStartLine());
        assertEquals(2, range.getStartCol());
        assertEquals(1, range.getEndLine());
        assertEquals(2, range.getEndCol());
    }
}
