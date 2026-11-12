package com.boris.context;

import java.nio.file.Path;

import com.boris.exceptions.BorisException;

/**
 * Resuelve las rutas del directorio de sesión ({PROYECTO}/.boris/{SESSION_ID})
 * y los archivos dentro de ella. Fuente única de verdad para la ubicación
 * del registro de búsquedas y los planes por sesión.
 */
public final class SessionDirectoryResolver {

    private SessionDirectoryResolver() {
    }

    /**
     * Devuelve el directorio de sesión: {projectRoot}/.boris/{sessionId}.
     *
     * @throws BorisException si no hay sessionId ni projectRoot (sin fallback a user.dir)
     */
    public static Path sessionDirectory() {
        String sessionId = SearchSessionContext.requireSessionId();
        String projectPath = ProjectContext.getProjectPath();
        if (projectPath == null || projectPath.isBlank()) {
            throw new BorisException("No project path set for session directory");
        }
        return Path.of(projectPath).resolve(".boris").resolve(sessionId).toAbsolutePath().normalize();
    }

    /**
     * Devuelve el archivo acumulativo de búsquedas: {sessionDir}/searchs.md.
     */
    public static Path searchesFile() {
        return sessionDirectory().resolve("searchs.md");
    }

    /**
     * Devuelve el directorio de planes de la sesión: {sessionDir}/plans.
     */
    public static Path plansDirectory() {
        return sessionDirectory().resolve("plans");
    }
}
