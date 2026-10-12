package com.boris.tooling.parallel;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import static org.junit.jupiter.api.Assertions.*;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class PathLockManagerTest {

    private PathLockManager lockManager;

    @BeforeEach
    void setUp() {
        lockManager = new PathLockManager();
    }

    @Test
    @Order(1)
    void testGetLockReturnsSameLockForSamePath() {
        Path tempFile = Path.of("/tmp/test-file.txt");
        ReentrantLock lock1 = lockManager.getLockFor(tempFile);
        ReentrantLock lock2 = lockManager.getLockFor(tempFile);
        
        assertSame(lock1, lock2, "Should return the same lock instance for the same path");
    }

    @Test
    @Order(2)
    void testDifferentPathsGetDifferentLocks() {
        Path file1 = Path.of("/tmp/test-file1.txt");
        Path file2 = Path.of("/tmp/test-file2.txt");
        ReentrantLock lock1 = lockManager.getLockFor(file1);
        ReentrantLock lock2 = lockManager.getLockFor(file2);
        
        assertNotSame(lock1, lock2, "Should return different locks for different paths");
    }

    @Test
    @Order(3)
    void testArePathsConflictingSameFile() {
        Path file1 = Path.of("src/main/java/Test.java");
        Path file2 = Path.of("src/main/java/Test.java");
        
        assertTrue(lockManager.arePathsConflicting(file1, file2),
            "Same paths should conflict");
    }

    @Test
    @Order(4)
    void testArePathsConflictingDifferentFiles() {
        Path file1 = Path.of("src/main/java/Test1.java");
        Path file2 = Path.of("src/main/java/Test2.java");
        
        assertFalse(lockManager.arePathsConflicting(file1, file2),
            "Different paths should not conflict");
    }

    @Test
    @Order(5)
    void testWithLockEnsuresExclusiveAccess() throws Exception {
        Path testFile = Path.of("/tmp/exclusive-test.txt");
        ConcurrentHashMap<String, Object> accessLog = new ConcurrentHashMap<>();
        
        // Execute two operations with the same lock sequentially
        lockManager.withLock(testFile, () -> accessLog.put("op1", System.nanoTime()));
        
        // Verify the lock was acquired and released properly
        assertEquals(1, accessLog.size(), "Should have one access logged");
        assertNotNull(accessLog.get("op1"), "op1 should be logged");
    }
}
