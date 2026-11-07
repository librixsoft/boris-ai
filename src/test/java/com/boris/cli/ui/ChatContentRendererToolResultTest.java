package com.boris.cli.ui;

import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class ChatContentRendererToolResultTest {

    @Test
    void testRenderToolResultLineUsesToolDetailColor() {
        ChatContentRenderer renderer = new ChatContentRenderer();
        ChatPanel panel = new ChatPanel();
        panel.setText("{\"tool\": \"delete_file\", \"path\": \"/Users/lastprophet/Downloads/hola.md\", \"success\": true}");

        TextGUIGraphics graphics = Mockito.mock(TextGUIGraphics.class);
        Mockito.when(graphics.getSize()).thenReturn(new com.googlecode.lanterna.TerminalSize(60, 10));

        renderer.drawComponent(graphics, panel);

        Mockito.verify(graphics, atLeastOnce()).setForegroundColor(UiTheme.TOOL_DETAIL);
    }

    @Test
    void testRenderToolResultDoesNotContainRawJson() {
        ChatContentRenderer renderer = new ChatContentRenderer();
        ChatPanel panel = new ChatPanel();
        panel.setText("{\"tool\": \"delete_file\", \"path\": \"/Users/lastprophet/Downloads/hola.md\", \"success\": true}");

        TextGUIGraphics graphics = Mockito.mock(TextGUIGraphics.class);
        Mockito.when(graphics.getSize()).thenReturn(new com.googlecode.lanterna.TerminalSize(60, 10));

        ArgumentCaptor<TextCharacter> captor = ArgumentCaptor.forClass(TextCharacter.class);
        renderer.drawComponent(graphics, panel);

        verify(graphics, atLeastOnce()).setCharacter(anyInt(), anyInt(), captor.capture());

        StringBuilder rendered = new StringBuilder();
        for (TextCharacter tc : captor.getAllValues()) {
            rendered.append(tc.getCharacterString());
        }

        assertFalse(rendered.toString().contains("{\"tool\""));
        assertFalse(rendered.toString().contains("\"success\""));
        assertFalse(rendered.toString().contains("\"path\""));
        assertTrue(rendered.toString().contains("delete_file"));
        assertTrue(rendered.toString().contains("/Users/lastprophet/Downloads/hola.md"));
    }

    @Test
    void testRenderToolResultInThinkingBlock() {
        ChatContentRenderer renderer = new ChatContentRenderer();
        ChatPanel panel = new ChatPanel();
        panel.setText("●  <thinking>\nAnalyzing...\n{\"tool\": \"delete_file\", \"path\": \"/tmp/x\", \"success\": true}\n</thinking>\nDone");

        TextGUIGraphics graphics = Mockito.mock(TextGUIGraphics.class);
        Mockito.when(graphics.getSize()).thenReturn(new com.googlecode.lanterna.TerminalSize(60, 10));

        renderer.drawComponent(graphics, panel);

        Mockito.verify(graphics, atLeastOnce()).setForegroundColor(UiTheme.TOOL_DETAIL);
        Mockito.verify(graphics, atLeastOnce()).setForegroundColor(UiTheme.THINKING);
    }

    @Test
    void testRenderMultipleToolResultLines() {
        ChatContentRenderer renderer = new ChatContentRenderer();
        ChatPanel panel = new ChatPanel();
        panel.setText("{\"tool\": \"write_file\", \"path\": \"/tmp/a.txt\", \"success\": true}\n{\"tool\": \"delete_file\", \"path\": \"/tmp/b.txt\", \"success\": true}");

        TextGUIGraphics graphics = Mockito.mock(TextGUIGraphics.class);
        Mockito.when(graphics.getSize()).thenReturn(new com.googlecode.lanterna.TerminalSize(60, 10));

        renderer.drawComponent(graphics, panel);

        Mockito.verify(graphics, atLeastOnce()).setForegroundColor(UiTheme.TOOL_DETAIL);
    }
}
