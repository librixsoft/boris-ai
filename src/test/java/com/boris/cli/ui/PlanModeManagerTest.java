package com.boris.cli.ui;

import java.nio.file.Path;
import java.nio.file.Paths;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PlanModeManagerTest {

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
        Path projectRoot = Paths.get(System.getProperty("user.dir"));
        Path plansDir = projectRoot.resolve(".boris").resolve("plans");
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
}
