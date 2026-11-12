package com.boris.tooling.integration;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.Test;

import com.boris.cli.ui.PlanMode;
import com.boris.cli.ui.PlanModeManager;
import com.boris.context.ProjectContext;
import com.boris.context.SearchSessionContext;
import com.boris.context.SessionDirectoryResolver;
import com.boris.exceptions.SessionWorkspaceViolationException;
import com.boris.tooling.engine.ToolRegistry;

import static org.junit.jupiter.api.Assertions.*;

class PlanModeToolGateTest {

    @TempDir
    Path tempDir;

    private Path plansDir;

    @BeforeEach
    void setUp() {
        ProjectContext.setProjectPath(tempDir.toString());
        SearchSessionContext.setSessionId("test-session");
        PlanModeManager.shared().setMode(PlanMode.PLAN);
        plansDir = SessionDirectoryResolver.plansDirectory();
    }

    @AfterEach
    void restaurarModoCompartido() {
        PlanModeManager.shared().setMode(PlanMode.WRITE);
        ProjectContext.clear();
        SearchSessionContext.clear();
    }

    private ToolRegistry registryEnPlanMode() {
        return ToolCallingConfig.buildToolRegistry(null);
    }

    @Test
    void applyEditEnPlanModeBloqueaArchivosNoMd() throws Exception {
        ToolRegistry registry = registryEnPlanMode();
        String result = registry.execute("apply_edit",
                "{\"path\":\"src/main/java/Foo.java\",\"old_text\":\"a\",\"new_text\":\"b\"}");
        assertTrue(result.contains("Plan mode activo"));
        assertFalse(result.contains("\"success\":true"));
    }

    @Test
    void applyEditEnPlanModeBloqueaMdFueraDePlans() throws Exception {
        ToolRegistry registry = registryEnPlanMode();
        String result = registry.execute("apply_edit",
                "{\"path\":\"/tmp/plan.md\",\"old_text\":\"a\",\"new_text\":\"b\"}");
        assertTrue(result.contains("Plan mode activo"));
    }

    @Test
    void applyEditEnPlanModePermiteMdDentroDePlans() throws Exception {
        ToolRegistry registry = registryEnPlanMode();
        String result = registry.execute("apply_edit",
                "{\"path\":\"" + plansDir + "/plan.md\",\"old_text\":\"a\",\"new_text\":\"b\"}");
        assertFalse(result.contains("Plan mode activo"), "El gate no debe bloquear .md dentro de plans/");
        assertFalse(result.contains("\"success\":true"), "El archivo no existe, debe fallar por otra razon pero no por plan mode");
    }

    @Test
    void revertEditEnPlanModeBloqueaArchivosNoMd() throws Exception {
        ToolRegistry registry = registryEnPlanMode();
        String result = registry.execute("revert_edit",
                "{\"path\":\"src/main/java/Foo.java\",\"old_text\":\"a\",\"new_text\":\"b\"}");
        assertTrue(result.contains("Plan mode activo"));
    }

    @Test
    void deleteFileEnPlanModeBloqueaSiempre() throws Exception {
        ToolRegistry registry = registryEnPlanMode();
        String result = registry.execute("delete_file",
                "{\"path\":\"" + plansDir + "/plan.md\"}");
        assertTrue(result.contains("Plan mode activo"));
    }

    @Test
    void createDirectoryEnPlanModeBloqueaSiempre() throws Exception {
        ToolRegistry registry = registryEnPlanMode();
        String result = registry.execute("create_directory",
                "{\"path\":\"" + plansDir + "/subdir\"}");
        assertTrue(result.contains("Plan mode activo"));
    }

    @Test
    void deleteDirectoryEnPlanModeBloqueaSiempre() throws Exception {
        ToolRegistry registry = registryEnPlanMode();
        String result = registry.execute("delete_directory",
                "{\"path\":\"" + plansDir + "\"}");
        assertTrue(result.contains("Plan mode activo"));
    }

    @Test
    void generatePdfEnPlanModeBloqueaSiempre() throws Exception {
        ToolRegistry registry = registryEnPlanMode();
        String result = registry.execute("generate_pdf",
                "{\"content\":\"hola\",\"outputPath\":\"" + plansDir + "/plan.pdf\",\"contentType\":\"text\"}");
        assertTrue(result.contains("Plan mode activo"));
    }

    @Test
    void createOfficeDocumentEnPlanModeBloqueaSiempre() throws Exception {
        ToolRegistry registry = registryEnPlanMode();
        String result = registry.execute("create_office_document",
                "{\"documentType\":\"word\",\"outputPath\":\"" + plansDir + "/plan.docx\",\"title\":\"t\",\"content\":\"c\"}");
        assertTrue(result.contains("Plan mode activo"));
    }

    @Test
    void bashEnPlanModeBloqueaSiempre() throws Exception {
        ToolRegistry registry = registryEnPlanMode();
        String result = registry.execute("bash", "{\"command\":\"touch /tmp/plan-mode-escape.txt\"}");
        assertTrue(result.contains("Plan mode activo"));
        assertFalse(result.contains("\"success\":true"));
    }

    @Test
    void gitEnPlanModeBloqueaSiempre() throws Exception {
        ToolRegistry registry = registryEnPlanMode();
        String result = registry.execute("git", "{\"command\":\"status\"}");
        assertTrue(result.contains("Plan mode activo"));
        assertFalse(result.contains("\"success\":true"));
    }

    @Test
    void mavenBuildEnPlanModeBloqueaSiempre() throws Exception {
        ToolRegistry registry = registryEnPlanMode();
        String result = registry.execute("maven_build", "{\"goal\":\"compile\"}");
        assertTrue(result.contains("Plan mode activo"));
        assertFalse(result.contains("\"success\":true"));
    }

    @Test
    void writeFile_enWriteMode_aBorisPlansPdf_rechazadoPorGuardDeSesion() throws Exception {
        ToolRegistry registry = registryEnPlanMode();
        // Forzar modo WRITE para probar el guard de sesión (no el gate de plan mode)
        PlanModeManager.shared().setMode(PlanMode.WRITE);

        assertThrows(com.boris.exceptions.SessionWorkspaceViolationException.class,
            () -> registry.execute("write_file",
                    "{\"path\":\"" + plansDir + "/out.pdf\",\"content\":\"hola\"}"));
    }
}
