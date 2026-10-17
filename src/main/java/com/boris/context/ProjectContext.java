package com.boris.context;

import java.nio.file.Path;
import java.nio.file.Paths;

public class ProjectContext {

    private static final ThreadLocal<String> currentProjectPath = new ThreadLocal<>();

    public static void setProjectPath(String path) {
        currentProjectPath.set(path);
    }

    public static String getProjectPath() {
        return currentProjectPath.get();
    }

    public static void clear() {
        currentProjectPath.remove();
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
