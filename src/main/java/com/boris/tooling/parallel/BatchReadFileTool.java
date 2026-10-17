package com.boris.tooling.parallel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.context.ProjectContext;
import com.boris.task.TaskAborter;
import com.boris.tooling.ToolProgressNotifier;
import com.boris.tooling.tool.ContextTracker;

/**
 * Herramienta para leer múltiples archivos concurrentemente del sistema
 * usando Virtual Threads de Java 21.
 * 
 * Beneficio: 1 turno de LLM reemplaza N turnos individuales.
 * Ahorro de tiempo: de ~45s a ~8s para 5 archivos.
 */
public class BatchReadFileTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public record FileResult(String path, boolean success, String content, String error, int lineCount) {}

    /**
     * Lee múltiples archivos en paralelo y devuelve un JSON agregado.
     * 
     * @param paths Lista de rutas de archivos a leer
     * @param maxLinesPerFile Líneas máximas a leer por archivo (null = todas)
     * @param aborter Referencia al TaskAborter para cancelación (opcional)
     * @return JSON con el contenido de cada archivo y su estado
     */
    public String execute(List<String> paths, Integer maxLinesPerFile, TaskAborter aborter) {
        if (paths == null || paths.isEmpty()) {
            try {
                return MAPPER.writeValueAsString(Map.of("success", false, "error", "paths list is empty"));
            } catch (Exception e) {
                return "{\"success\":false,\"error\":\"paths list is empty\"}";
            }
        }

        List<Future<FileResult>> futures = new ArrayList<>();
        List<String> uniquePaths = paths.stream().distinct().toList();

        String fileList = String.join(", ", uniquePaths);
        ToolProgressNotifier.notify("batch_read_files", "Leyendo " + uniquePaths.size() + " archivo(s) en paralelo: " + fileList);

        for (String p : uniquePaths) {
            futures.add(executor.submit(() -> readFileTask(p, maxLinesPerFile, aborter)));
        }

        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode filesArray = root.putArray("files");
        int totalSuccess = 0;
        int totalFailed = 0;

        for (Future<FileResult> f : futures) {
            try {
                FileResult r = f.get(15, TimeUnit.SECONDS);
                ObjectNode item = filesArray.addObject();
                item.put("path", r.path());
                item.put("success", r.success());
                item.put("lines", r.lineCount());
                if (r.success()) {
                    item.put("content", r.content());
                    totalSuccess++;
                    // Track CONTEXT.md reads for WriteTool verification
                    ContextTracker.markAsRead(r.path());
                } else {
                    item.put("error", r.error());
                    totalFailed++;
                }
            } catch (Exception e) {
                ObjectNode item = filesArray.addObject();
                item.put("path", "unknown");
                item.put("success", false);
                item.put("error", "Read failed: " + e.getMessage());
                totalFailed++;
            }
        }

        root.put("totalFiles", paths.size());
        root.put("successfulReads", totalSuccess);
        root.put("failedReads", totalFailed);
        
        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"JSON serialization failed: \" + e.getMessage()}";
        }
    }

    /**
     * Versión simplificada sin TaskAborter.
     */
    public String execute(List<String> paths, Integer maxLinesPerFile) {
        return execute(paths, maxLinesPerFile, null);
    }

    /**
     * Ejecuta la tarea de lectura de un solo archivo.
     */
    private FileResult readFileTask(String pathStr, Integer maxLines, TaskAborter aborter) {
        if (aborter != null && aborter.isAborted()) {
            return new FileResult(pathStr, false, null, "Aborted", 0);
        }
        String resolvedPath = ProjectContext.resolveRelativePath(pathStr);
        Path path = Paths.get(resolvedPath);
        if (!Files.exists(path)) {
            return new FileResult(pathStr, false, null, "File not found", 0);
        }
        if (!Files.isRegularFile(path)) {
            return new FileResult(pathStr, false, null, "Not a file", 0);
        }

        try {
            List<String> lines = Files.readAllLines(path);
            int count = lines.size();
            if (maxLines != null && maxLines > 0 && lines.size() > maxLines) {
                lines = lines.subList(0, maxLines);
            }
            return new FileResult(pathStr, true, String.join("\n", lines), null, count);
        } catch (Exception e) {
            return new FileResult(pathStr, false, null, e.getMessage(), 0);
        }
    }

    /**
     * Cierra el executor y libera recursos.
     */
    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}
