package com.boris.context;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import com.boris.exceptions.BorisException;

import static org.junit.jupiter.api.Assertions.*;

class SessionDirectoryResolverTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void limpiar() {
        ProjectContext.clear();
        SearchSessionContext.clear();
    }

    @Test
    void sessionDirectoryIncluyeProjectRootBorisisYSessionId() {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.setSessionId("a1b2c3d4e5f6a7b8");

        Path expected = tempDir.resolve(".boris").resolve("a1b2c3d4e5f6a7b8");
        assertEquals(expected.toAbsolutePath().normalize(),
                SessionDirectoryResolver.sessionDirectory().toAbsolutePath().normalize());
    }

    @Test
    void searchesFileEsSessionDirectoryMasSearchsMd() {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.setSessionId("sesion");

        Path expected = SessionDirectoryResolver.sessionDirectory().resolve("searchs.md");
        assertEquals(expected.toAbsolutePath().normalize(),
                SessionDirectoryResolver.searchesFile().toAbsolutePath().normalize());
    }

    @Test
    void plansDirectoryEsSessionDirectoryMasPlans() {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.setSessionId("sesion");

        Path expected = SessionDirectoryResolver.sessionDirectory().resolve("plans");
        assertEquals(expected.toAbsolutePath().normalize(),
                SessionDirectoryResolver.plansDirectory().toAbsolutePath().normalize());
    }

    @Test
    void sessionDirectorySinSessionIdLanzaBorisException() {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.clear();
        assertThrows(BorisException.class, SessionDirectoryResolver::sessionDirectory);
    }

    @Test
    void sessionDirectorySinProjectPathLanzaBorisException() {
        SearchSessionContext.setSessionId("sesion");
        ProjectContext.clear();
        assertThrows(BorisException.class, SessionDirectoryResolver::sessionDirectory);
    }

    @Test
    void searchesFileSinSessionIdLanzaBorisException() {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.clear();
        assertThrows(BorisException.class, SessionDirectoryResolver::searchesFile);
    }

    @Test
    void plansDirectorySinSessionIdLanzaBorisException() {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.clear();
        assertThrows(BorisException.class, SessionDirectoryResolver::plansDirectory);
    }
}
