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
}
