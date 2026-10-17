package com.boris.tooling.parallel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
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

/**
 * Batch directory listing tool using PARALLEL execution with Virtual Threads.
 * Lists multiple directories simultaneously.
 *
 * Features:
 * - Parallel listing of multiple directories
 * - Single directory works exactly like old ListFilesTool
 * - Returns file type, size, and name for each entry
 * - Progress notifications to UI
 */
public class BatchListDirectoriesTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long PARALLEL_TIMEOUT_MS = 30000;

    public record DirectoryEntry(String type, long size, String name) {}
    public record ListResult(String path, boolean success, List<DirectoryEntry> entries, String error) {}

    public String execute(List<String> paths, TaskAborter aborter) {
        if (paths == null || paths.isEmpty()) {
            return formatError("paths list is empty");
        }

        List<String> uniquePaths = paths.stream()
            .filter(p -> p != null && !p.isBlank())
            .distinct()
            .toList();

        if (uniquePaths.isEmpty()) {
            return formatError("no valid paths provided");
        }

        int totalDirs = uniquePaths.size();

        if (totalDirs == 1) {
            ToolProgressNotifier.notify("list_files", "Listando: " + uniquePaths.get(0));
        } else {
            ToolProgressNotifier.notify("list_files", "Listando " + totalDirs + " directorios en paralelo...");
        }

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<ListResult>> futures = new ArrayList<>();

        try {
            for (String path : uniquePaths) {
                futures.add(executor.submit(() -> listDirectory(path, aborter)));
            }

            List<ListResult> results = new ArrayList<>();
            int successCount = 0;
            int failCount = 0;

            for (Future<ListResult> future : futures) {
                try {
                    ListResult result = future.get(PARALLEL_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                    results.add(result);
                    if (result.success()) {
                        successCount++;
                    } else {
                        failCount++;
                    }
                } catch (Exception e) {
                    results.add(new ListResult("unknown", false, List.of(), "Listing timed out or failed: " + e.getMessage()));
                    failCount++;
                }
            }

            if (totalDirs == 1) {
                return formatSingleResult(results.get(0));
            } else {
                ToolProgressNotifier.notify("list_files", "Completado: " + successCount + " exitosos, " + failCount + " fallidos");
                return formatBatchResults(results, successCount, failCount);
            }
        } finally {
            executor.shutdown();
        }
    }

    public String execute(List<String> paths) {
        return execute(paths, null);
    }

    private ListResult listDirectory(String pathStr, TaskAborter aborter) {
        if (aborter != null && aborter.isAborted()) {
            return new ListResult(pathStr, false, List.of(), "Aborted");
        }

        if (pathStr == null || pathStr.isBlank()) {
            pathStr = ".";
        }

        // Resolver ruta usando ProjectContext (respeta el proyecto del usuario)
        String resolvedPath = ProjectContext.resolveRelativePath(pathStr);
        Path dirPath = Paths.get(resolvedPath);
        if (!Files.exists(dirPath)) {
            return new ListResult(pathStr, false, List.of(), "Directory not found: " + pathStr);
        }
        if (!Files.isDirectory(dirPath)) {
            return new ListResult(pathStr, false, List.of(), "Not a directory: " + pathStr);
        }

        try (var stream = Files.list(dirPath)) {
            List<DirectoryEntry> entries = new ArrayList<>();
            List<Path> sorted = stream.sorted().toList();

            for (Path entry : sorted) {
                String type = Files.isDirectory(entry) ? "D" : "F";
                long size = Files.size(entry);
                String name = entry.getFileName().toString();
                entries.add(new DirectoryEntry(type, size, name));
            }

            return new ListResult(pathStr, true, entries, null);
        } catch (IOException e) {
            return new ListResult(pathStr, false, List.of(), "Error listing directory: " + e.getMessage());
        }
    }

    private String formatSingleResult(ListResult result) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", result.success());

            if (result.success()) {
                StringBuilder sb = new StringBuilder();
                for (DirectoryEntry entry : result.entries()) {
                    sb.append(entry.type()).append(" ")
                      .append(String.format("%10d", entry.size())).append("  ")
                      .append(entry.name()).append("\n");
                }
                node.put("message", sb.toString().trim());
            } else {
                node.put("message", result.error());
            }

            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }

    private String formatBatchResults(List<ListResult> results, int successCount, int failCount) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("totalDirectories", results.size());
            root.put("successfulListings", successCount);
            root.put("failedListings", failCount);

            ArrayNode dirsArray = root.putArray("directories");
            for (ListResult r : results) {
                ObjectNode dirNode = dirsArray.addObject();
                dirNode.put("path", r.path());
                dirNode.put("success", r.success());

                if (r.success()) {
                    ArrayNode entriesArray = dirNode.putArray("entries");
                    for (DirectoryEntry entry : r.entries()) {
                        ObjectNode entryNode = entriesArray.addObject();
                        entryNode.put("type", entry.type());
                        entryNode.put("size", entry.size());
                        entryNode.put("name", entry.name());
                    }
                } else {
                    dirNode.put("error", r.error());
                }
            }

            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }

    private String formatError(String message) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", false);
            node.put("message", message);
            return MAPPER.writeValueAsString(node);
        } catch (Exception e) {
            throw new com.boris.exceptions.BorisException("Failed to format output", e);
        }
    }

    public void shutdown() {
        // No persistent executor to shut down
    }
}
