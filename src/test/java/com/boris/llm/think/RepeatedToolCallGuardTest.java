package com.boris.llm.think;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepeatedToolCallGuardTest {

    private static final int MAX_EXECUTIONS_PER_SIGNATURE = 1;

    private static RepeatedToolCallGuard guard() {
        return new RepeatedToolCallGuard(MAX_EXECUTIONS_PER_SIGNATURE);
    }

    @Test
    void identicalCallsWithinLimit_doNotExceed() {
        RepeatedToolCallGuard g = guard();

        assertFalse(g.recordExecution("list_files", "{\"path\":\"/x\"}"));
        assertTrue(g.recordExecution("list_files", "{\"path\":\"/x\"}"));
    }

    @Test
    void secondIdenticalCall_exceeds() {
        RepeatedToolCallGuard g = guard();

        g.recordExecution("list_files", "{\"path\":\"/x\"}");
        assertTrue(g.recordExecution("list_files", "{\"path\":\"/x\"}"));
    }

    @Test
    void formatVariantsCountTowardSameSignature() {
        RepeatedToolCallGuard g = guard();

        g.recordExecution("list_files", "{\"path\": \"/x\", \"max_depth\": 5}");
        assertTrue(g.recordExecution("list_files", "{\"max_depth\":5.0,\"path\":\"/x\"}"));
    }

    @Test
    void identicalArguments_blocked_butDistinctArgumentsAllowed() {
        RepeatedToolCallGuard g = guard();

        assertFalse(g.recordExecution("list_files", "{\"path\":\"/x\"}"));
        assertTrue(g.recordExecution("list_files", "{\"path\":\"/x\"}"));
        // 3rd call exceeds MAX_EXECUTIONS_PER_TOOL_NAME = 2
        assertTrue(g.recordExecution("list_files", "{\"path\":\"/y\"}"));
    }

    @Test
    void distinctToolsHaveIndependentCounters() {
        RepeatedToolCallGuard g = guard();

        assertFalse(g.recordExecution("list_files", "{\"path\":\"/x\"}"));
        assertTrue(g.recordExecution("list_files", "{\"path\":\"/x\"}"));
        assertFalse(g.recordExecution("read_file", "{\"path\":\"/x\"}"));
    }

    @Test
    void sameToolWithVaryingArgs_exceedsPerToolNameCap() {
        RepeatedToolCallGuard g = guard();

        assertFalse(g.recordExecution("list_files", "{\"path\":\"/a\"}"));
        assertFalse(g.recordExecution("list_files", "{\"path\":\"/b\"}"));
        assertTrue(g.recordExecution("list_files", "{\"path\":\"/c\"}"));
    }

    @Test
    void otherToolNameNotAffectedByCapOfAnotherTool() {
        RepeatedToolCallGuard g = guard();

        assertFalse(g.recordExecution("list_files", "{\"path\":\"/dir0\"}"));
        assertFalse(g.recordExecution("list_files", "{\"path\":\"/dir1\"}"));
        // 3rd call exceeds MAX_EXECUTIONS_PER_TOOL_NAME = 2
        assertTrue(g.recordExecution("list_files", "{\"path\":\"/dir2\"}"));
        assertFalse(g.recordExecution("read_file", "{\"path\":\"Foo.java\"}"));
    }

    @Test
    void consecutiveExecutionsSameTool_exceedsConsecutiveCap() {
        RepeatedToolCallGuard g = new RepeatedToolCallGuard(MAX_EXECUTIONS_PER_SIGNATURE, 3);

        assertFalse(g.recordExecution("bash", "{\"command\":\"cmd1\"}"));
        assertFalse(g.recordExecution("bash", "{\"command\":\"cmd2\"}"));
        // 3rd call exceeds MAX_EXECUTIONS_PER_TOOL_NAME = 2
        assertTrue(g.recordExecution("bash", "{\"command\":\"cmd3\"}"));
    }

    @Test
    void distinctTool_resetsConsecutiveCounter() {
        RepeatedToolCallGuard g = new RepeatedToolCallGuard(MAX_EXECUTIONS_PER_SIGNATURE, 3);

        assertFalse(g.recordExecution("bash", "{\"command\":\"cmd1\"}"));
        assertFalse(g.recordExecution("bash", "{\"command\":\"cmd2\"}"));
        assertFalse(g.recordExecution("read_file", "{\"path\":\"Foo.java\"}"));
        // 3rd bash call exceeds MAX_EXECUTIONS_PER_TOOL_NAME = 2
        assertTrue(g.recordExecution("bash", "{\"command\":\"cmd3\"}"));
    }

    @Test
    void preCheckDetectsImminentLimit() {
        RepeatedToolCallGuard g = guard();

        // Primera ejecución: no excede
        assertFalse(g.recordExecutionPreCheck("list_files", "{\"path\":\"/x\"}"));
        // Ejecutar realmente para incrementar contadores
        g.recordExecution("list_files", "{\"path\":\"/x\"}");
        // Segunda ejecución con misma firma: excede (maxExecutionsPerSignature = 1)
        assertTrue(g.recordExecutionPreCheck("list_files", "{\"path\":\"/x\"}"));
    }

    @Test
    void preCheckDoesNotIncrementCounter() {
        RepeatedToolCallGuard g = guard();

        // Pre-check no debe incrementar el contador
        assertFalse(g.recordExecutionPreCheck("list_files", "{\"path\":\"/x\"}"));
        // La ejecución real tampoco debe exceder (todavía es la primera)
        assertFalse(g.recordExecution("list_files", "{\"path\":\"/x\"}"));
        // Ahora sí excede
        assertTrue(g.recordExecution("list_files", "{\"path\":\"/x\"}"));
    }

    @Test
    void pathNormalizationSameSignature() {
        RepeatedToolCallGuard g = guard();

        // Rutas que se resuelven al mismo directorio absoluto deben generar la misma firma
        assertFalse(g.recordExecution("list_files", "{\"path\":\"/a/./b\"}"));
        assertTrue(g.recordExecution("list_files", "{\"path\":\"/a/b\"}"));
    }

    @Test
    void pathNormalizationDifferentPathsDifferentSignature() {
        RepeatedToolCallGuard g = guard();

        assertFalse(g.recordExecution("list_files", "{\"path\":\"/a\"}"));
        assertFalse(g.recordExecution("list_files", "{\"path\":\"/b\"}"));
    }
}
