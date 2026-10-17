package com.boris.tooling.parallel;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.task.TaskAborter;
import com.boris.tooling.ToolResultNotifier;
import com.boris.tooling.ToolProgressNotifier;
import com.boris.tooling.tool.ContextTracker;

/**
 * Herramienta para aplicar múltiples ediciones a diferentes archivos
 * en paralelo usando Virtual Threads.
 *
 * Genera diffs reales y los notifica a la UI para mostrar con colores.
 */
public class BatchEditTool {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

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
    }

    public String execute(List<FileEditSpec> edits, TaskAborter aborter) {
        if (edits == null || edits.isEmpty()) {
            return "{\"success\":false,\"error\":\"edits list is empty\"}";
        }

        // === VALIDACIÓN: Verificar código contra CONTEXT.md ===
        for (FileEditSpec edit : edits) {
            if (edit.path() != null && isCodeFile(edit.path())) {
                Path path = Path.of(edit.path()).toAbsolutePath();
                Path projectRoot = findProjectRoot(path);
                if (projectRoot != null) {
                    Path contextFile = projectRoot.resolve(".boris/CONTEXT.md");
                    if (Files.exists(contextFile)) {
                        String codeToValidate = edit.newText() != null ? edit.newText() : edit.content();
                        if (codeToValidate != null) {
                            String validationError = validateCodeAgainstContext(codeToValidate, contextFile);
                            if (validationError != null) {
                                return "{\"success\":false,\"error\":\"BLOQUEADO - POSIBLE ALUCINACIÓN en " +
                                       edit.path() + ":\\n" + validationError +
                                       "\\n\\nUSA search_context para verificar qué clases/métodos existen.\"}";
                            }
                        }
                    }
                }
            }
        }

        ToolProgressNotifier.notify("batch_apply_edits", "Aplicando " + edits.size() + " ediciones...");

        List<Future<EditResult>> futures = new ArrayList<>();

        java.util.Map<Path, List<FileEditSpec>> editsByPath = new java.util.LinkedHashMap<>();
        for (FileEditSpec edit : edits) {
            editsByPath.computeIfAbsent(Path.of(edit.path()).toAbsolutePath().normalize(), k -> new ArrayList<>()).add(edit);
        }

        for (java.util.Map.Entry<Path, List<FileEditSpec>> entry : editsByPath.entrySet()) {
            Path filePath = entry.getKey();
            List<FileEditSpec> fileEdits = entry.getValue();

            final List<FileEditSpec> serializedEdits = fileEdits;
            final Path serializedPath = filePath;

            futures.add(executor.submit(() -> {
                if (aborter != null && aborter.isAborted()) {
                    return new EditResult(serializedPath.toString(), false, null, "Aborted");
                }

                StringBuilder combinedDiff = new StringBuilder();
                for (FileEditSpec edit : serializedEdits) {
                    try {
                        EditResult result = applySingleEdit(edit, serializedPath, aborter);
                        if (!result.success()) {
                            return result;
                        }
                        if (result.diff() != null && !result.diff().isEmpty()) {
                            if (combinedDiff.length() > 0) {
                                combinedDiff.append("\n");
                            }
                            combinedDiff.append(result.diff());
                        }
                    } catch (Exception e) {
                        return new EditResult(serializedPath.toString(), false, null, "Edit failed: " + e.getMessage());
                    }
                }

                String finalDiff = combinedDiff.toString();
                if (!finalDiff.isEmpty()) {
                    ToolResultNotifier.notifyEdit(serializedPath.toString(), finalDiff, true);
                }

                return new EditResult(serializedPath.toString(), true, finalDiff, null);
            }));
        }

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
                    if (r.diff() != null && !r.diff().isEmpty()) {
                        item.put("diff", r.diff());
                    }
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

        root.put("totalFiles", editsByPath.size());
        root.put("successfulEdits", totalSuccess);
        root.put("failedEdits", totalFailed);

        try {
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            return "{\"success\":false,\"error\":\"JSON serialization failed\"}";
        }
    }

    public String execute(List<FileEditSpec> edits) {
        return execute(edits, null);
    }

    private EditResult applySingleEdit(FileEditSpec edit, Path path, TaskAborter aborter) {
        if (aborter != null && aborter.isAborted()) {
            return new EditResult(path.toString(), false, null, "Aborted");
        }

        if (edit.doDelete()) {
            try {
                Files.deleteIfExists(path);
                String diff = "- [FILE DELETED: " + path.getFileName() + "]";
                return new EditResult(path.toString(), true, diff, null);
            } catch (Exception e) {
                return new EditResult(path.toString(), false, null, "Delete failed: " + e.getMessage());
            }
        }

        if (edit.content() != null && !edit.hasReplace()) {
            try {
                String oldContent = Files.exists(path) ? Files.readString(path) : "";
                Files.writeString(path, edit.content());
                String diff = generateDiff(path.toString(), oldContent, edit.content());
                return new EditResult(path.toString(), true, diff, null);
            } catch (Exception e) {
                return new EditResult(path.toString(), false, null, "Write failed: " + e.getMessage());
            }
        }

        if (edit.hasReplace()) {
            try {
                if (!Files.exists(path)) {
                    return new EditResult(path.toString(), false, null, "File not found: " + path);
                }
                String content = Files.readString(path);
                if (content.contains(edit.oldText())) {
                    String newContent = content.replace(edit.oldText(), edit.newText());
                    Files.writeString(path, newContent);
                    String diff = generateReplaceDiff(path.toString(), edit.oldText(), edit.newText());
                    return new EditResult(path.toString(), true, diff, null);
                } else {
                    return new EditResult(path.toString(), false, null, "old_text not found in file");
                }
            } catch (Exception e) {
                return new EditResult(path.toString(), false, null, "Edit failed: " + e.getMessage());
            }
        }

        return new EditResult(path.toString(), false, null, "No valid edit operation specified");
    }

    private String generateReplaceDiff(String filePath, String oldText, String newText) {
        StringBuilder diff = new StringBuilder();
        diff.append("```diff\n");
        diff.append("--- ").append(filePath).append("\n");
        diff.append("+++ ").append(filePath).append("\n");

        String[] oldLines = oldText.split("\n", -1);
        String[] newLines = newText.split("\n", -1);

        for (String line : oldLines) {
            diff.append("- ").append(line).append("\n");
        }
        for (String line : newLines) {
            diff.append("+ ").append(line).append("\n");
        }

        diff.append("```");
        return diff.toString();
    }

    private String generateDiff(String filePath, String oldContent, String newContent) {
        if (oldContent.isEmpty()) {
            StringBuilder diff = new StringBuilder();
            diff.append("```diff\n");
            diff.append("--- /dev/null\n");
            diff.append("+++ ").append(filePath).append("\n");
            for (String line : newContent.split("\n", -1)) {
                diff.append("+ ").append(line).append("\n");
            }
            diff.append("```");
            return diff.toString();
        }

        StringBuilder diff = new StringBuilder();
        diff.append("```diff\n");
        diff.append("--- ").append(filePath).append("\n");
        diff.append("+++ ").append(filePath).append("\n");
        diff.append("@@ file replaced @@\n");
        diff.append("```");
        return diff.toString();
    }

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

    private static boolean isCodeFile(String path) {
        String lower = path.toLowerCase();
        return lower.endsWith(".java") || lower.endsWith(".kt") ||
               lower.endsWith(".py") || lower.endsWith(".js") ||
               lower.endsWith(".ts") || lower.endsWith(".tsx") ||
               lower.endsWith(".go") || lower.endsWith(".rs");
    }

    private static Path findProjectRoot(Path filePath) {
        Path current = filePath.toAbsolutePath().getParent();
        while (current != null) {
            if (Files.exists(current.resolve("pom.xml")) ||
                Files.exists(current.resolve("build.gradle")) ||
                Files.exists(current.resolve("package.json"))) {
                return current;
            }
            current = current.getParent();
        }
        return null;
    }

    private static String validateCodeAgainstContext(String code, Path contextFile) {
        try {
            String contextContent = Files.readString(contextFile);
            java.util.Set<String> standardClasses = java.util.Set.of(
                "ArrayList", "HashMap", "HashSet", "LinkedList", "StringBuilder",
                "String", "Integer", "Long", "Double", "Thread", "Exception",
                "File", "Date", "Random", "SimpleDateFormat", "Arrays", "List",
                "Map", "Set", "Properties", "System", "Math", "Object", "Class"
            );

            java.util.List<String> errors = new java.util.ArrayList<>();

            // Verificar instanciaciones: new ClassName(
            java.util.regex.Pattern newPattern = java.util.regex.Pattern.compile("new\\s+([A-Z][\\w]+)\\s*\\(");
            java.util.regex.Matcher newMatcher = newPattern.matcher(code);
            while (newMatcher.find()) {
                String className = newMatcher.group(1);
                if (standardClasses.contains(className)) continue;
                if (!contextContent.contains("### `" + className + "`") &&
                    !contextContent.contains("new " + className + "(")) {
                    errors.add("Clase '" + className + "' no encontrada en CONTEXT.md");
                }
            }

            // Verificar extends
            java.util.regex.Pattern extendsPattern = java.util.regex.Pattern.compile("extends\\s+([A-Z][\\w]+)");
            java.util.regex.Matcher extendsMatcher = extendsPattern.matcher(code);
            while (extendsMatcher.find()) {
                String className = extendsMatcher.group(1);
                if (standardClasses.contains(className)) continue;
                if (!contextContent.contains("### `" + className + "`")) {
                    errors.add("Clase padre '" + className + "' no encontrada en CONTEXT.md");
                }
            }

            if (!errors.isEmpty() && errors.size() <= 5) {
                return String.join("\\n", errors);
            }
        } catch (Exception e) {
            // Si no puede leer el contexto, no bloquear
        }
        return null;
    }
}
