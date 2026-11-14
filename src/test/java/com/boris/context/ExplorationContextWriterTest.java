package com.boris.context;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import com.boris.context.ExplorationContextWriter;
import com.boris.context.ExplorationContextWriterException;
import com.boris.exceptions.BorisException;

import static org.junit.jupiter.api.Assertions.*;

class ExplorationContextWriterTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void limpiar() {
        ProjectContext.clear();
        SearchSessionContext.clear();
    }

    @Test
    void appendEscribeLineConTimestampToolArgsYExtractoTruncado() throws Exception {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.setSessionId("sesion");

        ExplorationContextWriter.append("list_files", "{\"path\":\"src\"}",
                "{\"success\":true,\"entries\":[{\"name\":\"Main.java\",\"type\":\"file\"}]}");

        Path file = SessionDirectoryResolver.explorationFile();
        assertTrue(Files.exists(file));

        String content = Files.readString(file);
        assertTrue(content.contains("list_files"));
        assertTrue(content.contains("src"));
        assertTrue(content.contains("Main.java"));
        // Debe tener timestamp ISO entre corchetes: [2026-11-13T10:15:30]
        java.util.regex.Pattern ts = java.util.regex.Pattern.compile("\\[\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}");
        assertTrue(ts.matcher(content).find());
    }

    @Test
    void modoAppendDosEjecucionesProducenDosLineasEnOrden() throws Exception {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.setSessionId("sesion");

        ExplorationContextWriter.append("list_files", "{\"path\":\"src\"}", "resultado1");
        ExplorationContextWriter.append("batch_read_files", "{\"paths\":[\"a.java\"]}", "resultado2");

        List<String> lines = Files.readAllLines(SessionDirectoryResolver.explorationFile());
        // Header (2 líneas: título + vacío) + 2 entradas = 4 líneas totales
        assertEquals(4, lines.size());
        // Las entradas están en las líneas 3 y 4 (índices 2 y 3)
        assertTrue(lines.get(2).contains("list_files"));
        assertTrue(lines.get(3).contains("batch_read_files"));
    }

    @Test
    void truncadoResultadoMayorAMaxCharsQuedaRecortado() throws Exception {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.setSessionId("sesion");

        StringBuilder largo = new StringBuilder();
        for (int i = 0; i < 3000; i++) {
            largo.append("x");
        }

        ExplorationContextWriter.append("list_files", "{\"path\":\"src\"}", largo.toString());

        String content = Files.readString(SessionDirectoryResolver.explorationFile());
        // El extracto debe estar truncado a MAX_ENTRY_RESULT_CHARS
        assertTrue(content.length() < 3000 + 200);
        assertTrue(content.contains("... [truncated"));
    }

    @Test
    void sinProjectPathLanzaExplorationContextWriterException() {
        SearchSessionContext.setSessionId("sesion");
        ProjectContext.clear();
        assertThrows(ExplorationContextWriterException.class,
                () -> ExplorationContextWriter.append("list_files", "{\"path\":\"src\"}", "resultado"));
    }

    @Test
    void sinSessionIdLanzaExplorationContextWriterException() {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.clear();
        assertThrows(ExplorationContextWriterException.class,
                () -> ExplorationContextWriter.append("list_files", "{\"path\":\"src\"}", "resultado"));
    }

    @Test
    void readMemoryBlockArchivoInexistenteDevuelveBloqueVacio() {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.setSessionId("sesion");

        String block = ExplorationContextWriter.readMemoryBlock();
        assertNotNull(block);
        assertTrue(block.isBlank() || block.contains("EXPLORATION MEMORY"));
    }

    @Test
    void readMemoryBlockRespetaMaxEntriesYMaxChars() {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.setSessionId("sesion");

        // Escribir muchas entradas
        for (int i = 0; i < 20; i++) {
            ExplorationContextWriter.append("list_files", "{\"path\":\"src\"}", "resultado " + i);
        }

        String block = ExplorationContextWriter.readMemoryBlock();
        // Debe contener el header y footer
        assertTrue(block.contains("EXPLORATION MEMORY"));
        assertTrue(block.contains("END EXPLORATION MEMORY"));
        // No debe exceder MAX_INJECTION_CHARS
        assertTrue(block.length() <= ExplorationContextWriter.MAX_INJECTION_CHARS + 200);
    }

    @Test
    void soloRegistraToolsDelSetExploration() throws Exception {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.setSessionId("sesion");

        // Tool fuera del set no debe generar línea
        ExplorationContextWriter.append("web_search", "{\"query\":\"hola\"}", "resultado");
        ExplorationContextWriter.append("list_files", "{\"path\":\"src\"}", "resultado");

        List<String> lines = Files.readAllLines(SessionDirectoryResolver.explorationFile());
        // Header (2 líneas) + 1 entrada (list_files) = 3 líneas totales
        assertEquals(3, lines.size());
        // La entrada de list_files está en la línea 3 (índice 2)
        assertTrue(lines.get(2).contains("list_files"));
    }
}
