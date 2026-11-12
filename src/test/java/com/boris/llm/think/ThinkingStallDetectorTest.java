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
    void jsonWhitespaceAndKeyOrderVariants_detectStall() {
        ThinkingStallDetector d = detector();

        ThinkingStallDetector.ToolCallSignature first =
                new ThinkingStallDetector.ToolCallSignature("list_files", "{\"path\": \"/Users/x\", \"recursive\": true}");
        ThinkingStallDetector.ToolCallSignature second =
                new ThinkingStallDetector.ToolCallSignature("list_files", "{\"recursive\":true,\"path\":\"/Users/x\"}");
        ThinkingStallDetector.ToolCallSignature third =
                new ThinkingStallDetector.ToolCallSignature("list_files", "{ \"recursive\" : true , \"path\" : \"/Users/x\" }");

        assertFalse(d.recordThinking("", List.of(first)));
        assertFalse(d.recordThinking("", List.of(second)));
        assertEquals(1, d.getConsecutiveNonProgressiveIterations());
        assertFalse(d.recordThinking("", List.of(third)));
        assertTrue(d.recordThinking("", List.of(first)));
        assertTrue(d.isStallDetected());
    }

    @Test
    void jsonNumberRepresentationVariants_detectStall() {
        ThinkingStallDetector d = detector();

        ThinkingStallDetector.ToolCallSignature integerForm =
                new ThinkingStallDetector.ToolCallSignature("list_files", "{\"path\":\"/x\",\"max_depth\":5}");
        ThinkingStallDetector.ToolCallSignature doubleForm =
                new ThinkingStallDetector.ToolCallSignature("list_files", "{\"max_depth\":5.0,\"path\":\"/x\"}");
        ThinkingStallDetector.ToolCallSignature spacedForm =
                new ThinkingStallDetector.ToolCallSignature("list_files", "{ \"path\": \"/x\", \"max_depth\": 5 }");

        assertFalse(d.recordThinking("", List.of(integerForm)));
        assertFalse(d.recordThinking("", List.of(doubleForm)));
        assertFalse(d.recordThinking("", List.of(spacedForm)));
        assertTrue(d.recordThinking("", List.of(integerForm)));
        assertTrue(d.isStallDetected());
    }

    @Test
    void jsonDistinctValuesAfterCanonicalization_noStall() {
        ThinkingStallDetector d = detector();

        assertFalse(d.recordThinking("", List.of(listFiles("/Users/x"))));
        assertFalse(d.recordThinking("", List.of(listFiles("/Users/y"))));
        assertFalse(d.isStallDetected());
        assertEquals(0, d.getConsecutiveNonProgressiveIterations());
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

    @Test
    void sameToolNameConsecutiveExceedsLimit_detectsStall() {
        ThinkingStallDetector d = new ThinkingStallDetector(3, 10, 3);

        assertFalse(d.recordThinking("", List.of(new ThinkingStallDetector.ToolCallSignature("bash", "{\"command\":\"find -maxdepth 3\"}"))));
        assertFalse(d.recordThinking("", List.of(new ThinkingStallDetector.ToolCallSignature("bash", "{\"command\":\"find -maxdepth 4\"}"))));
        assertFalse(d.recordThinking("", List.of(new ThinkingStallDetector.ToolCallSignature("bash", "{\"command\":\"find -maxdepth 5\"}"))));
        assertTrue(d.recordThinking("", List.of(new ThinkingStallDetector.ToolCallSignature("bash", "{\"command\":\"find -maxdepth 6\"}"))));
        assertTrue(d.isStallDetected());
    }
}
