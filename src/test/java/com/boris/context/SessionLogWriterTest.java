package com.boris.context;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import com.boris.exceptions.SessionLogException;
import com.boris.settings.Settings;

import static org.junit.jupiter.api.Assertions.*;

class SessionLogWriterTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void limpiar() {
        SearchSessionContext.clear();
    }

    @Test
    void escribirLineaContieneTimestampYEvento() throws Exception {
        Path logFile = tempDir.resolve("test.log");
        SessionLogWriter writer = new SessionLogWriter(logFile, true);
        writer.logEvent("USER", "hola mundo");
        writer.close();

        String content = Files.readString(logFile);
        assertTrue(content.contains("] [USER] hola mundo"));
        assertTrue(content.contains("]"));
    }

    @Test
    void creaDirectorioLogsSiNoExiste() throws Exception {
        Path nestedLog = tempDir.resolve("subdir").resolve("test.log");
        SessionLogWriter writer = new SessionLogWriter(nestedLog, true);
        writer.close();
        assertTrue(Files.exists(nestedLog));
        assertTrue(Files.isDirectory(tempDir.resolve("subdir")));
    }

    @Test
    void modoAppendProduceDosLineas() throws Exception {
        Path logFile = tempDir.resolve("test.log");
        SessionLogWriter writer = new SessionLogWriter(logFile, true);
        writer.logEvent("USER", "primera");
        writer.logEvent("BOT", "segunda");
        writer.close();

        String content = Files.readString(logFile);
        assertEquals(2, content.lines().count());
        assertTrue(content.contains("primera"));
        assertTrue(content.contains("segunda"));
    }

    @Test
    void directorioNoCreatibleLanzaSessionLogException() throws Exception {
        Path readOnlyDir = tempDir.resolve("readonly");
        Files.createDirectory(readOnlyDir);
        readOnlyDir.toFile().setReadable(true);
        readOnlyDir.toFile().setWritable(false);

        Path logFile = readOnlyDir.resolve("test.log");
        assertThrows(SessionLogException.class, () -> {
            SessionLogWriter writer = new SessionLogWriter(logFile, true);
            writer.close();
        });
    }

    @Test
    void logsEnabledFalseNoEscribeNada() throws Exception {
        Path logFile = tempDir.resolve("test.log");
        SessionLogWriter writer = new SessionLogWriter(logFile, false);
        writer.logEvent("USER", "hola");
        writer.close();
        assertFalse(Files.exists(logFile));
    }
}
