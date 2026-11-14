package com.boris.context;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import com.boris.exceptions.BorisException;

import static org.junit.jupiter.api.Assertions.*;

class SessionLogResolverTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void limpiar() {
        SearchSessionContext.clear();
    }

    @Test
    void logsDirectoryDevuelveHomeBorisisLogs() {
        Path expected = Paths.get(System.getProperty("user.home"))
                .resolve(".boris").resolve("logs");
        assertEquals(expected.toAbsolutePath().normalize(),
                SessionLogResolver.logsDirectory().toAbsolutePath().normalize());
    }

    @Test
    void logsDirectorySeResuelveDinamicamenteDesdeUserHome() {
        Path logsDir = SessionLogResolver.logsDirectory();
        Path expected = Paths.get(System.getProperty("user.home"))
                .resolve(".boris").resolve("logs");
        assertEquals(expected.toAbsolutePath().normalize(),
                logsDir.toAbsolutePath().normalize());
    }

    @Test
    void sessionLogFileDevuelveFormatoTimestampSessionId() {
        SearchSessionContext.setSessionId("a3f9c2e81b7d4f60");
        Path logFile = SessionLogResolver.sessionLogFile();
        String fileName = logFile.getFileName().toString();
        Pattern pattern = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}_[0-9a-f]{16}\\.log$");
        assertTrue(pattern.matcher(fileName).matches(),
                "File name does not match pattern: " + fileName);
    }

    @Test
    void sessionLogFileSinSessionIdLanzaBorisException() {
        SearchSessionContext.clear();
        assertThrows(BorisException.class, SessionLogResolver::sessionLogFile);
    }

    @Test
    void sessionLogFileEstaEnLogsDirectory() {
        SearchSessionContext.setSessionId("a3f9c2e81b7d4f60");
        Path logFile = SessionLogResolver.sessionLogFile();
        assertTrue(logFile.startsWith(SessionLogResolver.logsDirectory()));
    }
}
