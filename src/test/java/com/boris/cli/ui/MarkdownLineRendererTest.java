package com.boris.cli.ui;

import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class MarkdownLineRendererTest {

    private MarkdownLineRenderer renderer;
    private TextGUIGraphics graphics;

    @BeforeEach
    void setUp() {
        renderer = new MarkdownLineRenderer();
        graphics = Mockito.mock(TextGUIGraphics.class);
    }

    @Test
    void testUpdateThinkingStateSingleLineTags() {
        // Line with complete <think>...</think> starts false, ends false
        assertFalse(MarkdownLineRenderer.updateThinkingState("<think>thought</think>", false));
        // Line with opening <think> starts false, ends true
        assertTrue(MarkdownLineRenderer.updateThinkingState("<think>start thinking", false));
        // Line with closing </think> starts true, ends false
        assertFalse(MarkdownLineRenderer.updateThinkingState("end thinking</think>", true));
        // Line with no tags preserves state
        assertTrue(MarkdownLineRenderer.updateThinkingState("middle of thinking", true));
        assertFalse(MarkdownLineRenderer.updateThinkingState("middle of text", false));
    }

    @Test
    void testUpdateThinkingStateOtherTags() {
        assertTrue(MarkdownLineRenderer.updateThinkingState("<thought>first step", false));
        assertFalse(MarkdownLineRenderer.updateThinkingState("done step</thought>", true));
        assertTrue(MarkdownLineRenderer.updateThinkingState("<thinking>analysis", false));
        assertFalse(MarkdownLineRenderer.updateThinkingState("done</thinking>", true));
        assertTrue(MarkdownLineRenderer.updateThinkingState("<reasoning>logic", false));
        assertFalse(MarkdownLineRenderer.updateThinkingState("done</reasoning>", true));
    }

    @Test
    void testUpdateThinkingStateCaseInsensitive() {
        assertTrue(MarkdownLineRenderer.updateThinkingState("<THINK>capitalized tag", false));
        assertFalse(MarkdownLineRenderer.updateThinkingState("finished</THINK>", true));
    }

    @Test
    void testParseLineSegmentsMixedLine() {
        String line = "●  <thinking>Let me think</thinking> Here is the answer";
        List<MarkdownLineRenderer.TextSegment> segments = MarkdownLineRenderer.parseLineSegments(line, false);
        assertEquals(3, segments.size());
        assertEquals("●  ", segments.get(0).getText());
        assertFalse(segments.get(0).isThinking());
        assertEquals("Let me think", segments.get(1).getText());
        assertTrue(segments.get(1).isThinking());
        assertEquals(" Here is the answer", segments.get(2).getText());
        assertFalse(segments.get(2).isThinking());
    }

    @Test
    void testRenderNormalLineUsesStandardColors() {
        renderer.render(graphics, "Hello world", 0, 40, false);
        verify(graphics).setForegroundColor(UiTheme.FG);
    }

    @Test
    void testRenderUserLineUsesUserColor() {
        renderer.render(graphics, ">> hola", 0, 40, false);
        verify(graphics).setForegroundColor(UiTheme.USERC);
        verify(graphics).setBackgroundColor(UiTheme.BG);
    }

    @Test
    void testRenderUserContinuationUsesUserColor() {
        renderer.render(graphics, TextWrap.USER_CONT_MARKER + "continuacion", 0, 40, false);
        verify(graphics).setForegroundColor(UiTheme.USERC);
        verify(graphics).setBackgroundColor(UiTheme.BG);
    }

    @Test
    void testRenderThinkingLineUsesThinkingGreyColor() {
        renderer.render(graphics, "Analyzing problem...", 0, 40, true);
        verify(graphics).setForegroundColor(UiTheme.THINKING);
    }

    @Test
    void testRenderThinkingHeadersUsesThinkingBold() {
        renderer.render(graphics, "### Step 1", 0, 40, true);
        verify(graphics).setForegroundColor(UiTheme.THINKING_BOLD);
    }

    @Test
    void testRenderThinkingQuoteUsesThinkingMuted() {
        renderer.render(graphics, "> quote inside thinking", 0, 40, true);
        verify(graphics).setForegroundColor(UiTheme.THINKING_MUTED);
    }

    @Test
    void testRenderThinkingCodeUsesSyntaxColors() {
        renderer.render(graphics, "```java", 0, 40, true);
        // Code fences inside thinking must use syntax highlighting colors, not THINKING grey
        verify(graphics, never()).setForegroundColor(UiTheme.THINKING);
        verify(graphics).setBackgroundColor(UiTheme.BG_ELEVATED);
    }

    @Test
    void testRenderThinkingCodeBlockContentUsesSyntaxColors() {
        // Code block content inside thinking should render with syntax colors via renderCodeLine
        renderer.render(graphics, "public class Hello {", 0, 40, true, true, true);
        ArgumentCaptor<TextColor> captor = ArgumentCaptor.forClass(TextColor.class);
        verify(graphics, atLeastOnce()).setForegroundColor(captor.capture());
        List<TextColor> colors = captor.getAllValues();
        // Dimmed syntax colors must be present; THINKING must not be used for code tokens
        TextColor dimKeyword = new TextColor.RGB(135, 84, 153); // SYNTAX_KEYWORD * 0.6
        TextColor dimType = new TextColor.RGB(54, 135, 138); // SYNTAX_TYPE * 0.6
        assertTrue(colors.stream().anyMatch(c -> rgbEq(c, dimKeyword) || rgbEq(c, dimType)));
        assertFalse(colors.contains(UiTheme.THINKING));
        assertFalse(colors.stream().anyMatch(c -> rgbEq(c, UiTheme.SYNTAX_KEYWORD) || rgbEq(c, UiTheme.SYNTAX_TYPE))); // full brightness not used in thinking
    }

    @Test
    void testRenderInlineCodeInThinkingUsesSyntaxColors() {
        renderer.render(graphics, "Use `System.out.println()` here", 0, 60, true);
        ArgumentCaptor<TextColor> captor = ArgumentCaptor.forClass(TextColor.class);
        verify(graphics, atLeastOnce()).setForegroundColor(captor.capture());
        List<TextColor> colors = captor.getAllValues();
        // Dimmed inline code inside thinking must use syntax highlighting colors
        TextColor dimKeyword = new TextColor.RGB(135, 84, 153); // SYNTAX_KEYWORD * 0.6
        TextColor dimText = new TextColor.RGB(144, 144, 144); // SYNTAX_TEXT * 0.6
        TextColor dimOperator = new TextColor.RGB(129, 132, 138); // SYNTAX_OPERATOR * 0.6
        assertTrue(colors.stream().anyMatch(c -> rgbEq(c, dimKeyword) || rgbEq(c, dimText) || rgbEq(c, dimOperator)));
    }

    private static boolean rgbEq(TextColor a, TextColor b) {
        return a.getRed() == b.getRed() && a.getGreen() == b.getGreen() && a.getBlue() == b.getBlue();
    }

    @Test
    void testRenderThinkingBoldUsesThinkingBoldColor() {
        renderer.render(graphics, "This is **important** step", 0, 40, true);
        verify(graphics, atLeastOnce()).setForegroundColor(UiTheme.THINKING_BOLD);
        verify(graphics, atLeastOnce()).setForegroundColor(UiTheme.THINKING);
    }

    @Test
    void testRenderThinkingListUsesThinkingColor() {
        renderer.render(graphics, "- Item in thinking", 0, 40, true);
        verify(graphics, atLeastOnce()).setForegroundColor(UiTheme.THINKING);
    }

    @Test
    void testRenderMultiSegmentLine() {
        renderer.render(graphics, "Prefix <think>Inside</think> Suffix", 0, 60, false);
        verify(graphics, atLeastOnce()).setForegroundColor(UiTheme.FG);
        verify(graphics, atLeastOnce()).setForegroundColor(UiTheme.THINKING);
    }

    @Test
    void testRenderThinkingDisabledHidesThinkingContent() {
        // When thinkingEnabled is false, thinking segment should not be styled with THINKING color
        renderer.render(graphics, "Prefix <think>Inside</think> Suffix", 0, 60, false, false);
        verify(graphics, atLeastOnce()).setForegroundColor(UiTheme.FG);
        verify(graphics, never()).setForegroundColor(UiTheme.THINKING);
    }

    @Test
    void testRenderFullThinkingLineDisabled() {
        renderer.render(graphics, "<think>Full thinking line</think>", 0, 40, false, false);
        verify(graphics, never()).setForegroundColor(UiTheme.THINKING);
    }

    @Test
    void testRenderSingleSegmentThinkingLineStripsTags() {
        renderer.render(graphics, "<thinking> I am Boris</thinking>", 0, 40, false, true);
        ArgumentCaptor<TextCharacter> captor = ArgumentCaptor.forClass(TextCharacter.class);
        verify(graphics, atLeastOnce()).setCharacter(anyInt(), anyInt(), captor.capture());
        StringBuilder rendered = new StringBuilder();
        for (TextCharacter tc : captor.getAllValues()) {
            rendered.append(tc.getCharacterString());
        }
        assertFalse(rendered.toString().contains("<thinking>"));
        assertFalse(rendered.toString().contains("</thinking>"));
        assertTrue(rendered.toString().contains("I am Boris"));
    }

    @Test
    void testRenderOpeningTagLineStripsTag() {
        renderer.render(graphics, "<thinking>Start of thinking", 0, 40, false, true);
        ArgumentCaptor<TextCharacter> captor = ArgumentCaptor.forClass(TextCharacter.class);
        verify(graphics, atLeastOnce()).setCharacter(anyInt(), anyInt(), captor.capture());
        StringBuilder rendered = new StringBuilder();
        for (TextCharacter tc : captor.getAllValues()) {
            rendered.append(tc.getCharacterString());
        }
        assertFalse(rendered.toString().contains("<thinking>"));
        assertTrue(rendered.toString().contains("Start of thinking"));
    }

    @Test
    void testRenderClosingTagLineStripsTag() {
        renderer.render(graphics, "End of thinking</thinking>", 0, 40, true, true);
        ArgumentCaptor<TextCharacter> captor = ArgumentCaptor.forClass(TextCharacter.class);
        verify(graphics, atLeastOnce()).setCharacter(anyInt(), anyInt(), captor.capture());
        StringBuilder rendered = new StringBuilder();
        for (TextCharacter tc : captor.getAllValues()) {
            rendered.append(tc.getCharacterString());
        }
        assertFalse(rendered.toString().contains("</thinking>"));
        assertTrue(rendered.toString().contains("End of thinking"));
    }

    @Test
    void testRenderAssistantPrefixWithThinkingNoDoubleSpace() {
        List<MarkdownLineRenderer.TextSegment> segs = MarkdownLineRenderer.parseLineSegments(":: <thinking> El usuario", false);
        assertEquals(2, segs.size());
        assertEquals(":: ", segs.get(0).getText());
        assertEquals("El usuario", segs.get(1).getText());
    }

    @Test
    void testEnsureBlankLineAfterThinking() {
        String input = ":: <thinking>razonando</thinking>\nHola";
        assertEquals(":: <thinking>razonando</thinking>\n\nHola",
                MarkdownLineRenderer.ensureBlankLineAfterThinking(input));
    }

    @Test
    void testRenderFileSavedLineUsesGreenBackground() {
        renderer.render(graphics, "─── +5 ✓ /path/to/file guardado ───", 0, 40, false);
        verify(graphics).setBackgroundColor(UiTheme.DIFF_ADD_BG);
        verify(graphics).setForegroundColor(UiTheme.DIFF_ADD);
    }

    @Test
    void testRenderFileSavedLineWithRemovedLinesUsesGreenBackground() {
        renderer.render(graphics, "─── +5 -2 ✓ /path/to/file guardado ───", 0, 40, false);
        verify(graphics).setBackgroundColor(UiTheme.DIFF_ADD_BG);
        verify(graphics).setForegroundColor(UiTheme.DIFF_ADD);
    }

    @Test
    void testRenderFileSavedLineBulkEditUsesGreenBackground() {
        renderer.render(graphics, "─── +5 ✓ guardado ───", 0, 40, false);
        verify(graphics).setBackgroundColor(UiTheme.DIFF_ADD_BG);
        verify(graphics).setForegroundColor(UiTheme.DIFF_ADD);
    }

    @Test
    void testEnsureBlankLineAfterThinkingKeepsConsecutiveBlocksTogether() {
        String input = "<thinking>a</thinking>\n<thinking>b</thinking>\nHola";
        assertEquals("<thinking>a</thinking>\n<thinking>b</thinking>\n\nHola",
                MarkdownLineRenderer.ensureBlankLineAfterThinking(input));
    }
}
