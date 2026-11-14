package com.boris.context;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import com.boris.exceptions.BorisException;

import static org.junit.jupiter.api.Assertions.*;

class SessionDirectoryResolverExplorationTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void limpiar() {
        ProjectContext.clear();
        SearchSessionContext.clear();
    }

    @Test
    void explorationFileEsSessionDirectoryMasExplorationMd() {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.setSessionId("sesion");

        Path expected = SessionDirectoryResolver.sessionDirectory().resolve("exploration.md");
        assertEquals(expected.toAbsolutePath().normalize(),
                SessionDirectoryResolver.explorationFile().toAbsolutePath().normalize());
    }

    @Test
    void explorationFileSinSessionIdLanzaBorisException() {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.clear();
        assertThrows(BorisException.class, SessionDirectoryResolver::explorationFile);
    }

    @Test
    void explorationFileSinProjectPathLanzaBorisException() {
        SearchSessionContext.setSessionId("sesion");
        ProjectContext.clear();
        assertThrows(BorisException.class, SessionDirectoryResolver::explorationFile);
    }
}
