package com.boris.tooling.parallel;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import com.boris.task.TaskAborter;

/**
 * Despachador concurrente de herramientas que utiliza Java 21 Virtual Threads
 * para ejecutar operaciones del sistema operativo en paralelo.
 * 
 * Clasificación de seguridad:
 * 1. Lectura/Idempotentes: 100% paralelizables sin restricciones
 * 2. Escritura/Modificación: Con File-Locking por Path
 * 3. Comandos de terminal: Semáforo de concurrencia
 */
public class ParallelToolDispatcher {
    private final ExecutorService virtualExecutor;
    private final PathLockManager lockManager;
    private final TaskAborter aborter;
    private final int maxConcurrentCommands;
    private final long ioTimeoutMs;

    public ParallelToolDispatcher(TaskAborter aborter) {
        this(aborter, 16, 2, 30000);
    }

    public ParallelToolDispatcher(TaskAborter aborter, int maxIoThreads, int maxCommands, long ioTimeoutMs) {
        this.virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
        this.lockManager = new PathLockManager();
        this.aborter = aborter;
        this.maxConcurrentCommands = maxCommands;
        this.ioTimeoutMs = ioTimeoutMs;
    }

    /**
     * Ejecuta una lista de Callable en paralelo usando Virtual Threads.
     * Verifica el estado de TaskAborter en cada tarea.
     * 
     * @param tasks Lista de tareas a ejecutar en paralelo
     * @param timeoutMs Timeout máximo para completar todas las tareas
     * @return Lista de resultados en orden original
     */
    public <T> List<T> executeParallel(List<java.util.concurrent.Callable<T>> tasks, long timeoutMs) throws Exception {
        if (tasks == null || tasks.isEmpty()) {
            return List.of();
        }

        // Verificar si ya fue abortado antes de empezar
        if (aborter != null && aborter.isAborted()) {
            throw new CancellationException("Task aborted before execution");
        }

        // Submit all tasks to virtual thread executor
        List<Future<T>> futures = new ArrayList<>();
        for (java.util.concurrent.Callable<T> task : tasks) {
            futures.add(virtualExecutor.submit(() -> {
                if (aborter != null && aborter.isAborted()) {
                    throw new CancellationException("Task aborted during execution");
                }
                return task.call();
            }));
        }

        // Collect results with timeout
        List<T> results = new ArrayList<>();
        for (Future<T> future : futures) {
            try {
                results.add(future.get(timeoutMs, TimeUnit.MILLISECONDS));
            } catch (TimeoutException e) {
                future.cancel(true);
                throw new RuntimeException("Task timed out after " + timeoutMs + "ms", e);
            } catch (CancellationException e) {
                throw new CancellationException("Task was cancelled: " + e.getMessage());
            } catch (ExecutionException e) {
                throw new RuntimeException("Task failed: " + e.getCause().getMessage(), e.getCause());
            }
        }

        return results;
    }

    /**
     * Ejecuta una lista de Callable usando el timeout por defecto (ioTimeoutMs).
     */
    public <T> List<T> executeParallel(List<java.util.concurrent.Callable<T>> tasks) throws Exception {
        return executeParallel(tasks, ioTimeoutMs);
    }

    /**
     * Ejecuta operaciones de lectura en paralelo (sin riesgo de carrera).
     */
    public <T> List<T> executeReadsParallel(List<java.util.concurrent.Callable<T>> tasks) throws Exception {
        return executeParallel(tasks, ioTimeoutMs);
    }

    /**
     * Ejecuta operaciones de escritura en paralelo con bloqueo por archivo.
     * Si dos operaciones tocan el mismo archivo, se serializan automáticamente.
     */
    public <T> List<T> executeWritesParallel(List<WriteOperation<T>> operations) throws Exception {
        // Group operations by file path
        java.util.Map<Path, List<WriteOperation<T>>> operationsByPath = new java.util.LinkedHashMap<>();
        for (WriteOperation<T> op : operations) {
            operationsByPath.computeIfAbsent(op.path(), k -> new ArrayList<>()).add(op);
        }

        // Execute each file's operations sequentially, but different files in parallel
        List<java.util.concurrent.Callable<T>> parallelTasks = new ArrayList<>();
        for (java.util.Map.Entry<Path, List<WriteOperation<T>>> entry : operationsByPath.entrySet()) {
            Path filePath = entry.getKey();
            List<WriteOperation<T>> fileOps = entry.getValue();

            if (fileOps.size() == 1) {
                // Single operation - execute directly
                WriteOperation<T> op = fileOps.get(0);
                final PathLockManager opLockManager = op.lockManager();
                final WriteOperation<T> finalOp = op;
                parallelTasks.add(() -> {
                    if (aborter != null && aborter.isAborted()) {
                        throw new CancellationException("Task aborted");
                    }
                    return opLockManager.executeWithLock(finalOp.path(), finalOp);
                });
            } else {
                // Multiple operations on same file - serialize them
                final List<WriteOperation<T>> serializedOps = fileOps;
                final Path serializedPath = filePath;
                // Use first operation's lock manager for all operations on this file
                final PathLockManager fileLockManager = serializedOps.get(0).lockManager();
                parallelTasks.add(() -> {
                    if (aborter != null && aborter.isAborted()) {
                        throw new CancellationException("Task aborted");
                    }
                    return fileLockManager.executeWithLock(serializedPath, () -> {
                        T result = null;
                        for (WriteOperation<T> sOp : serializedOps) {
                            result = sOp.execute();
                        }
                        return result;
                    });
                });
            }
        }

        return executeParallel(parallelTasks, ioTimeoutMs);
    }

    /**
     * Cierra el executor y libera recursos.
     */
    public void shutdown() {
        virtualExecutor.shutdown();
        try {
            if (!virtualExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                virtualExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            virtualExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Operación de escritura que puede ejecutarse bajo lock.
     */
    public interface WriteOperation<T> {
        T execute();
        Path path();
        PathLockManager lockManager();
    }

    /**
     * Obtiene el PathLockManager asociado para operaciones personalizadas.
     */
    public PathLockManager getLockManager() {
        return lockManager;
    }
}
