package com.boris.llm.think;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ToolProgressGuardTest {

    @Test
    void sameResultThreeTimesTriggersLoop() {
        ToolProgressGuard guard = new ToolProgressGuard();

        assertFalse(guard.recordResult("list_files", "{\"path\":\"/x\"}", "{\"success\":true}"));
        assertFalse(guard.recordResult("list_files", "{\"path\":\"/x\"}", "{\"success\":true}"));
        assertTrue(guard.recordResult("list_files", "{\"path\":\"/x\"}", "{\"success\":true}"));
    }

    @Test
    void differentResultResetsCounter() {
        ToolProgressGuard guard = new ToolProgressGuard();

        assertFalse(guard.recordResult("list_files", "{\"path\":\"/x\"}", "{\"success\":true}"));
        assertFalse(guard.recordResult("list_files", "{\"path\":\"/x\"}", "{\"success\":true}"));
        assertFalse(guard.recordResult("list_files", "{\"path\":\"/x\"}", "{\"success\":false,\"error\":\"not found\"}"));
        assertFalse(guard.recordResult("list_files", "{\"path\":\"/x\"}", "{\"success\":true}"));
    }

    @Test
    void differentArgsDoesNotTrigger() {
        ToolProgressGuard guard = new ToolProgressGuard();

        assertFalse(guard.recordResult("list_files", "{\"path\":\"/a\"}", "{\"success\":true}"));
        assertFalse(guard.recordResult("list_files", "{\"path\":\"/b\"}", "{\"success\":true}"));
        assertFalse(guard.recordResult("list_files", "{\"path\":\"/a\"}", "{\"success\":true}"));
    }

    @Test
    void resetClearsState() {
        ToolProgressGuard guard = new ToolProgressGuard();

        guard.recordResult("list_files", "{\"path\":\"/x\"}", "{\"success\":true}");
        guard.recordResult("list_files", "{\"path\":\"/x\"}", "{\"success\":true}");
        guard.reset();
        assertFalse(guard.recordResult("list_files", "{\"path\":\"/x\"}", "{\"success\":true}"));
    }

    @Test
    void nullResultIsHandled() {
        ToolProgressGuard guard = new ToolProgressGuard();

        assertFalse(guard.recordResult("list_files", "{\"path\":\"/x\"}", null));
        assertFalse(guard.recordResult("list_files", "{\"path\":\"/x\"}", null));
        assertTrue(guard.recordResult("list_files", "{\"path\":\"/x\"}", null));
    }

    @Test
    void browserResultsWithSameProgressTokenTriggerLoopEvenWithCosmeticArgVariations() {
        ToolProgressGuard guard = new ToolProgressGuard();
        String unchangedResult = "{\"status\":\"failed\",\"action\":\"scroll\","
                + "\"screen_hash_after\":\"HASH1\",\"url_after\":\"https://linkedin.com/feed/\"}";

        assertFalse(guard.recordResult("browser_ui_action",
                "{\"action\":\"scroll\",\"scroll_direction\":\"down\"}", unchangedResult));
        assertFalse(guard.recordResult("browser_ui_action",
                "{\"scroll_direction\":\"down\",\"action\":\"scroll\"}", unchangedResult));
        assertTrue(guard.recordResult("browser_ui_action",
                "{\"action\":\"scroll\",\"scroll_direction\":\"down\"}", unchangedResult));
    }

    @Test
    void browserResultsWithDifferentScreenHashDoNotTriggerLoop() {
        ToolProgressGuard guard = new ToolProgressGuard();

        for (int i = 0; i < 5; i++) {
            String result = "{\"status\":\"success\",\"action\":\"scroll\","
                    + "\"screen_hash_after\":\"HASH" + i + "\",\"url_after\":\"https://linkedin.com/feed/\"}";
            assertFalse(guard.recordResult("browser_ui_action",
                    "{\"action\":\"scroll\",\"scroll_direction\":\"down\"}", result));
        }
    }

    @Test
    void browserResultsWithSameScreenHashButDifferentUrlDoNotTriggerLoop() {
        ToolProgressGuard guard = new ToolProgressGuard();

        for (int i = 0; i < 5; i++) {
            String result = "{\"status\":\"success\",\"action\":\"click_text\","
                    + "\"screen_hash_after\":\"SAMEHASH\",\"url_after\":\"https://linkedin.com/p" + i + "\"}";
            assertFalse(guard.recordResult("browser_ui_action",
                    "{\"action\":\"click_text\",\"click_target_text\":\"Siguiente\"}", result));
        }
    }
}
