package com.boris.cli.ui;

import com.googlecode.lanterna.TextColor;
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
        String line = "● <think>Let me think</think> Here is the answer";
        List<MarkdownLineRenderer.TextSegment> segments = MarkdownLineRenderer.parseLineSegments(line, false);
        assertEquals(3, segments.size());
        assertEquals("● ", segments.get(0).getText());
        assertFalse(segments.get(0).isThinking());
        assertEquals("<think>Let me think</think>", segments.get(1).getText());
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
    void testRenderThinkingCodeUsesThinkingColor() {
        renderer.render(graphics, "```java", 0, 40, true);
        verify(graphics).setForegroundColor(UiTheme.THINKING);
        verify(graphics).setBackgroundColor(UiTheme.BG_ELEVATED);
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
        verify(graphics).setForegroundColor(UiTheme.THINKING);
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
}
