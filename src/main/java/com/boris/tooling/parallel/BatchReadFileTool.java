package com.boris.tooling.parallel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.context.ProjectContext;
import com.boris.task.TaskAborter;
import com.boris.tooling.ToolProgressNotifier;

/**
 * Herramienta para leer archivos del sistema.
 * SECUENCIAL - sin paralelismo, un archivo a la vez.
 */
public class BatchReadFileTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public record FileResult(String path, boolean success, String content, String error, int lineCount) {}

    private static String resolvePathWithProject(String pathStr, String projectPath) {
        if (pathStr == null || pathStr.isBlank()) {
            return pathStr;
        }
        Path path = Paths.get(pathStr);
        if (path.isAbsolute()) {
            return pathStr;
        }
        if (projectPath != null && !projectPath.isBlank()) {
            return Paths.get(projectPath, pathStr).toString();
        }
        return pathStr;
    }

    /**
     * Lee archivos secuencialmente y devuelve un JSON agregado.
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

        List<String> uniquePaths = paths.stream().distinct().toList();

        String fileList = String.join(", ", uniquePaths);
        ToolProgressNotifier.notify("batch_read_files", "Leyendo " + uniquePaths.size() + " archivo(s): " + fileList);

        // Obtener projectPath para resolver rutas relativas
        String rawProjectPath = ProjectContext.getProjectPath();
        final String capturedProjectPath = (rawProjectPath == null || rawProjectPath.isBlank())
            ? System.getProperty("user.dir")
            : rawProjectPath;

        ObjectNode root = MAPPER.createObjectNode();
        ArrayNode filesArray = root.putArray("files");
        int totalSuccess = 0;
        int totalFailed = 0;

        // SECUENCIAL - un archivo a la vez
        for (String p : uniquePaths) {
            FileResult r = readFileTask(p, maxLinesPerFile, aborter, capturedProjectPath);
            ObjectNode item = filesArray.addObject();
            item.put("path", r.path());
            item.put("success", r.success());
            item.put("lines", r.lineCount());
            if (r.success()) {
                item.put("content", r.content());
                totalSuccess++;
            } else {
                item.put("error", r.error());
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
    private FileResult readFileTask(String pathStr, Integer maxLines, TaskAborter aborter, String projectPath) {
        if (aborter != null && aborter.isAborted()) {
            return new FileResult(pathStr, false, null, "Aborted", 0);
        }
        String resolvedPath = resolvePathWithProject(pathStr, projectPath);
        Path path = Paths.get(resolvedPath);
        if (!Files.exists(path)) {
            return new FileResult(pathStr, false, null, "File not found", 0);
        }
        if (!Files.isRegularFile(path)) {
            return new FileResult(pathStr, false, null, "Not a file", 0);
        }
        if (!ProjectContext.isInsideProject(path.toAbsolutePath())) {
            return new FileResult(pathStr, false, null,
                "Access denied: file is outside the project directory (" + projectPath + "). "
                    + "batch_read_files can only read files inside the current project. "
                    + "Do not read user home, caches (e.g. ~/.m2) or hidden folders; "
                    + "use create_library_context, query_symbols or query_methods for library code.", 0);
        }

        if (ProjectContext.isInsideAgentWorkspace(path.toAbsolutePath())) {
            return new FileResult(pathStr, false, null,
                "Access denied: .boris is agent internal workspace (index, dashboard, cached repos). "
                    + "Use query_symbols for signatures and query_framework_knowledge(framework, class_name) for class summaries.", 0);
        }

        try {
            List<String> lines = Files.readAllLines(path);
            int count = lines.size();
            if (maxLines != null && maxLines > 0 && lines.size() > maxLines) {
                lines = lines.subList(0, maxLines);
            }

            // Ingesta automática a nivel código: registrar ficha técnica de lo aprendido
            if (resolvedPath.endsWith(".java")) {
                com.boris.knowledge.KnowledgeStorageService.autoRecordJavaFile(path, Paths.get(projectPath));
            }

            return new FileResult(pathStr, true, String.join("\n", lines), null, count);
        } catch (Exception e) {
            return new FileResult(pathStr, false, null, e.getMessage(), 0);
        }
    }

    public void shutdown() {
        // No hay recursos que liberar (ya no usa executor)
    }
}
