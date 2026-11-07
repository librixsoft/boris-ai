package com.boris.cli.ui;

import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class MarkdownLineRendererToolResultTest {

    @Test
    void testRenderToolResultLineDirectly() {
        MarkdownLineRenderer renderer = new MarkdownLineRenderer();
        TextGUIGraphics graphics = Mockito.mock(TextGUIGraphics.class);

        renderer.render(graphics, "{\"tool\": \"delete_file\", \"path\": \"/tmp/x\", \"success\": true}", 0, 60, false);

        Mockito.verify(graphics, atLeastOnce()).setForegroundColor(UiTheme.TOOL_DETAIL);
    }

    @Test
    void testRenderToolResultLineDirectlyCapturesOutput() {
        MarkdownLineRenderer renderer = new MarkdownLineRenderer();
        TextGUIGraphics graphics = Mockito.mock(TextGUIGraphics.class);

        ArgumentCaptor<TextCharacter> captor = ArgumentCaptor.forClass(TextCharacter.class);
        renderer.render(graphics, "{\"tool\": \"delete_file\", \"path\": \"/tmp/x\", \"success\": true}", 0, 60, false);

        verify(graphics, atLeastOnce()).setCharacter(anyInt(), anyInt(), captor.capture());

        StringBuilder rendered = new StringBuilder();
        for (TextCharacter tc : captor.getAllValues()) {
            rendered.append(tc.getCharacterString());
        }

        assertFalse(rendered.toString().contains("{\"tool\""));
        assertFalse(rendered.toString().contains("\"success\""));
        assertFalse(rendered.toString().contains("\"path\""));
        assertTrue(rendered.toString().contains("delete_file"));
        assertTrue(rendered.toString().contains("/tmp/x"));
    }
}
