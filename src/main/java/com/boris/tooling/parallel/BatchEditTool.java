package com.boris.tooling.parallel;

import java.nio.file.Files;
import java.nio.file.Path;
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
import com.boris.tooling.tool.EditTool;
import com.boris.task.TaskAborter;

/**
 * Herramienta para aplicar múltiples ediciones a diferentes archivos
 * en paralelo usando Virtual Threads y PathLockManager.
 * 
 * Si las ediciones tocan archivos diferentes, se ejecutan en paralelo.
 * Si tocan el mismo archivo, se serializan automáticamente.
 */
public class BatchEditTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private final ParallelToolDispatcher dispatcher;

    public record EditResult(String path, boolean success, String diff, String error) {}

    public record FileEditSpec(
        String path,
        String oldText,
        String newText,
        boolean doDelete,
        String content
    ) {
        public boolean hasReplace() {
            return oldText != null && newText != null;
        }
    }

    public BatchEditTool() {
        this.dispatcher = new ParallelToolDispatcher(null);
    }

    /**
     * Aplica múltiples ediciones a archivos en paralelo.
     * 
     * @param edits Lista de especificaciones de edición
     * @param aborter Referencia al TaskAborter para cancelación (opcional)
     * @return JSON con el resultado de cada edición
     */
    public String execute(List<FileEditSpec> edits, TaskAborter aborter) {
        if (edits == null || edits.isEmpty()) {
            return "{\"success\":false,\"error\":\"edits list is empty\"}";
        }

        List<Future<EditResult>> futures = new ArrayList<>();
        
        // Group edits by path for proper locking
        java.util.Map<Path, List<FileEditSpec>> editsByPath = new java.util.LinkedHashMap<>();
        for (FileEditSpec edit : edits) {
            editsByPath.computeIfAbsent(Path.of(edit.path()).toAbsolutePath().normalize(), k -> new ArrayList<>()).add(edit);
        }

        // Create parallel tasks (one per file)
        for (java.util.Map.Entry<Path, List<FileEditSpec>> entry : editsByPath.entrySet()) {
            Path filePath = entry.getKey();
            List<FileEditSpec> fileEdits = entry.getValue();

            final List<FileEditSpec> serializedEdits = fileEdits;
            final Path serializedPath = filePath;
            
            futures.add(executor.submit(() -> {
                if (aborter != null && aborter.isAborted()) {
                    return new EditResult(serializedPath.toString(), false, null, "Aborted");
                }

                // If multiple edits on same file, apply sequentially
                for (FileEditSpec edit : serializedEdits) {
                    try {
                        EditResult result = applySingleEdit(edit, serializedPath, aborter);
                        if (!result.success) {
                            return result; // Stop on first failure
                        }
                    } catch (Exception e) {
                        return new EditResult(serializedPath.toString(), false, null, "Edit failed: " + e.getMessage());
                    }
                }
                return new EditResult(serializedPath.toString(), true, "Applied " + serializedEdits.size() + " edits", null);
            }));
        }

        // Collect results
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode resultsArray = root.putArray("results");
        int totalSuccess = 0;
        int totalFailed = 0;

        for (Future<EditResult> f : futures) {
            try {
                EditResult r = f.get(30, TimeUnit.SECONDS);
                ObjectNode item = resultsArray.addObject();
                item.put("path", r.path());
                item.put("success", r.success());
                if (r.success()) {
                    item.put("diff", r.diff());
                    totalSuccess++;
                } else {
                    item.put("error", r.error());
                    totalFailed++;
                }
            } catch (Exception e) {
                ObjectNode item = resultsArray.addObject();
                item.put("success", false);
                item.put("error", "Execution failed: " + e.getMessage());
                totalFailed++;
            }
        }

        root.put("totalFiles", edits.size());
        root.put("successfulEdits", totalSuccess);
        root.put("failedEdits", totalFailed);

        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"JSON serialization failed\"}";
        }
    }

    /**
     * Versión simplificada sin TaskAborter.
     */
    public String execute(List<FileEditSpec> edits) {
        return execute(edits, null);
    }

    /**
     * Aplica una única edición a un archivo.
     */
    private EditResult applySingleEdit(FileEditSpec edit, Path path, TaskAborter aborter) {
        if (aborter != null && aborter.isAborted()) {
            return new EditResult(path.toString(), false, null, "Aborted");
        }

        if (edit.doDelete) {
            try {
                Files.deleteIfExists(path);
                return new EditResult(path.toString(), true, "File deleted", null);
            } catch (Exception e) {
                return new EditResult(path.toString(), false, null, "Delete failed: " + e.getMessage());
            }
        }

        if (edit.content != null && !edit.hasReplace()) {
            // Write content (overwrite)
            try {
                Files.writeString(path, edit.content);
                return new EditResult(path.toString(), true, "Content written", null);
            } catch (Exception e) {
                return new EditResult(path.toString(), false, null, "Write failed: " + e.getMessage());
            }
        }

        if (edit.hasReplace()) {
            // Apply surgical edit
            try {
                String content = Files.readString(path);
                if (content.contains(edit.oldText)) {
                    String newContent = content.replace(edit.oldText, edit.newText);
                    Files.writeString(path, newContent);
                    return new EditResult(path.toString(), true, "Text replaced", null);
                } else {
                    return new EditResult(path.toString(), false, null, "old_text not found in file");
                }
            } catch (Exception e) {
                return new EditResult(path.toString(), false, null, "Edit failed: " + e.getMessage());
            }
        }

        return new EditResult(path.toString(), false, null, "No valid edit operation specified");
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
        if (dispatcher != null) {
            dispatcher.shutdown();
        }
    }
}
