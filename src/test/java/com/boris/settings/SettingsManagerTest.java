package com.boris.settings;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class SettingsManagerTest {

    private SettingsManager settingsManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        settingsManager = new SettingsManager();
    }

    @Test
    void ensureExists_createsFile_whenNotExists() throws Exception {
        Path settingsFile = tempDir.resolve("settings.json");
        assertFalse(Files.exists(settingsFile));

        settingsManager.ensureExists(settingsFile.toString());

        assertTrue(Files.exists(settingsFile));

        String content = Files.readString(settingsFile);
        assertNotNull(content);
        assertTrue(content.contains("\"model\""));
        assertTrue(content.contains("granite4.2:8b"));
    }

    @Test
    void ensureExists_doesNotOverwrite_whenAlreadyExists() throws Exception {
        Path settingsFile = tempDir.resolve("settings.json");
        Files.writeString(settingsFile, "{\"custom\": true}");

        settingsManager.ensureExists(settingsFile.toString());

        String content = Files.readString(settingsFile);
        assertTrue(content.contains("\"custom\""));
    }

    @Test
    void ensureExists_createsParentDirectories_whenMissing() throws Exception {
        Path nestedDir = tempDir.resolve("deeply/nested/dir");
        Path settingsFile = nestedDir.resolve("settings.json");
        assertFalse(Files.exists(nestedDir));

        settingsManager.ensureExists(settingsFile.toString());

        assertTrue(Files.exists(settingsFile));
    }

    @Test
    void load_returnsContent_whenFileExists() throws Exception {
        Path settingsFile = tempDir.resolve("existing.json");
        Files.writeString(settingsFile, "{\"test\": 123}");

        String content = settingsManager.load(settingsFile.toString());

        assertEquals("{\"test\": 123}", content);
    }

    @Test
    void load_returnsNull_whenFileDoesNotExist() throws Exception {
        Path settingsFile = tempDir.resolve("nonexistent.json");

        String content = settingsManager.load(settingsFile.toString());

        assertNull(content);
    }

    @Test
    void ensureExists_createsSettingsFromTemplate_validatingAllParameters() throws Exception {
        Path settingsFile = tempDir.resolve("settings.json");
        assertFalse(Files.exists(settingsFile));

        settingsManager.ensureExists(settingsFile.toString());
        assertTrue(Files.exists(settingsFile));

        Settings settings = settingsManager.loadSettings(settingsFile.toString());
        assertNotNull(settings);

        // Validar parámetros del modelo
        assertNotNull(settings.getModel(), "Model config should not be null");
        assertEquals("http://localhost:8080", settings.getModel().getBaseUrl());
        assertEquals("granite4.2:8b", settings.getModel().getName());
        assertNotNull(settings.getModel().getOptions());
        assertEquals("high", settings.getModel().getOptions().get("think"));

        // Validar variables de entorno
        assertNotNull(settings.getEnv());
        assertEquals("ollama", settings.getEnv().get("OLLAMA_API_KEY"));

        // Validar parámetros de configuración general
        assertEquals(20, settings.getMaxHistorySize());
        assertEquals(true, settings.getEnableHistory());
        assertEquals(true, settings.getEnforceSequentialExecution());
        assertEquals(0.7, settings.getTemperature());
        assertEquals(10000, settings.getContextWindow());
        assertEquals(true, settings.getThinkingEnabled());
        assertEquals("think", settings.getThinkingMode());
    }

}
