package com.boris.llm.think;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Guardia anti-drilling para detectar cuando el modelo hace "drilling"
 * (bajar nivel por nivel en list_files) en lugar de usar el parámetro depth.
 *
 * <p>Rastrea las rutas de directorio accedidas por list_files en el turno actual.
 * Detecta cuando cada ruta nueva es hija directa de la anterior y frena cuando
 * detecta 3+ niveles consecutivos de drilling (2 pasos de drilling consecutivos).
 * Las rutas se normalizan a absolutas antes de comparar.</p>
 */
public class DirectoryDrillingGuard {

    private Path previousPath;
    private int drillingCount;

    /**
     * Registra un acceso a un directorio y detecta drilling.
     *
     * @param path ruta del directorio accedido (absoluta o relativa)
     * @return true si se detectó drilling (3+ niveles consecutivos), false en caso contrario
     */
    public boolean recordDirectoryAccess(String path) {
        if (path == null || path.isBlank()) {
            return false;
        }

        Path current = Paths.get(path).toAbsolutePath().normalize();

        if (previousPath == null) {
            previousPath = current;
            drillingCount = 0;
            return false;
        }

        boolean isDirectChild = current.getParent() != null
                && current.getParent().normalize().equals(previousPath.normalize());

        if (isDirectChild) {
            drillingCount++;
        } else {
            drillingCount = 0;
        }

        previousPath = current;

        return drillingCount >= 2;
    }

    /**
     * Limpia el estado del guardia entre turnos.
     */
    public void reset() {
        previousPath = null;
        drillingCount = 0;
    }
}
