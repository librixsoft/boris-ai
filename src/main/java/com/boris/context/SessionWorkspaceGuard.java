package com.boris.context;

import java.nio.file.Path;
import java.nio.file.Paths;

import com.boris.exceptions.SessionWorkspaceViolationException;

/**
 * Guard único de verdad para el workspace de sesión (.boris/{sessionId}).
 * Solo permite escribir archivos .md directamente en {session}/plans/.
 * Bloquea searchs.md (escrito por sistema vía SearchContextWriter), cualquier otro archivo
 * dentro de .boris/{sessionId}, y subdirectorios bajo plans/.
 */
public final class SessionWorkspaceGuard {

    private SessionWorkspaceGuard() {
    }

    /**
     * Valida que la ruta sea escribible por un tool del agente.
     *
     * @param path ruta absoluta o relativa a validar
     * @throws SessionWorkspaceViolationException si la ruta está dentro de .boris/{sessionId}
     *         y no es un archivo .md directo en {session}/plans/
     */
    public static void assertWritable(String path) {
        String sessionId = SearchSessionContext.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            return;
        }

        String projectPath = ProjectContext.getProjectPath();
        if (projectPath == null || projectPath.isBlank()) {
            return;
        }

        // Resolver ruta a absoluta
        String resolvedPathStr = ProjectContext.resolveRelativePath(path);
        Path target = Paths.get(resolvedPathStr).toAbsolutePath().normalize();

        // Directorio de sesión y de planes
        Path sessionDir = Paths.get(projectPath)
                .resolve(".boris")
                .resolve(sessionId)
                .toAbsolutePath()
                .normalize();
        Path plansDir = sessionDir.resolve("plans").toAbsolutePath().normalize();

        // Si la ruta está fuera de .boris/{sessionId}, no bloqueamos
        if (!target.startsWith(sessionDir)) {
            return;
        }

        // La ruta está dentro de .boris/{sessionId}/
        if (target.startsWith(plansDir)) {
            // Dentro de plans/: solo permitir archivos .md directos (sin subdirectorios)
            Path parent = target.getParent();
            if (parent != null && parent.normalize().equals(plansDir.normalize())) {
                String fileName = target.getFileName().toString();
                if (fileName.endsWith(".md")) {
                    return; // .md directo en plans/ está permitido
                }
            }
            throw new SessionWorkspaceViolationException(
                    "Solo se permiten archivos .md directamente en " + plansDir
                            + ". Ruta bloqueada: " + path);
        }

        // Dentro de .boris/{sessionId} pero fuera de plans/: bloquear todo
        // (incluye searchs.md, que lo escribe el sistema vía SearchContextWriter, no vía tool)
        throw new SessionWorkspaceViolationException(
                "No se puede escribir dentro de .boris/{sessionId} salvo archivos .md en plans/"
                        + ". Ruta bloqueada: " + path);
    }
}
