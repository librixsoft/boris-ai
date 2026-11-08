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

    @Test
    void testRenderToolResultWithHtmlInPathDoesNotLeakRawJsonOrHtml() {
        MarkdownLineRenderer renderer = new MarkdownLineRenderer();
        TextGUIGraphics graphics = Mockito.mock(TextGUIGraphics.class);

        ArgumentCaptor<TextCharacter> captor = ArgumentCaptor.forClass(TextCharacter.class);
        // HTML code inline in the path value
        renderer.render(graphics, "{\"tool\": \"write_file\", \"path\": \"<h1>Hola Mundo</h1>\", \"success\": true}", 0, 60, false);

        verify(graphics, atLeastOnce()).setForegroundColor(UiTheme.TOOL_DETAIL);
        verify(graphics, atLeastOnce()).setCharacter(anyInt(), anyInt(), captor.capture());

        StringBuilder rendered = new StringBuilder();
        for (TextCharacter tc : captor.getAllValues()) {
            rendered.append(tc.getCharacterString());
        }

        // Raw JSON must not leak
        assertFalse(rendered.toString().contains("{\"tool\""));
        assertFalse(rendered.toString().contains("\"success\""));
        assertFalse(rendered.toString().contains("\"path\""));
        // HTML code must not leak
        assertFalse(rendered.toString().contains("<h1>Hola Mundo</h1>"));
        // Safe invalid path display must be present
        assertTrue(rendered.toString().contains("Invalid tool result path"));
    }

    @Test
    void testRenderToolResultWithPathOnly() {
        MarkdownLineRenderer renderer = new MarkdownLineRenderer();
        TextGUIGraphics graphics = Mockito.mock(TextGUIGraphics.class);

        renderer.render(graphics, "{\"path\": \"/Users/lastprophet/Downloads/hola.md\", \"success\": true}", 0, 60, false);

        Mockito.verify(graphics, atLeastOnce()).setForegroundColor(UiTheme.TOOL_DETAIL);
    }

    @Test
    void testRenderToolResultInThinkingBlock() {
        MarkdownLineRenderer renderer = new MarkdownLineRenderer();
        TextGUIGraphics graphics = Mockito.mock(TextGUIGraphics.class);

        // Line entirely within thinking block
        renderer.render(graphics, "{\"tool\": \"delete_file\", \"path\": \"/tmp/x\", \"success\": true}", 0, 60, true);

        Mockito.verify(graphics, atLeastOnce()).setForegroundColor(UiTheme.TOOL_DETAIL);
    }

    @Test
    void testRenderMultipleToolResultLines() {
        MarkdownLineRenderer renderer = new MarkdownLineRenderer();
        TextGUIGraphics graphics = Mockito.mock(TextGUIGraphics.class);

        renderer.render(graphics, "{\"tool\": \"write_file\", \"path\": \"/tmp/a.txt\", \"success\": true}", 0, 60, false);
        renderer.render(graphics, "{\"tool\": \"delete_file\", \"path\": \"/tmp/b.txt\", \"success\": true}", 0, 60, false);

        Mockito.verify(graphics, atLeastOnce()).setForegroundColor(UiTheme.TOOL_DETAIL);
    }

    @Test
    void testRenderToolResultWithBackticksDoesNotFallbackToInlineCode() {
        MarkdownLineRenderer renderer = new MarkdownLineRenderer();
        TextGUIGraphics graphics = Mockito.mock(TextGUIGraphics.class);

        // JSON containing backticks should NOT be rendered as inline code (FG color)
        // It should be rendered as tool result (TOOL_DETAIL color)
        renderer.render(graphics, "{\"tool\": \"write_file\", \"path\": \"/tmp/file`name.txt\", \"success\": true}", 0, 60, false);

        Mockito.verify(graphics, atLeastOnce()).setForegroundColor(UiTheme.TOOL_DETAIL);
        // Verify inline code renderer was NOT used (no BG_ELEVATED for inline code)
        verify(graphics, never()).setBackgroundColor(UiTheme.BG_ELEVATED);
    }
}
