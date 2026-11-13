package com.boris.llm.think;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DirectoryDrillingGuardTest {

    @Test
    void singleAccess_noDrilling() {
        DirectoryDrillingGuard guard = new DirectoryDrillingGuard();
        assertFalse(guard.recordDirectoryAccess("/a"));
    }

    @Test
    void twoLevels_noDrilling() {
        DirectoryDrillingGuard guard = new DirectoryDrillingGuard();
        assertFalse(guard.recordDirectoryAccess("/a"));
        assertFalse(guard.recordDirectoryAccess("/a/b"));
    }

    @Test
    void threeLevels_detectsDrilling() {
        DirectoryDrillingGuard guard = new DirectoryDrillingGuard();
        assertFalse(guard.recordDirectoryAccess("/a"));
        assertFalse(guard.recordDirectoryAccess("/a/b"));
        assertTrue(guard.recordDirectoryAccess("/a/b/c"));
    }

    @Test
    void nonHierarchical_noDrilling() {
        DirectoryDrillingGuard guard = new DirectoryDrillingGuard();
        assertFalse(guard.recordDirectoryAccess("/a"));
        assertFalse(guard.recordDirectoryAccess("/x"));
        assertFalse(guard.recordDirectoryAccess("/y"));
    }

    @Test
    void mixedPaths_resetsDrillingOnBreak() {
        DirectoryDrillingGuard guard = new DirectoryDrillingGuard();
        assertFalse(guard.recordDirectoryAccess("/a"));
        assertFalse(guard.recordDirectoryAccess("/a/b"));
        assertFalse(guard.recordDirectoryAccess("/x"));
        assertFalse(guard.recordDirectoryAccess("/x/y"));
        assertTrue(guard.recordDirectoryAccess("/x/y/z"));
    }

    @Test
    void reset_clearsState() {
        DirectoryDrillingGuard guard = new DirectoryDrillingGuard();
        guard.recordDirectoryAccess("/a");
        guard.recordDirectoryAccess("/a/b");
        guard.recordDirectoryAccess("/a/b/c"); // drilling detectado
        guard.reset();
        // Después del reset, el estado está limpio: un solo acceso no detecta drilling
        assertFalse(guard.recordDirectoryAccess("/a"));
        // Pero el drilling detection sigue funcionando desde cero
        assertFalse(guard.recordDirectoryAccess("/a/b"));
        assertTrue(guard.recordDirectoryAccess("/a/b/c"));
    }

    @Test
    void normalizedPaths_detectDrilling() {
        DirectoryDrillingGuard guard = new DirectoryDrillingGuard();
        // /a/./b se normaliza a /a/b
        assertFalse(guard.recordDirectoryAccess("/a/./b"));
        // /a/b/c es hija directa de /a/b
        assertFalse(guard.recordDirectoryAccess("/a/b/c"));
        // /a/b/c/d es hija directa de /a/b/c -> drilling
        assertTrue(guard.recordDirectoryAccess("/a/b/c/d"));
    }
}
