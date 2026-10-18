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
            pathStr = ".";
        }
        Path path = Paths.get(pathStr);
        if (path.isAbsolute()) {
            return pathStr;
        }
        // Try project path first, fallback to current working directory
        String projectPath = getProjectPath();
        if (projectPath == null || projectPath.isBlank()) {
            projectPath = System.getProperty("user.dir");
        }
        if (projectPath != null && !projectPath.isBlank()) {
            return Paths.get(projectPath, pathStr).normalize().toString();
        }
        return pathStr;
    }

    public static boolean isInsideAgentWorkspace(java.nio.file.Path absolutePath) {
        if (absolutePath == null) {
            return false;
        }
        String projectPath = getProjectPath();
        if (projectPath == null || projectPath.isBlank()) {
            projectPath = System.getProperty("user.dir");
        }
        if (projectPath == null || projectPath.isBlank()) {
            return false;
        }
        Path agentWorkspace = Paths.get(projectPath, ".boris").normalize();
        Path target = absolutePath.toAbsolutePath().normalize();
        return target.startsWith(agentWorkspace);
    }
}
