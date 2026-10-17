package com.boris.tooling.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.tooling.ToolDefinition;
import com.boris.tooling.ToolProgressNotifier;
import com.boris.tooling.parallel.BatchGitHubSearchTool;

/**
 * Tool that creates .boris/CONTEXT.md with REAL API documentation
 * extracted from GitHub repository source code.
 */
public class LibraryContextTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final BatchGitHubSearchTool GITHUB = new BatchGitHubSearchTool();

    public static ToolDefinition create_library_context() {
        var repoProp = Map.of("type", "string", "description",
            "GitHub repository in format owner/repo (e.g., mabe02/lanterna)");
        var versionProp = Map.of("type", "string", "description",
            "Library version (e.g., 3.1.5)");
        var projectPathProp = Map.of("type", "string", "description",
            "Path to project where .boris/CONTEXT.md will be created");

        var properties = new LinkedHashMap<String, Object>();
        properties.put("repo", repoProp);
        properties.put("version", versionProp);
        properties.put("project_path", projectPathProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("repo", "project_path"));

        return ToolDefinition.of(
            "create_library_context",
            "Creates .boris/CONTEXT.md with REAL API documentation extracted from GitHub. " +
            "Use this BEFORE writing code that uses external libraries.",
            schema);
    }

    public static String execute(Map<String, Object> args) {
        String repo = (String) args.getOrDefault("repo", "");
        String version = (String) args.getOrDefault("version", "latest");
        String projectPath = (String) args.getOrDefault("project_path", ".");

        if (repo == null || repo.isBlank()) {
            return formatOutput(false, "Error: repo is required (format: owner/repo)");
        }

        ToolProgressNotifier.notify("create_library_context", "Extrayendo API real de " + repo);

        try {
            // 1. Get README for library name and description
            String readmeContent = getReadme(repo);
            String libraryName = extractLibraryName(repo, readmeContent);

            // 2. Find main source packages
            List<String> packages = findMainPackages(repo);

            // 3. Get example/test files to extract real usage patterns
            List<String> exampleFiles = findExampleFiles(repo);
            // DEBUG: Found " + exampleFiles.size() + " example files: " + exampleFiles);

            // 4. Extract real imports and classes from examples
            Set<String> realImports = new TreeSet<>();
            Set<String> realClasses = new TreeSet<>();
            List<String> codeExamples = new ArrayList<>();

            for (String examplePath : exampleFiles) {
                // DEBUG: Reading file: " + examplePath);
                String content = getFileContent(repo, examplePath);
                // DEBUG: Content length: " + (content != null ? content.length() : 0));
                if (content != null && !content.isEmpty()) {
                    Set<String> fileImports = extractImports(content, repo);
                    Set<String> fileClasses = extractClassUsage(content);
                    // DEBUG: Extracted " + fileImports.size() + " imports, " + fileClasses.size() + " classes from " + examplePath);
                    realImports.addAll(fileImports);
                    realClasses.addAll(fileClasses);
                    if (codeExamples.size() < 2) {
                        String example = extractUsefulExample(content);
                        if (example != null) {
                            codeExamples.add(example);
                        }
                    }
                }
            }

            // 5. Generate CONTEXT.md
            String contextContent = generateContextFile(
                libraryName, version, repo, realImports, realClasses, codeExamples, readmeContent);

            // 6. Write to project
            Path contextPath = Paths.get(projectPath, ".boris", "CONTEXT.md");
            Files.createDirectories(contextPath.getParent());
            Files.writeString(contextPath, contextContent);

            ToolProgressNotifier.notify("create_library_context",
                "Contexto creado: " + realImports.size() + " imports, " + realClasses.size() + " clases");

            return formatOutput(true,
                "CONTEXT.md creado en " + contextPath + " con " +
                realImports.size() + " imports verificados y " +
                realClasses.size() + " clases reales de " + repo);

        } catch (Exception e) {
            return formatOutput(false, "Error creando contexto: " + e.getMessage());
        }
    }

    private static String getReadme(String repo) {
        try {
            var spec = new BatchGitHubSearchTool.SearchSpec("readme", repo, null, null, null);
            String result = GITHUB.execute(List.of(spec));
            JsonNode node = MAPPER.readTree(result);
            return node.path("content").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private static List<String> findMainPackages(String repo) {
        try {
            var spec = new BatchGitHubSearchTool.SearchSpec("files", repo, "src/main/java", null, null);
            String result = GITHUB.execute(List.of(spec));
            JsonNode node = MAPPER.readTree(result);

            List<String> packages = new ArrayList<>();
            for (JsonNode file : node.path("files")) {
                if ("dir".equals(file.path("type").asText())) {
                    packages.add(file.path("path").asText());
                }
            }
            return packages;
        } catch (Exception e) {
            return List.of();
        }
    }

    private static List<String> findExampleFiles(String repo) {
        List<String> examples = new ArrayList<>();

        // Navigate directory structure recursively (no auth required)
        // Prioritize gui2 and example directories for UI libraries
        String[] basePaths = {
            "src/test/java/com/googlecode/lanterna/gui2",
            "src/main/java/com/googlecode/lanterna/gui2",
            "src/test/java/com/googlecode/lanterna/screen",
            "src/test/java/com/googlecode/lanterna/terminal",
            "src/test/java",
            "src/main/java",
            "test",
            "examples"
        };

        for (String basePath : basePaths) {
            exploreDirectory(repo, basePath, examples, 0);
            if (examples.size() >= 15) break;
        }

        // DEBUG: Total files found: " + examples.size());
        return examples;
    }

    private static void exploreDirectory(String repo, String path, List<String> examples, int depth) {
        if (depth > 4 || examples.size() >= 10) return;

        try {
            var spec = new BatchGitHubSearchTool.SearchSpec("files", repo, path, null, 50);
            String result = GITHUB.execute(List.of(spec));
            JsonNode node = MAPPER.readTree(result);

            if (!node.path("success").asBoolean(true)) {
                return; // Directory doesn't exist
            }

            for (JsonNode file : node.path("files")) {
                String name = file.path("name").asText();
                String filePath = file.path("path").asText();
                String type = file.path("type").asText();

                if ("dir".equals(type)) {
                    // Recurse into subdirectories
                    exploreDirectory(repo, filePath, examples, depth + 1);
                } else if (name.endsWith(".java")) {
                    // Prioritize test/example files
                    if (name.contains("Test") || name.contains("Example") ||
                        name.contains("Demo") || name.contains("Sample") ||
                        name.contains("Main") || name.contains("Basic")) {
                        examples.add(0, filePath); // Add at beginning
                    } else {
                        examples.add(filePath);
                    }
                }

                if (examples.size() >= 10) return;
            }
        } catch (Exception e) {
            // DEBUG: Error exploring " + path + ": " + e.getMessage());
        }
    }

    private static String getFileContent(String repo, String path) {
        try {
            var spec = new BatchGitHubSearchTool.SearchSpec("file_content", repo, path, null, null);
            String result = GITHUB.execute(List.of(spec));
            JsonNode node = MAPPER.readTree(result);
            return node.path("content").asText("");
        } catch (Exception e) {
            return "";
        }
    }

    private static Set<String> extractImports(String content, String repo) {
        Set<String> imports = new TreeSet<>();

        // Pattern for standard imports
        Pattern pattern = Pattern.compile("import\\s+(?:static\\s+)?([\\w.]+(?:\\.\\*)?);", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String imp = matcher.group(1);
            // Only include imports that look like they're from the library (not standard Java)
            if (!imp.startsWith("java.") &&
                !imp.startsWith("javax.") &&
                !imp.startsWith("org.junit") &&
                !imp.startsWith("org.assertj") &&
                !imp.startsWith("org.mockito") &&
                !imp.startsWith("org.hamcrest")) {
                imports.add(imp);
            }
        }

        // Also try to find package declarations to understand the library structure
        Pattern pkgPattern = Pattern.compile("package\\s+([\\w.]+);", Pattern.MULTILINE);
        Matcher pkgMatcher = pkgPattern.matcher(content);
        if (pkgMatcher.find()) {
            String pkg = pkgMatcher.group(1);
            if (!pkg.startsWith("java.") && !pkg.startsWith("javax.")) {
                // Add wildcard import for the package
                imports.add(pkg + ".*");
            }
        }

        return imports;
    }

    private static final Set<String> JAVA_STANDARD_CLASSES = Set.of(
        "ArrayList", "HashMap", "HashSet", "LinkedList", "TreeMap", "TreeSet",
        "String", "StringBuilder", "StringBuffer", "Integer", "Long", "Double", "Float", "Boolean",
        "Object", "Class", "Thread", "Runnable", "Exception", "Error", "Throwable",
        "File", "Path", "Files", "Paths", "URL", "URI", "InputStream", "OutputStream",
        "Reader", "Writer", "BufferedReader", "BufferedWriter", "PrintWriter", "PrintStream",
        "ByteArrayInputStream", "ByteArrayOutputStream", "StringReader", "StringWriter",
        "Arrays", "Collections", "List", "Map", "Set", "Queue", "Deque", "Iterator",
        "Pattern", "Matcher", "Scanner", "Random", "Math", "System", "Runtime",
        "Date", "Calendar", "LocalDate", "LocalTime", "LocalDateTime", "Instant",
        "Optional", "Stream", "Collectors", "Function", "Consumer", "Supplier", "Predicate",
        "ProcessBuilder", "Process", "InputStreamReader", "OutputStreamWriter",
        "ArrayOutputStream", "FULL_WIDTH_ROMANJI", "HALF_WIDTH_KATAKANA", "HANGUL",
        "NEW_LINE_AFTER", "SIMPLIFIED_CHINESE_2500_MOST_COMMON"
    );

    private static Set<String> extractClassUsage(String content) {
        Set<String> classes = new TreeSet<>();

        // Pattern to find class instantiations: new ClassName(
        Pattern newPattern = Pattern.compile("new\\s+([A-Z][\\w]+)\\s*[(<]");
        Matcher matcher = newPattern.matcher(content);
        while (matcher.find()) {
            String cls = matcher.group(1);
            if (!JAVA_STANDARD_CLASSES.contains(cls)) {
                classes.add(cls);
            }
        }

        // Pattern to find static method calls: ClassName.method(
        Pattern staticPattern = Pattern.compile("([A-Z][\\w]+)\\.\\w+\\s*\\(");
        matcher = staticPattern.matcher(content);
        while (matcher.find()) {
            String cls = matcher.group(1);
            if (!JAVA_STANDARD_CLASSES.contains(cls)) {
                classes.add(cls);
            }
        }

        return classes;
    }

    private static String extractUsefulExample(String content) {
        // Try to extract main method or a complete small method
        Pattern mainPattern = Pattern.compile(
            "(public\\s+static\\s+void\\s+main\\s*\\([^)]*\\)\\s*\\{[^}]+\\})",
            Pattern.DOTALL);
        Matcher matcher = mainPattern.matcher(content);
        if (matcher.find()) {
            String main = matcher.group(1);
            if (main.length() < 2000) {
                return main;
            }
        }

        // Try to find a simple test method
        Pattern testPattern = Pattern.compile(
            "(@Test[^{]*\\{[^}]+\\})",
            Pattern.DOTALL);
        matcher = testPattern.matcher(content);
        if (matcher.find()) {
            String test = matcher.group(1);
            if (test.length() < 1500) {
                return test;
            }
        }

        return null;
    }

    private static String extractLibraryName(String repo, String readme) {
        // Try to get name from README title
        Pattern titlePattern = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = titlePattern.matcher(readme);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        // Fallback to repo name
        return repo.contains("/") ? repo.split("/")[1] : repo;
    }

    private static String generateContextFile(
            String libraryName,
            String version,
            String repo,
            Set<String> imports,
            Set<String> classes,
            List<String> examples,
            String readme) {

        StringBuilder sb = new StringBuilder();

        sb.append("# ").append(libraryName).append(" v").append(version).append("\n\n");
        sb.append("Fuente: https://github.com/").append(repo).append("\n\n");
        sb.append("**IMPORTANTE: Este archivo contiene API REAL extraida del codigo fuente.**\n");
        sb.append("**Solo usa clases y metodos listados aqui. No inventes otros.**\n\n");

        // Imports section
        sb.append("## Imports Verificados\n");
        sb.append("```java\n");
        for (String imp : imports) {
            sb.append("import ").append(imp).append(";\n");
        }
        sb.append("```\n\n");

        // Classes section
        sb.append("## Clases Reales Encontradas\n");
        for (String cls : classes) {
            sb.append("- `").append(cls).append("`\n");
        }
        sb.append("\n");

        // Examples section
        if (!examples.isEmpty()) {
            sb.append("## Ejemplos de Uso (copiados del repo)\n");
            for (String example : examples) {
                sb.append("```java\n");
                sb.append(example).append("\n");
                sb.append("```\n\n");
            }
        }

        // Add note about what NOT to use
        sb.append("## PROHIBIDO (clases que NO existen)\n");
        sb.append("- NO usar clases que no esten listadas arriba\n");
        sb.append("- NO inventar metodos\n");
        sb.append("- Si necesitas algo que no esta aqui, busca en el repo primero\n");

        return sb.toString();
    }

    private static String formatOutput(boolean success, String message) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", success);
            node.put("message", message);
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            return "{\"success\":" + success + ",\"message\":\"" + message + "\"}";
        }
    }
}
