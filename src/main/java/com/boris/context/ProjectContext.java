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
        if (pathStr.startsWith("~/") || pathStr.equals("~")) {
            return pathStr.replaceFirst("^~", System.getProperty("user.home"));
        }
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
        Path projectRoot = projectRootOrCurrent();
        if (projectRoot == null) {
            return false;
        }
        Path agentWorkspace = projectRoot.resolve(".boris").normalize();
        Path target = absolutePath.toAbsolutePath().normalize();
        return target.startsWith(agentWorkspace);
    }

    public static Path projectRootOrCurrent() {
        String projectPath = getProjectPath();
        if (projectPath == null || projectPath.isBlank()) {
            projectPath = System.getProperty("user.dir");
        }
        if (projectPath == null || projectPath.isBlank()) {
            return null;
        }
        return Paths.get(projectPath).toAbsolutePath().normalize();
    }

    public static boolean isInsideProject(java.nio.file.Path absolutePath) {
        if (absolutePath == null) {
            return false;
        }
        Path projectRoot = projectRootOrCurrent();
        if (projectRoot == null) {
            return false;
        }
        Path target = absolutePath.toAbsolutePath().normalize();
        return target.equals(projectRoot) || target.startsWith(projectRoot);
    }
}
