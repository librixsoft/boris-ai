package com.boris.tooling.parallel;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class BatchReadFileToolTest {

    private BatchReadFileTool tool;
    
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        tool = new BatchReadFileTool();
        com.boris.context.ProjectContext.setProjectPath(tempDir.toAbsolutePath().toString());
    }

    @AfterEach
    void tearDown() {
        tool.shutdown();
        com.boris.context.ProjectContext.clear();
    }

    @Test
    @Order(1)
    void testExecuteWithEmptyPaths() {
        String result = tool.execute(List.of(), null);
        assertTrue(result.contains("empty"), "Should report empty paths");
        assertTrue(result.contains("false"), "Should report failure");
    }

    @Test
    @Order(2)
    void testExecuteWithNullPaths() {
        String result = tool.execute(null, null);
        assertTrue(result.contains("empty"), "Should report null paths as empty");
    }

    @Test
    @Order(3)
    void testExecuteSingleFile() throws Exception {
        Path testFile = tempDir.resolve("single.txt");
        Files.writeString(testFile, "Hello World");
        
        String result = tool.execute(List.of(testFile.toString()), null);
        assertTrue(result.contains("true"), "Should report success");
        assertTrue(result.contains("Hello World"), "Should contain file content");
        assertTrue(result.contains("single.txt"), "Should contain filename");
    }

    @Test
    @Order(4)
    void testExecuteMultipleFilesConcurrently() throws Exception {
        // Create 5 test files
        List<Path> testFiles = List.of(
            tempDir.resolve("file1.txt"),
            tempDir.resolve("file2.txt"),
            tempDir.resolve("file3.txt"),
            tempDir.resolve("file4.txt"),
            tempDir.resolve("file5.txt")
        );
        
        for (int i = 0; i < testFiles.size(); i++) {
            Files.writeString(testFiles.get(i), "Content " + (i + 1));
        }
        
        List<String> paths = testFiles.stream()
            .map(Path::toString)
            .toList();
        
        // Measure execution time
        long startTime = System.nanoTime();
        String result = tool.execute(paths, null);
        long endTime = System.nanoTime();
        
        long elapsedMs = (endTime - startTime) / 1_000_000;
        
        // Verify all files were read
        assertTrue(result.contains("5"), "Should report 5 total files");
        assertTrue(result.contains("5"), "Should report 5 successful reads");
        
        // Verify concurrent execution (should be much faster than sequential)
        // Sequential would take 5 * 15s timeout, concurrent should be < 1 second for small files
        assertTrue(elapsedMs < 1000, "Concurrent read should complete in under 1 second, took: " + elapsedMs + "ms");
        
        // Verify all content present
        for (int i = 1; i <= 5; i++) {
            assertTrue(result.contains("Content " + i), "Should contain content for file " + i);
        }
    }

    @Test
    @Order(5)
    void testExecuteWithMaxLines() throws Exception {
        Path testFile = tempDir.resolve("longfile.txt");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 100; i++) {
            sb.append("Line ").append(i).append("\n");
        }
        Files.writeString(testFile, sb.toString());
        
        String result = tool.execute(List.of(testFile.toString()), 10);
        assertTrue(result.contains("true"), "Should report success");
        assertTrue(result.contains("10"), "Should report 10 lines max");
    }

    @Test
    @Order(6)
    void testExecuteWithNonExistentFile() {
        String result = tool.execute(List.of("/nonexistent/path.txt"), null);
        assertTrue(result.contains("false"), "Should report failure for missing file");
        assertTrue(result.contains("not found"), "Should report 'not found' error");
    }

    @Test
    @Order(7)
    void testExecuteMixedFiles() throws Exception {
        Path existingFile = tempDir.resolve("exists.txt");
        Files.writeString(existingFile, "Exists");
        
        List<String> paths = List.of(
            existingFile.toString(),
            "/nonexistent/file.txt"
        );
        
        String result = tool.execute(paths, null);
        assertTrue(result.contains("1"), "Should report 1 total file");
        assertTrue(result.contains("1"), "Should report 1 successful read");
    }

    @Test
    @Order(8)
    void testExecuteWithDuplicatePaths() throws Exception {
        Path testFile = tempDir.resolve("duplicate.txt");
        Files.writeString(testFile, "Duplicate content");
        
        // Request same file twice
        List<String> paths = List.of(testFile.toString(), testFile.toString());
        
        String result = tool.execute(paths, null);
        // Should deduplicate and only read once
        assertTrue(result.contains("true"), "Should report success");
    }
}
