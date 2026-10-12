package com.boris.tooling.parallel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Gestor de bloqueos por ruta para garantizar que dos hilos virtuales
 * no modifiquen el mismo archivo en el mismo instante.
 */
public class PathLockManager {
    private final ConcurrentHashMap<Path, ReentrantLock> locks;

    public PathLockManager() {
        this.locks = new ConcurrentHashMap<>();
    }

    /**
     * Obtiene o crea un lock para la ruta especificada.
     * Las rutas se normalizan para evitar duplicados por diferentes pathings.
     */
    public ReentrantLock getLockFor(Path path) {
        try {
            Path normalized = path.toAbsolutePath().normalize().toRealPath();
            return locks.computeIfAbsent(normalized, p -> new ReentrantLock());
        } catch (Exception e) {
            // Fallback to normalized path if realPath fails
            Path normalized = path.toAbsolutePath().normalize();
            return locks.computeIfAbsent(normalized, p -> new ReentrantLock());
        }
    }

    /**
     * Ejecuta una acción con el lock para la ruta especificada.
     * Garantiza que la acción se complete antes de que otro hilo acceda.
     */
    public <T> T withLock(Path path, java.util.concurrent.Callable<T> action) throws Exception {
        ReentrantLock lock = getLockFor(path);
        lock.lock();
        try {
            return action.call();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Ejecuta un Runnable con el lock para la ruta especificada.
     */
    public void withLock(Path path, Runnable action) {
        ReentrantLock lock = getLockFor(path);
        lock.lock();
        try {
            action.run();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Ejecuta una acción con el lock para la ruta especificada (sintaxis alternativa).
     * Sobrecarga para compatibilidad con WriteOperation.
     */
    public <T> T executeWithLock(Path path, java.util.concurrent.Callable<T> action) throws Exception {
        return withLock(path, action);
    }

    /**
     * Ejecuta una operación de escritura con el lock para la ruta especificada.
     */
    public <T> T executeWithLock(Path path, com.boris.tooling.parallel.ParallelToolDispatcher.WriteOperation<T> operation) throws Exception {
        ReentrantLock lock = getLockFor(path);
        lock.lock();
        try {
            return operation.execute();
        } finally {
            lock.unlock();
        }
    }

    /**
     * Verifica si dos rutas comparten el mismo lock.
     * Útil para determinar si operaciones pueden ejecutarse en paralelo.
     */
    public boolean arePathsConflicting(Path path1, Path path2) {
        try {
            Path n1 = path1.toAbsolutePath().normalize().toRealPath();
            Path n2 = path2.toAbsolutePath().normalize().toRealPath();
            return n1.equals(n2);
        } catch (Exception e) {
            Path n1 = path1.toAbsolutePath().normalize();
            Path n2 = path2.toAbsolutePath().normalize();
            return n1.equals(n2);
        }
    }

    /**
     * Devuelve el número de locks activos.
     */
    public int getActiveLockCount() {
        return locks.size();
    }
}
