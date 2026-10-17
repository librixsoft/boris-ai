package com.boris.cli.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TextWrapTest {

    @Test
    void wrapDiffLine_shortLine_noWrap() {
        String line = "  10 │+ short code";
        String result = TextWrap.wrap(line, 80);
        assertEquals(line, result);
    }

    @Test
    void wrapDiffLine_longLine_wrapsWithContinuation() {
        String line = "  10 │+ this is a very long line of code that should be wrapped to multiple lines";
        String result = TextWrap.wrap(line, 40);

        String[] lines = result.split("\n");
        assertTrue(lines.length > 1, "Should wrap to multiple lines");
        assertTrue(lines[0].startsWith("  10 │+ "), "First line keeps prefix");
        assertTrue(lines[1].startsWith(TextWrap.DIFF_CONT_MARKER), "Continuation has marker");
    }

    @Test
    void wrapDiffLine_removeLine_preservesSymbol() {
        String line = "   5 │- removed code that is very long and needs to wrap";
        String result = TextWrap.wrap(line, 35);

        String[] lines = result.split("\n");
        assertTrue(lines.length > 1);
        assertTrue(lines[1].startsWith(TextWrap.DIFF_CONT_MARKER + "-"), "Continuation preserves - symbol");
    }

    @Test
    void wrapSimpleDiffLine_plus() {
        String line = "+ this is added code that is quite long";
        String result = TextWrap.wrap(line, 25);

        String[] lines = result.split("\n");
        assertTrue(lines.length > 1, "Should wrap to multiple lines");
        assertTrue(lines[0].startsWith("+ "), "First line keeps + prefix");
        assertTrue(lines[0].length() <= 25, "First line respects width");
        assertTrue(lines[1].startsWith(TextWrap.DIFF_CONT_MARKER + "+"), "Continuation has marker and symbol");
    }

    @Test
    void wrapDiffHeader_truncatesWithEllipsis() {
        String line = "@@@ /very/long/path/to/file/that/exceeds/width.java @@@";
        String result = TextWrap.wrap(line, 30);

        assertTrue(result.endsWith("..."), "Should truncate with ellipsis");
        assertFalse(result.contains("\n"), "Should not wrap headers");
    }

    @Test
    void wrapNormalText_stillWorks() {
        String text = "This is normal text that should wrap at word boundaries";
        String result = TextWrap.wrap(text, 20);

        String[] lines = result.split("\n");
        assertTrue(lines.length > 1);
        for (String l : lines) {
            assertTrue(l.length() <= 20);
        }
    }

    @Test
    void diffContMarker_isInvisible() {
        // The marker uses zero-width space, should not affect visible length
        assertEquals(2, TextWrap.DIFF_CONT_MARKER.length());
    }
}
