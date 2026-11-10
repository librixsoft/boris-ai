package com.boris.cli.ui;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.boris.context.ProjectContext;

@Component
public class PlanModeManager {

    private final AtomicReference<PlanMode> mode = new AtomicReference<>(PlanMode.WRITE);

    public PlanMode getMode() {
        return mode.get();
    }

    public void setMode(PlanMode mode) {
        this.mode.set(mode);
    }

    public boolean canWrite(String absolutePath) {
        if (getMode() == PlanMode.WRITE) {
            return true;
        }
        Path path = Paths.get(absolutePath).toAbsolutePath().normalize();
        if (!path.getFileName().toString().endsWith(".md")) {
            return false;
        }
        Path projectRoot = ProjectContext.projectRootOrCurrent();
        if (projectRoot == null) {
            return false;
        }
        Path plansDir = projectRoot.resolve(".boris").resolve("plans").toAbsolutePath().normalize();
        return path.startsWith(plansDir);
    }

    public Path getPlansDirectory() {
        Path projectRoot = ProjectContext.projectRootOrCurrent();
        if (projectRoot == null) {
            return null;
        }
        return projectRoot.resolve(".boris").resolve("plans").toAbsolutePath().normalize();
    }

    public void initializeFromSettings(PlanMode planMode) {
        if (planMode != null) {
            setMode(planMode);
        }
    }
}
