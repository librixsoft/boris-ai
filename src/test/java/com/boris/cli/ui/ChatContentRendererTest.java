package com.boris.cli.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ChatContentRendererTest {

    @Test
    void testColumnToCharIndexSimpleAscii() {
        ChatContentRenderer renderer = new ChatContentRenderer();
        String line = "Hello World";
        assertEquals(0, renderer.columnToCharIndex(line, 0));
        assertEquals(5, renderer.columnToCharIndex(line, 5));
        assertEquals(11, renderer.columnToCharIndex(line, 11));
        assertEquals(11, renderer.columnToCharIndex(line, 20));
    }

    @Test
    void testColumnToCharIndexNullOrEmpty() {
        ChatContentRenderer renderer = new ChatContentRenderer();
        assertEquals(0, renderer.columnToCharIndex(null, 5));
        assertEquals(0, renderer.columnToCharIndex("", 5));
        assertEquals(0, renderer.columnToCharIndex("Hello", -1));
    }

    @Test
    void testDrawComponentWithThinking() {
        ChatContentRenderer renderer = new ChatContentRenderer();
        ChatPanel panel = new ChatPanel();
        panel.setText("● <think>\nThinking line 1\nThinking line 2\n</think>\nNormal response");

        com.googlecode.lanterna.gui2.TextGUIGraphics graphics = org.mockito.Mockito.mock(com.googlecode.lanterna.gui2.TextGUIGraphics.class);
        org.mockito.Mockito.when(graphics.getSize()).thenReturn(new com.googlecode.lanterna.TerminalSize(60, 10));

        renderer.drawComponent(graphics, panel);

        org.mockito.Mockito.verify(graphics, org.mockito.Mockito.atLeastOnce()).setForegroundColor(UiTheme.THINKING);
        org.mockito.Mockito.verify(graphics, org.mockito.Mockito.atLeastOnce()).setForegroundColor(UiTheme.FG);
    }

    @Test
    void testDrawComponentActiveStreamingThinking() {
        ChatContentRenderer renderer = new ChatContentRenderer();
        ChatPanel panel = new ChatPanel();
        // Model is actively streaming its thinking; </think> hasn't arrived yet
        panel.setText("● <think>\nCurrently streaming thought 1\nCurrently streaming thought 2");

        com.googlecode.lanterna.gui2.TextGUIGraphics graphics = org.mockito.Mockito.mock(com.googlecode.lanterna.gui2.TextGUIGraphics.class);
        org.mockito.Mockito.when(graphics.getSize()).thenReturn(new com.googlecode.lanterna.TerminalSize(60, 10));

        renderer.drawComponent(graphics, panel);

        org.mockito.Mockito.verify(graphics, org.mockito.Mockito.atLeastOnce()).setForegroundColor(UiTheme.THINKING);
    }

    @Test
    void testDrawComponentWithScrollingPastOpenTag() {
        ChatContentRenderer renderer = new ChatContentRenderer();
        ChatPanel panel = new ChatPanel();
        // Open tag is at line 0, but viewport is scrolled to line 2
        panel.setText("<think>\nLine 1\nLine 2 (visible)\nLine 3 (visible)\n</think>\nAfter");
        panel.scroll(2);

        com.googlecode.lanterna.gui2.TextGUIGraphics graphics = org.mockito.Mockito.mock(com.googlecode.lanterna.gui2.TextGUIGraphics.class);
        org.mockito.Mockito.when(graphics.getSize()).thenReturn(new com.googlecode.lanterna.TerminalSize(60, 10));

        renderer.drawComponent(graphics, panel);

        org.mockito.Mockito.verify(graphics, org.mockito.Mockito.atLeastOnce()).setForegroundColor(UiTheme.THINKING);
    }
}
