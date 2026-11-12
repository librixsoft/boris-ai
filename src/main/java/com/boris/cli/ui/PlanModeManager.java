package com.boris.cli.ui;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Component;

import com.boris.context.ProjectContext;
import com.boris.context.SessionDirectoryResolver;
import com.boris.exceptions.BorisException;

@Component
public class PlanModeManager {

    private static final PlanModeManager SHARED = new PlanModeManager();

    private final AtomicReference<PlanMode> mode = new AtomicReference<>(PlanMode.WRITE);

    public static PlanModeManager shared() {
        return SHARED;
    }

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
        Path plansDir;
        try {
            plansDir = SessionDirectoryResolver.plansDirectory();
        } catch (BorisException e) {
            return false;
        }
        return path.startsWith(plansDir);
    }

    public Path getPlansDirectory() {
        try {
            return SessionDirectoryResolver.plansDirectory();
        } catch (BorisException e) {
            return null;
        }
    }

    public void initializeFromSettings(PlanMode planMode) {
        if (planMode != null) {
            setMode(planMode);
        }
    }
}
