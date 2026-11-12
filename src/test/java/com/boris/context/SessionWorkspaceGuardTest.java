package com.boris.context;

import java.nio.file.Path;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.boris.exceptions.SessionWorkspaceViolationException;

import static org.junit.jupiter.api.Assertions.*;

class SessionWorkspaceGuardTest {

    @AfterEach
    void limpiar() {
        ProjectContext.clear();
        SearchSessionContext.clear();
    }

    @Test
    void assertWritable_planMdDirecto_enPlans_noLanza() {
        ProjectContext.setProjectPath("/tmp/proyecto");
        SearchSessionContext.setSessionId("a1b2c3d4e5f6a7b8");

        // No debe lanzar
        SessionWorkspaceGuard.assertWritable("/tmp/proyecto/.boris/a1b2c3d4e5f6a7b8/plans/nota.md");
    }

    @Test
    void assertWritable_pdf_enPlans_lanza() {
        ProjectContext.setProjectPath("/tmp/proyecto");
        SearchSessionContext.setSessionId("a1b2c3d4e5f6a7b8");

        assertThrows(SessionWorkspaceViolationException.class,
                () -> SessionWorkspaceGuard.assertWritable(
                        "/tmp/proyecto/.boris/a1b2c3d4e5f6a7b8/plans/out.pdf"));
    }

    @Test
    void assertWritable_md_enSubdirPlans_lanza() {
        ProjectContext.setProjectPath("/tmp/proyecto");
        SearchSessionContext.setSessionId("a1b2c3d4e5f6a7b8");

        assertThrows(SessionWorkspaceViolationException.class,
                () -> SessionWorkspaceGuard.assertWritable(
                        "/tmp/proyecto/.boris/a1b2c3d4e5f6a7b8/plans/subdir/x.md"));
    }

    @Test
    void assertWritable_searchsMd_enSesion_lanza() {
        ProjectContext.setProjectPath("/tmp/proyecto");
        SearchSessionContext.setSessionId("a1b2c3d4e5f6a7b8");

        assertThrows(SessionWorkspaceViolationException.class,
                () -> SessionWorkspaceGuard.assertWritable(
                        "/tmp/proyecto/.boris/a1b2c3d4e5f6a7b8/searchs.md"));
    }

    @Test
    void assertWritable_docx_enSesion_lanza() {
        ProjectContext.setProjectPath("/tmp/proyecto");
        SearchSessionContext.setSessionId("a1b2c3d4e5f6a7b8");

        assertThrows(SessionWorkspaceViolationException.class,
                () -> SessionWorkspaceGuard.assertWritable(
                        "/tmp/proyecto/.boris/a1b2c3d4e5f6a7b8/cualquier.docx"));
    }

    @Test
    void assertWritable_fueraDeBoris_noLanza() {
        ProjectContext.setProjectPath("/tmp/proyecto");
        SearchSessionContext.setSessionId("a1b2c3d4e5f6a7b8");

        // No debe lanzar
        SessionWorkspaceGuard.assertWritable("/tmp/proyecto/src/main/java/Foo.java");
        SessionWorkspaceGuard.assertWritable("/tmp/otro/archivo.txt");
    }

    @Test
    void assertWritable_sinSessionId_noLanza() {
        ProjectContext.setProjectPath("/tmp/proyecto");
        SearchSessionContext.clear();

        // No debe lanzar (no hay sesión)
        SessionWorkspaceGuard.assertWritable("/tmp/proyecto/.boris/algo/searchs.md");
    }

    @Test
    void assertWritable_sinProjectPath_noLanza() {
        SearchSessionContext.setSessionId("a1b2c3d4e5f6a7b8");
        ProjectContext.clear();

        // No debe lanzar (no hay project path)
        SessionWorkspaceGuard.assertWritable("/tmp/proyecto/.boris/a1b2c3d4e5f6a7b8/searchs.md");
    }
}
