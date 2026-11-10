package com.boris.tooling.tool;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

import com.boris.cli.ui.PlanMode;
import com.boris.cli.ui.PlanModeManager;
import com.boris.exceptions.PlanModeException;

class WriteToolTest {

    @TempDir
    Path tempDir;

    private PlanModeManager planModeManager;

    @BeforeEach
    void setUp() {
        planModeManager = new PlanModeManager();
    }

    @Test
    void write_file_createsNewFile() throws Exception {
        Path file = tempDir.resolve("new.txt");
        WriteTool tool = new WriteTool(planModeManager);

        String result = tool.execute(Map.of("path", file.toString(), "content", "test content"));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(Files.exists(file));
        assertEquals("test content", Files.readString(file));
    }

    @Test
    void write_file_overwritesExistingFile() throws Exception {
        Path file = tempDir.resolve("overwrite.txt");
        Files.writeString(file, "old content");
        WriteTool tool = new WriteTool(planModeManager);

        String result = tool.execute(Map.of("path", file.toString(), "content", "new content"));

        assertTrue(result.contains("\"success\":true"));
        assertEquals("new content", Files.readString(file));
    }

    @Test
    void write_file_createsParentDirectories() throws Exception {
        Path deepDir = tempDir.resolve("a/b/c");
        Path file = deepDir.resolve("file.txt");
        WriteTool tool = new WriteTool(planModeManager);

        String result = tool.execute(Map.of("path", file.toString(), "content", "deep"));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(Files.exists(file));
    }

    @Test
    void write_file_returnsError_whenPathBlank() {
        WriteTool tool = new WriteTool(planModeManager);

        String result = tool.execute(Map.of("path", "", "content", "x"));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("path is required"));
    }

    @Test
    void write_file_definition_hasParametersSchema() {
        var def = WriteTool.write_file();
        Map<String, Object> props = (Map<String, Object>) def.parameters().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("path"));
        assertTrue(props.containsKey("content"));
    }

    @Test
    void writesFileToAbsolutePathOutsideProjectAndCreatesParentDirs() throws Exception {
        Path target = tempDir.resolve("subdir/landing/index.html");
        String content = "<h1>Landing</h1>";
        WriteTool tool = new WriteTool(planModeManager);

        String result = tool.execute(java.util.Map.of(
                "path", target.toString(),
                "content", content));

        assertTrue(result.contains("\"success\":true"), "Expected success, got: " + result);
        assertTrue(java.nio.file.Files.exists(target), "File should be created at the absolute path: " + target);
        assertEquals(content, java.nio.file.Files.readString(target));
        assertTrue(java.nio.file.Files.isDirectory(target.getParent()), "Parent directory should be created");
    }

    @Test
    void sanitizeTargetPathKeepsAbsolutePathAsIs() {
        String abs = "/Users/lastprophet/Downloads/index.html";
        assertEquals(abs, WriteTool.sanitizeTargetPath(abs));
    }

    @Test
    void sanitizeTargetPathStripsLanguagePrefix() {
        String result = WriteTool.sanitizeTargetPath("html:/Users/lastprophet/Downloads/index.html");
        assertEquals("/Users/lastprophet/Downloads/index.html", result);
    }

    @Test
    void execute_lanzaPlanModeException_whenModoPlanYRutaInvalida() {
        planModeManager.setMode(PlanMode.PLAN);
        WriteTool tool = new WriteTool(planModeManager);

        assertThrows(PlanModeException.class,
                () -> tool.execute(Map.of("path", "/tmp/invalid.txt", "content", "x")));
    }

    @Test
    void execute_escribeCorrectamente_whenModoWrite() {
        Path file = tempDir.resolve("test.md");
        WriteTool tool = new WriteTool(planModeManager);

        String result = tool.execute(Map.of("path", file.toString(), "content", "content"));

        assertTrue(result.contains("\"success\":true"));
    }

    @Test
    void execute_escribeCorrectamente_whenModoPlanYRutaValida() throws Exception {
        planModeManager.setMode(PlanMode.PLAN);
        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        Path plansDir = projectRoot.resolve(".boris").resolve("plans");
        Files.createDirectories(plansDir);
        Path file = plansDir.resolve("plan.md");
        WriteTool tool = new WriteTool(planModeManager);

        String result = tool.execute(Map.of("path", file.toString(), "content", "content"));

        assertTrue(result.contains("\"success\":true"));
        assertEquals("content", Files.readString(file));
    }
}
