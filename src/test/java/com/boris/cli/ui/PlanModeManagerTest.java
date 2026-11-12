package com.boris.cli.ui;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import com.boris.context.ProjectContext;
import com.boris.context.SearchSessionContext;
import com.boris.context.SessionDirectoryResolver;
import com.boris.tooling.engine.ToolDefinition;
import com.boris.tooling.engine.ToolRegistry;
import com.boris.tooling.integration.ToolCallingConfig;

import static org.junit.jupiter.api.Assertions.*;

class PlanModeManagerTest {

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.setSessionId("test-session");
    }

    @AfterEach
    void restaurarModoCompartido() {
        PlanModeManager.shared().setMode(PlanMode.WRITE);
        ProjectContext.clear();
        SearchSessionContext.clear();
    }

    @Test
    void canWriteRetornaTrueEnModoWriteParaCualquierRuta() {
        PlanModeManager manager = new PlanModeManager();
        assertTrue(manager.canWrite("/any/path/file.txt"));
        assertTrue(manager.canWrite("/any/path/file.md"));
    }

    @Test
    void canWriteRetornaFalseEnModoPlanParaRutaNoMd() {
        PlanModeManager manager = new PlanModeManager();
        manager.setMode(PlanMode.PLAN);
        assertFalse(manager.canWrite("/any/path/file.txt"));
        assertFalse(manager.canWrite("/any/path/file.java"));
    }

    @Test
    void canWriteRetornaFalseEnModoPlanParaRutaMdFueraDePlans() {
        PlanModeManager manager = new PlanModeManager();
        manager.setMode(PlanMode.PLAN);
        assertFalse(manager.canWrite("/tmp/plan.md"));
        assertFalse(manager.canWrite("/home/user/docs/plan.md"));
    }

    @Test
    void canWriteRetornaTrueEnModoPlanParaRutaMdDentroDePlans() {
        PlanModeManager manager = new PlanModeManager();
        manager.setMode(PlanMode.PLAN);
        Path plansDir = SessionDirectoryResolver.plansDirectory();
        String validPath = plansDir.toAbsolutePath().normalize().toString() + "/plan.md";
        assertTrue(manager.canWrite(validPath));
    }

    @Test
    void getModeRetornaWritePorDefecto() {
        PlanModeManager manager = new PlanModeManager();
        assertEquals(PlanMode.WRITE, manager.getMode());
    }

    @Test
    void setModeCambiaElModo() {
        PlanModeManager manager = new PlanModeManager();
        manager.setMode(PlanMode.PLAN);
        assertEquals(PlanMode.PLAN, manager.getMode());
        manager.setMode(PlanMode.WRITE);
        assertEquals(PlanMode.WRITE, manager.getMode());
    }

    @Test
    void sharedRetornaSiempreLaMismaInstancia() {
        assertSame(PlanModeManager.shared(), PlanModeManager.shared());
    }

    @Test
    void writeDeRegistryEnModoPlanRechazaArchivosNoMd() throws Exception {
        ToolCallingConfig.buildToolRegistry(null);
        PlanModeManager.shared().setMode(PlanMode.PLAN);
        ToolRegistry registry = ToolCallingConfig.buildToolRegistry(null);
        ToolDefinition writeTool = registry.getTool("write_file");
        assertNotNull(writeTool);
        String result = registry.execute("write_file",
                "{\"path\":\"/tmp/boris-plan-mode-test/archivo.txt\",\"content\":\"hola\"}");
        assertTrue(result.contains("Plan mode activo"), "write_file debe rechazar la escritura en plan mode");
        assertFalse(result.contains("\"success\":true"));
    }

    @Test
    void writeDeRegistryEnModoWritePermiteEscrituraEnCualquierRuta() throws Exception {
        ToolCallingConfig.buildToolRegistry(null);
        PlanModeManager.shared().setMode(PlanMode.WRITE);
        ToolRegistry registry = ToolCallingConfig.buildToolRegistry(null);
        String result = registry.execute("write_file",
                "{\"path\":\"/tmp/boris-plan-mode-test/archivo.txt\",\"content\":\"hola\"}");
        assertTrue(result.contains("\"success\":true"), "write_file debe permitir escribir en modo WRITE");
    }
}
