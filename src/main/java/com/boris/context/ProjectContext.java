package com.boris.context;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ProjectContext {

    // Primary: InheritableThreadLocal for child threads
    private static final InheritableThreadLocal<String> currentProjectPath = new InheritableThreadLocal<>();

    // Fallback: static variable for thread pools that don't inherit ThreadLocal
    private static volatile String globalProjectPath;

    public static void setProjectPath(String path) {
        currentProjectPath.set(path);
        globalProjectPath = path; // Also set the global fallback
    }

    public static String getProjectPath() {
        String path = currentProjectPath.get();
        if (path == null || path.isBlank()) {
            // Fallback to global path if ThreadLocal is empty
            path = globalProjectPath;
        }
        return path;
    }

    public static void clear() {
        currentProjectPath.remove();
        globalProjectPath = null;
    }

    public static String resolveRelativePath(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) {
            return pathStr;
        }
        Path path = Paths.get(pathStr);
        if (path.isAbsolute()) {
            return pathStr;
        }
        String projectPath = getProjectPath();
        if (projectPath != null && !projectPath.isBlank()) {
            return Paths.get(projectPath, pathStr).toString();
        }
        return pathStr;
    }
}
