package com.boris.llm.think;

import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ThinkingStallDetectorTest {

    private static ThinkingStallDetector detector() {
        return new ThinkingStallDetector(3, 10);
    }

    private static ThinkingStallDetector.ToolCallSignature listFiles(String path) {
        return new ThinkingStallDetector.ToolCallSignature("list_files", "{\"path\":\"" + path + "\"}");
    }

    @Test
    void sameToolSameArgsConsecutive_detectsStallAfterThreeIterations() {
        ThinkingStallDetector d = detector();

        assertFalse(d.recordThinking("", List.of(listFiles("/Users/..."))));
        assertFalse(d.recordThinking("", List.of(listFiles("/Users/..."))));
        assertFalse(d.recordThinking("", List.of(listFiles("/Users/..."))));
        assertTrue(d.recordThinking("", List.of(listFiles("/Users/..."))));
        assertTrue(d.isStallDetected());
        assertEquals(3, d.getConsecutiveNonProgressiveIterations());
    }

    @Test
    void sameToolDifferentArgs_noStallProgressIsReal() {
        ThinkingStallDetector d = detector();

        assertFalse(d.recordThinking("", List.of(listFiles("/Users/..."))));
        assertFalse(d.recordThinking("", List.of(listFiles("/other/..."))));
        assertFalse(d.recordThinking("", List.of(listFiles("/Users/..."))));
        assertFalse(d.isStallDetected());
        assertEquals(0, d.getConsecutiveNonProgressiveIterations());
    }

    @Test
    void differentTool_resetsCounter() {
        ThinkingStallDetector d = detector();

        assertFalse(d.recordThinking("", List.of(listFiles("/Users/..."))));
        assertFalse(d.recordThinking("", List.of(new ThinkingStallDetector.ToolCallSignature("read_file", "{\"path\":\"/Users/foo.txt\"}"))));
        assertFalse(d.isStallDetected());
        assertEquals(0, d.getConsecutiveNonProgressiveIterations());
    }

    @Test
    void toolCallWithDifferentArgs_breaksStallStreak() {
        ThinkingStallDetector d = detector();

        // Two identical calls start a streak
        assertFalse(d.recordThinking("", List.of(listFiles("/Users/..."))));
        assertFalse(d.recordThinking("", List.of(listFiles("/Users/..."))));
        assertEquals(1, d.getConsecutiveNonProgressiveIterations());

        // A call with different args breaks the streak
        assertFalse(d.recordThinking("", List.of(listFiles("/other/..."))));
        assertEquals(0, d.getConsecutiveNonProgressiveIterations());

        // Streak restarts from zero
        assertFalse(d.recordThinking("", List.of(listFiles("/Users/..."))));
        assertEquals(0, d.getConsecutiveNonProgressiveIterations());
        assertFalse(d.isStallDetected());
    }

    @Test
    void thinkingOnlyRepetitive_detectsStall() {
        ThinkingStallDetector d = detector();

        assertFalse(d.recordThinking("same thinking content", null));
        assertFalse(d.recordThinking("same thinking content", null));
        assertFalse(d.recordThinking("same thinking content", null));
        assertTrue(d.recordThinking("same thinking content", null));
        assertTrue(d.isStallDetected());
    }

    @Test
    void thinkingOnlyDifferentContent_noStall() {
        ThinkingStallDetector d = detector();

        assertFalse(d.recordThinking("thinking one", null));
        assertFalse(d.recordThinking("thinking two", null));
        assertFalse(d.isStallDetected());
        assertEquals(0, d.getConsecutiveNonProgressiveIterations());
    }

    @Test
    void reset_clearsState() {
        ThinkingStallDetector d = detector();

        d.recordThinking("", List.of(listFiles("/Users/...")));
        d.recordThinking("", List.of(listFiles("/Users/...")));
        d.recordThinking("", List.of(listFiles("/Users/...")));
        assertEquals(2, d.getConsecutiveNonProgressiveIterations());

        d.reset();
        assertEquals(0, d.getConsecutiveNonProgressiveIterations());
        assertFalse(d.isStallDetected());

        // After reset, first identical call should not count as non-progressive
        assertFalse(d.recordThinking("", List.of(listFiles("/Users/..."))));
        assertEquals(0, d.getConsecutiveNonProgressiveIterations());
    }
}
