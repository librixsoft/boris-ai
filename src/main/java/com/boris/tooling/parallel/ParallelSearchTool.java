package com.boris.tooling.parallel;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.context.ProjectContext;
import com.boris.task.TaskAborter;

/**
 * Herramienta de búsqueda multi-hilo en el workspace usando Java Virtual Threads.
 * Busca patrones de texto en archivos que coinciden con un patrón de archivo.
 * 
 * Equivalente a 'grep -r' pero con soporte concurrente optimizado.
 */
public class ParallelSearchTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    public record SearchMatch(String file, int line, String content, int lineNumber) {}

    /**
     * Realiza una búsqueda multi-hilo en el workspace.
     * 
     * @param query Término o patrón a buscar
     * @param filePattern Patrón de archivo para filtrar (ej: "*.java", "pom.xml")
     * @param maxResults Número máximo de resultados
     * @param basePath Directorio base para buscar (null = directorio actual)
     * @param aborter Referencia al TaskAborter para cancelación (opcional)
     * @return JSON con los resultados de búsqueda
     */
    public String execute(String query, String filePattern, Integer maxResults, String basePath, TaskAborter aborter) {
        if (query == null || query.isBlank()) {
            return "{\"success\":false,\"error\":\"query is required\"}";
        }

        String resolvedBase = basePath != null ? basePath : ProjectContext.resolveRelativePath(".");
        Path searchRoot = Paths.get(resolvedBase).toAbsolutePath().normalize();
        if (!Files.isDirectory(searchRoot)) {
            String fallback = ProjectContext.resolveRelativePath(".");
            searchRoot = Paths.get(fallback).toAbsolutePath().normalize();
        }

        // Find all matching files first
        List<Path> matchingFiles = new ArrayList<>();
        try {
            List<String> filePatterns = parseFilePattern(filePattern);
            Files.walk(searchRoot)
                .filter(Files::isRegularFile)
                .filter(p -> matchesFilePattern(p, filePatterns))
                .limit(maxResults != null ? maxResults * 10 : 10000)
                .forEach(matchingFiles::add);
        } catch (IOException e) {
            return "{\"success\":false,\"error\":\"Failed to walk directory: \" + e.getMessage()}";
        }

        if (matchingFiles.isEmpty()) {
            return "{\"success\":true,\"results\":[],\"totalFiles\":0,\"matches\":0}";
        }

        // Search in parallel
        Pattern regexPattern = Pattern.compile(query, Pattern.CASE_INSENSITIVE);
        List<Callable<List<SearchMatch>>> tasks = new ArrayList<>();
        
        for (Path file : matchingFiles) {
            final Path filePath = file;
            tasks.add(() -> searchFile(regexPattern, filePath, aborter));
        }

        // Execute all tasks in parallel
        List<Future<List<SearchMatch>>> futures = new ArrayList<>();
        for (Callable<List<SearchMatch>> task : tasks) {
            futures.add(executor.submit(task));
        }

        // Collect results
        List<SearchMatch> allMatches = new ArrayList<>();
        for (Future<List<SearchMatch>> f : futures) {
            try {
                List<SearchMatch> matches = f.get(10, TimeUnit.SECONDS);
                if (matches != null) {
                    allMatches.addAll(matches);
                }
            } catch (Exception e) {
                // Skip failed files
            }
        }

        // Sort by file then line number
        allMatches.sort((a, b) -> {
            int cmp = a.file().compareTo(b.file());
            if (cmp != 0) return cmp;
            return Integer.compare(a.lineNumber(), b.lineNumber());
        });

        // Limit results
        if (maxResults != null && maxResults > 0 && allMatches.size() > maxResults) {
            allMatches = allMatches.subList(0, maxResults);
        }

        // Build JSON response
        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode resultsArray = root.putArray("results");
        int totalFiles = matchingFiles.size();
        int matchedFiles = 0;

        for (SearchMatch match : allMatches) {
            ObjectNode item = resultsArray.addObject();
            item.put("file", match.file());
            item.put("line", match.line());
            item.put("content", match.content());
            item.put("lineNumber", match.lineNumber());
        }

        // Count unique files with matches
        java.util.Set<String> uniqueFiles = new java.util.HashSet<>();
        for (SearchMatch match : allMatches) {
            uniqueFiles.add(match.file());
        }
        matchedFiles = uniqueFiles.size();

        root.put("totalFilesScanned", totalFiles);
        root.put("filesWithMatches", matchedFiles);
        root.put("totalMatches", allMatches.size());

        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"JSON serialization failed\"}";
        }
    }

    /**
     * Versión simplificada sin TaskAborter.
     */
    public String execute(String query, String filePattern, Integer maxResults, String basePath) {
        return execute(query, filePattern, maxResults, basePath, null);
    }

    /**
     * Busca en un solo archivo (ejecutado en Virtual Thread).
     */
    private List<SearchMatch> searchFile(Pattern pattern, Path filePath, TaskAborter aborter) {
        try {
            if (aborter != null && aborter.isAborted()) {
                return List.of();
            }
            
            List<String> lines = Files.readAllLines(filePath);
            List<SearchMatch> matches = new ArrayList<>();
            
            for (int i = 0; i < lines.size(); i++) {
                if (aborter != null && aborter.isAborted()) {
                    return matches;
                }
                String line = lines.get(i);
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    matches.add(new SearchMatch(
                        filePath.toString(),
                        i + 1,
                        line.trim(),
                        i + 1
                    ));
                }
            }
            return matches;
        } catch (IOException e) {
            return List.of();
        }
    }

    /**
     * Pasa un patrón de archivo como "*.java" a una lista de patrones.
     */
    private List<String> parseFilePattern(String filePattern) {
        if (filePattern == null || filePattern.isBlank()) {
            return List.of("*");
        }
        return List.of(filePattern.split("\\s*,\\s*"));
    }

    /**
     * Verifica si un archivo coincide con algún patrón.
     */
    private boolean matchesFilePattern(Path path, List<String> patterns) {
        String fileName = path.getFileName().toString();
        for (String pattern : patterns) {
            if (pattern.equals("*") || fileName.equals(pattern)) {
                return true;
            }
            if (pattern.startsWith("*")) {
                String ext = pattern.substring(1);
                if (fileName.endsWith(ext)) {
                    return true;
                }
            }
        }
        return false;
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
