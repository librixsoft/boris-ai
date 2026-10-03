package com.boris.tooling.tool;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class ExecuteCommandToolTest {

    private ExecuteCommandTool tool;

    @BeforeEach
    void setUp() {
        tool = new ExecuteCommandTool();
    }

    @Test
    void execute_command_definition_hasCorrectNameAndParameters() {
        var def = ExecuteCommandTool.execute_command();

        assertEquals("execute_command", def.name());
        assertFalse(def.description().isBlank());
        assertNotNull(def.parameters());
        assertTrue(def.parameters().containsKey("properties"));
    }

    @Test
    void execute_simpleCommand_returnsSuccess() {
        String result = tool.execute(Map.of("command", "echo Hello World"));

        assertNotNull(result);
        assertTrue(result.contains("\"success\":true"));
        assertTrue(result.contains("\"exitCode\":0"));
        assertTrue(result.contains("Hello World"));
    }

    @Test
    void execute_invalidCommand_returnsErrorExitCode() {
        String result = tool.execute(Map.of("command", "non_existent_command_123456789"));

        assertNotNull(result);
        assertTrue(result.contains("\"success\":false"));
    }

    @Test
    void execute_missingCommand_returnsError() {
        String result = tool.execute(Map.of());

        assertNotNull(result);
        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("command is required"));
    }

    @Test
    void execute_withCustomWorkingDirectory(@TempDir Path tempDir) {
        String result = tool.execute(Map.of(
                "command", "pwd",
                "workingDirectory", tempDir.toAbsolutePath().toString()
        ));

        assertNotNull(result);
        assertTrue(result.contains("\"success\":true"));
        assertTrue(result.contains(tempDir.getFileName().toString()));
    }

    @Test
    void execute_invalidWorkingDirectory_returnsError() {
        String result = tool.execute(Map.of(
                "command", "echo test",
                "workingDirectory", "/invalid_dir_that_does_not_exist_987"
        ));

        assertNotNull(result);
        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("working directory does not exist"));
    }
}
