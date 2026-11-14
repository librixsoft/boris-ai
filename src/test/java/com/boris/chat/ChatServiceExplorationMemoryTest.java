package com.boris.chat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import com.boris.context.ExplorationContextWriter;
import com.boris.context.ProjectContext;
import com.boris.context.SearchSessionContext;
import com.boris.context.SessionDirectoryResolver;
import com.boris.task.TaskAborter;

import static org.junit.jupiter.api.Assertions.*;

class ChatServiceExplorationMemoryTest {

    @TempDir
    Path tempDir;

    @AfterEach
    void limpiar() {
        ProjectContext.clear();
        SearchSessionContext.clear();
        // Limpiar archivo de exploración si existe
        try {
            ProjectContext.setProjectPath(tempDir.toString());
            SearchSessionContext.setSessionId("sesion-test");
            Files.deleteIfExists(SessionDirectoryResolver.explorationFile());
        } catch (Exception ignored) {}
    }

    private void setupSesion() {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.setSessionId("sesion-test");
    }

    @Test
    void buildPromptWithHistoryIncluyeBloqueExplorationMemory() throws Exception {
        setupSesion();
        ChatService service = new ChatService(() -> null, "boris", new TaskAborter(), 10, true);

        // Simular que ya hay exploration.md con contenido
        ExplorationContextWriter.append("list_files", "{\"path\":\"src\"}",
                "{\"success\":true,\"entries\":[{\"name\":\"Main.java\",\"type\":\"file\"}]}");

        String prompt = service.buildPromptWithHistory("Hola");

        assertTrue(prompt.contains("===== EXPLORATION MEMORY (ya explorado en esta sesión, no repetir) ====="));
        assertTrue(prompt.contains("===== END EXPLORATION MEMORY ====="));
        assertTrue(prompt.contains("Main.java"));
    }

    @Test
    void buildPromptWithHistorySinArchivoMemoriaNoIncluyeBloqueVacio() throws Exception {
        setupSesion();
        ChatService service = new ChatService(() -> null, "boris", new TaskAborter(), 10, true);

        String prompt = service.buildPromptWithHistory("Hola");

        assertFalse(prompt.contains("EXPLORATION MEMORY"));
        assertFalse(prompt.contains("END EXPLORATION MEMORY"));
    }

    @Test
    void clearHistoryNoLimpiaExplorationMd() throws Exception {
        setupSesion();
        ChatService service = new ChatService(() -> null, "boris", new TaskAborter(), 10, true);

        ExplorationContextWriter.append("list_files", "{\"path\":\"src\"}", "resultado");

        service.clearHistory();

        // El archivo exploration.md debe seguir existiendo con contenido
        Path file = SessionDirectoryResolver.explorationFile();
        assertTrue(Files.exists(file));
        String content = new String(Files.readString(file));
        assertTrue(content.contains("list_files"));
    }
}
