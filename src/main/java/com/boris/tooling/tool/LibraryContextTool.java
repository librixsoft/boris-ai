package com.boris.tooling.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.tooling.ToolDefinition;
import com.boris.tooling.ToolProgressNotifier;
import com.boris.tooling.parallel.BatchGitHubSearchTool;

/**
 * Tool that creates/updates .boris/CONTEXT.md with REAL API documentation
 * extracted from GitHub repository source code.
 *
 * OPTIMIZATIONS:
 * - Cache check: skips GitHub calls if context exists and is fresh
 * - Multi-library: supports multiple libraries in one file
 * - Smart append: adds new libraries without losing existing ones
 */
public class LibraryContextTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final BatchGitHubSearchTool GITHUB = new BatchGitHubSearchTool();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int CACHE_DAYS = 15;

    /**
     * Metadata for a single library in CONTEXT.md
     */
    private static class LibraryMeta {
        String repo;
        String version;
        LocalDateTime created;

        LibraryMeta(String repo, String version, LocalDateTime created) {
            this.repo = repo;
            this.version = version;
            this.created = created;
        }
    }

    /**
     * Parsed CONTEXT.md structure
     */
    private static class ContextFile {
        List<LibraryMeta> libraries = new ArrayList<>();
        Map<String, String> librarySections = new LinkedHashMap<>();

        boolean hasLibrary(String repo, String version) {
            return libraries.stream()
                .anyMatch(lib -> lib.repo.equals(repo) &&
                         lib.version.equals(version) &&
                         lib.created.isAfter(LocalDateTime.now().minusDays(CACHE_DAYS)));
        }
    }

    public static ToolDefinition create_library_context() {
        var repoProp = Map.of("type", "string", "description",
            "GitHub repository in format owner/repo (e.g., mabe02/lanterna)");
        var versionProp = Map.of("type", "string", "description",
            "Library version (e.g., 3.1.5)");
        var projectPathProp = Map.of("type", "string", "description",
            "Path to project where .boris/CONTEXT.md will be created");
        var forceProp = Map.of("type", "boolean", "description",
            "Force recreation even if context exists (default: false)");

        var properties = new LinkedHashMap<String, Object>();
        properties.put("repo", repoProp);
        properties.put("version", versionProp);
        properties.put("project_path", projectPathProp);
        properties.put("force", forceProp);

        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", List.of("repo", "project_path"));

        return ToolDefinition.of(
            "create_library_context",
            "Creates/updates .boris/CONTEXT.md with REAL API documentation from GitHub. " +
            "Supports multiple libraries. Skips GitHub if context already exists and is fresh (<7 days). " +
            "Use force=true to recreate.",
            schema);
    }

    public static String execute(Map<String, Object> args) {
        String repo = (String) args.getOrDefault("repo", "");
        String version = (String) args.getOrDefault("version", "latest");
        String projectPath = (String) args.getOrDefault("project_path", ".");
        boolean force = Boolean.TRUE.equals(args.get("force"));

        if (repo == null || repo.isBlank()) {
            return formatOutput(false, "Error: repo is required (format: owner/repo)");
        }

        Path contextPath = Paths.get(projectPath, ".boris", "CONTEXT.md");

        // === CACHE CHECK ===
        if (!force && Files.exists(contextPath)) {
            try {
                ContextFile existing = parseContextFile(contextPath);
                if (existing.hasLibrary(repo, version)) {
                    ToolProgressNotifier.notify("create_library_context",
                        "Cache hit: " + repo + " v" + version + " ya existe");
                    return formatOutput(true,
                        "CONTEXT.md ya contiene " + repo + " v" + version +
                        " (cache válido por " + CACHE_DAYS + " días). Usa force=true para recrear.");
                }
            } catch (Exception e) {
                // If parsing fails, recreate
            }
        }

        ToolProgressNotifier.notify("create_library_context", "Extrayendo API real de " + repo);

        try {
            // 1. Get README for library name and description
            String readmeContent = getReadme(repo);

            // Si GitHub falla (rate limit, etc) y existe CONTEXT.md previo → usarlo como fallback
            // Solo si NO es force y ya tiene exactamente este repo+versión
            if (!force && (readmeContent == null || readmeContent.isBlank()) && Files.exists(contextPath)) {
                try {
                    ContextFile existing = parseContextFile(contextPath);
                    // Solo usar fallback si ya tiene este repo+versión exacta
                    boolean hasExactMatch = existing.libraries.stream()
                        .anyMatch(lib -> lib.repo.equals(repo) && lib.version.equals(version));
                    if (hasExactMatch) {
                        ToolProgressNotifier.notify("create_library_context",
                            "GitHub no disponible, usando cache existente");
                        return formatOutput(true,
                            "GitHub no responde (rate limit?). Usando CONTEXT.md existente para " + repo +
                            " v" + version + ". Intenta más tarde con force=true.");
                    }
                } catch (Exception e) {
                    // Si no puede parsear, continuar intentando crear
                }
            }

            String libraryName = extractLibraryName(repo, readmeContent);

            // 2. Find example/test files to extract real usage patterns
            List<String> exampleFiles = findExampleFiles(repo);

            // 3. Extract real imports and classes from examples
            Set<String> realImports = new TreeSet<>();
            Set<String> realClasses = new TreeSet<>();
            List<String> codeExamples = new ArrayList<>();
            Map<String, List<String>> classMethods = new LinkedHashMap<>();

            for (String examplePath : exampleFiles) {
                String content = getFileContent(repo, examplePath);
                if (content != null && !content.isEmpty()) {
                    realImports.addAll(extractImports(content, repo));
                    realClasses.addAll(extractClassUsage(content));
                    extractMethodCalls(content, classMethods);

                    if (codeExamples.size() < 3) {
                        String example = extractUsefulExample(content);
                        if (example != null && example.length() > 100) {
                            codeExamples.add(example);
                        }
                    }
                }
            }

            // 4. Read main source files to extract public methods
            List<String> mainSourceFiles = findMainSourceFiles(repo);
            for (String sourceFile : mainSourceFiles) {
                String content = getFileContent(repo, sourceFile);
                if (content != null && !content.isEmpty()) {
                    extractPublicMethods(content, classMethods);
                    realImports.addAll(extractImports(content, repo));
                }
            }

            // 5. Generate section for this library
            String librarySection = generateLibrarySection(
                libraryName, version, repo, realImports, realClasses, classMethods, codeExamples);

            // 6. Merge with existing context or create new
            String finalContent = mergeOrCreateContext(contextPath, repo, version, librarySection);

            // 7. Write to project
            Files.createDirectories(contextPath.getParent());
            Files.writeString(contextPath, finalContent);

            ToolProgressNotifier.notify("create_library_context",
                "Contexto creado: " + realImports.size() + " imports, " + realClasses.size() + " clases");

            return formatOutput(true,
                "CONTEXT.md actualizado con " + repo + " v" + version + ": " +
                realImports.size() + " imports, " + realClasses.size() + " clases");

        } catch (Exception e) {
            // Fallback: si hay error pero existe CONTEXT.md previo, usarlo
            if (Files.exists(contextPath)) {
                return formatOutput(true,
                    "Error contactando GitHub: " + e.getMessage() +
                    ". Usando CONTEXT.md existente (puede estar desactualizado).");
            }
            return formatOutput(false, "Error creando contexto: " + e.getMessage());
        }
    }

    // === PARSING & MERGING ===

    private static ContextFile parseContextFile(Path path) throws IOException {
        String content = Files.readString(path);
        ContextFile ctx = new ContextFile();

        // Parse YAML frontmatter
        if (content.startsWith("---")) {
            int endIdx = content.indexOf("---", 3);
            if (endIdx > 0) {
                String frontmatter = content.substring(3, endIdx).trim();
                parseLibrariesFromYaml(frontmatter, ctx);
                content = content.substring(endIdx + 3).trim();
            }
        }

        // Parse library sections (# LibName vX.X.X)
        Pattern sectionPattern = Pattern.compile("(?m)^# (.+?) v(.+?)$");
        Matcher matcher = sectionPattern.matcher(content);

        int lastEnd = 0;
        String lastLib = null;
        while (matcher.find()) {
            if (lastLib != null) {
                ctx.librarySections.put(lastLib, content.substring(lastEnd, matcher.start()).trim());
            }
            lastLib = matcher.group(1).toLowerCase().replace(" ", "-");
            lastEnd = matcher.start();
        }
        if (lastLib != null) {
            ctx.librarySections.put(lastLib, content.substring(lastEnd).trim());
        }

        return ctx;
    }

    private static void parseLibrariesFromYaml(String yaml, ContextFile ctx) {
        Pattern libPattern = Pattern.compile(
            "- repo: ([^\\n]+)\\n\\s+version: ([^\\n]+)\\n\\s+created: ([^\\n]+)");
        Matcher matcher = libPattern.matcher(yaml);
        while (matcher.find()) {
            try {
                ctx.libraries.add(new LibraryMeta(
                    matcher.group(1).trim(),
                    matcher.group(2).trim(),
                    LocalDateTime.parse(matcher.group(3).trim(), DATE_FORMAT)
                ));
            } catch (Exception e) {
                // Skip malformed entries
            }
        }
    }

    private static String mergeOrCreateContext(Path contextPath, String repo, String version, String newSection)
            throws IOException {

        List<LibraryMeta> libraries = new ArrayList<>();
        Map<String, String> sections = new LinkedHashMap<>();

        // Load existing if present
        if (Files.exists(contextPath)) {
            try {
                ContextFile existing = parseContextFile(contextPath);
                // Keep libraries that are NOT the one we're updating
                for (LibraryMeta lib : existing.libraries) {
                    if (!lib.repo.equals(repo)) {
                        libraries.add(lib);
                    }
                }
                // Keep sections for other libraries
                String repoKey = repo.contains("/") ? repo.split("/")[1].toLowerCase() : repo.toLowerCase();
                for (Map.Entry<String, String> entry : existing.librarySections.entrySet()) {
                    if (!entry.getKey().contains(repoKey)) {
                        sections.put(entry.getKey(), entry.getValue());
                    }
                }
            } catch (Exception e) {
                // Start fresh if parsing fails
            }
        }

        // Add new library
        libraries.add(new LibraryMeta(repo, version, LocalDateTime.now()));
        String repoKey = repo.contains("/") ? repo.split("/")[1].toLowerCase() : repo.toLowerCase();
        sections.put(repoKey, newSection);

        // Generate final content
        StringBuilder sb = new StringBuilder();

        // YAML frontmatter
        sb.append("---\n");
        sb.append("libraries:\n");
        for (LibraryMeta lib : libraries) {
            sb.append("  - repo: ").append(lib.repo).append("\n");
            sb.append("    version: ").append(lib.version).append("\n");
            sb.append("    created: ").append(lib.created.format(DATE_FORMAT)).append("\n");
        }
        sb.append("---\n\n");

        // Warning header
        sb.append("**IMPORTANTE: Este archivo contiene API REAL extraída del código fuente.**\n");
        sb.append("**Solo usa clases y métodos listados aquí. No inventes otros.**\n\n");

        // Library sections
        for (String section : sections.values()) {
            sb.append(section).append("\n\n");
        }

        // Footer
        sb.append("---\n\n");
        sb.append("## PROHIBIDO\n");
        sb.append("- NO usar clases que no estén listadas arriba\n");
        sb.append("- NO inventar métodos que no aparezcan aquí\n");
        sb.append("- Si necesitas algo que no está, busca en el repo primero\n");

        return sb.toString();
    }

    // === GITHUB EXTRACTION (generic, not hardcoded) ===

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

    private static List<String> findExampleFiles(String repo) {
        List<String> examples = new ArrayList<>();

        // Generic paths that work for most Java projects
        String[] basePaths = {
            "src/test/java",
            "src/main/java",
            "test",
            "tests",
            "examples",
            "samples",
            "demo",
            "demos"
        };

        for (String basePath : basePaths) {
            exploreDirectory(repo, basePath, examples, 0);
            if (examples.size() >= 10) break;
        }

        return examples;
    }

    private static void exploreDirectory(String repo, String path, List<String> examples, int depth) {
        if (depth > 3 || examples.size() >= 10) return;

        try {
            var spec = new BatchGitHubSearchTool.SearchSpec("files", repo, path, null, 30);
            String result = GITHUB.execute(List.of(spec));
            JsonNode node = MAPPER.readTree(result);

            if (!node.path("success").asBoolean(true)) {
                return;
            }

            for (JsonNode file : node.path("files")) {
                String name = file.path("name").asText();
                String filePath = file.path("path").asText();
                String type = file.path("type").asText();

                if ("dir".equals(type) && depth < 3) {
                    exploreDirectory(repo, filePath, examples, depth + 1);
                } else if (name.endsWith(".java")) {
                    // Prioritize test/example files
                    if (name.contains("Test") || name.contains("Example") ||
                        name.contains("Demo") || name.contains("Sample") ||
                        name.contains("Main") || name.contains("Basic")) {
                        examples.add(0, filePath);
                    } else {
                        examples.add(filePath);
                    }
                }

                if (examples.size() >= 10) return;
            }
        } catch (Exception e) {
            // Ignore errors, directory may not exist
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

    private static List<String> findMainSourceFiles(String repo) {
        List<String> sources = new ArrayList<>();

        // Try to find main source directory
        try {
            var spec = new BatchGitHubSearchTool.SearchSpec("files", repo, "src/main/java", null, 20);
            String result = GITHUB.execute(List.of(spec));
            JsonNode node = MAPPER.readTree(result);

            for (JsonNode file : node.path("files")) {
                String name = file.path("name").asText();
                String filePath = file.path("path").asText();

                if (name.endsWith(".java") && sources.size() < 10) {
                    sources.add(filePath);
                }
            }
        } catch (Exception e) {
            // Ignore
        }

        return sources;
    }

    // === EXTRACTION HELPERS ===

    private static Set<String> extractImports(String content, String repo) {
        Set<String> imports = new TreeSet<>();

        Pattern pattern = Pattern.compile("import\\s+(?:static\\s+)?([\\w.]+(?:\\.\\*)?);", Pattern.MULTILINE);
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String imp = matcher.group(1);
            if (!imp.startsWith("java.") &&
                !imp.startsWith("javax.") &&
                !imp.startsWith("org.junit") &&
                !imp.startsWith("org.assertj") &&
                !imp.startsWith("org.mockito") &&
                !imp.startsWith("org.hamcrest")) {
                imports.add(imp);
            }
        }

        Pattern pkgPattern = Pattern.compile("package\\s+([\\w.]+);", Pattern.MULTILINE);
        Matcher pkgMatcher = pkgPattern.matcher(content);
        if (pkgMatcher.find()) {
            String pkg = pkgMatcher.group(1);
            if (!pkg.startsWith("java.") && !pkg.startsWith("javax.")) {
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
        "Arrays", "Collections", "List", "Map", "Set", "Queue", "Deque", "Iterator",
        "Pattern", "Matcher", "Scanner", "Random", "Math", "System", "Runtime",
        "Date", "Calendar", "LocalDate", "LocalTime", "LocalDateTime", "Instant",
        "Optional", "Stream", "Collectors", "Function", "Consumer", "Supplier", "Predicate"
    );

    private static Set<String> extractClassUsage(String content) {
        Set<String> classes = new TreeSet<>();

        Pattern newPattern = Pattern.compile("new\\s+([A-Z][\\w]+)\\s*[(<]");
        Matcher matcher = newPattern.matcher(content);
        while (matcher.find()) {
            String cls = matcher.group(1);
            if (!JAVA_STANDARD_CLASSES.contains(cls)) {
                classes.add(cls);
            }
        }

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

    private static void extractMethodCalls(String content, Map<String, List<String>> classMethods) {
        Pattern pattern = Pattern.compile("([a-zA-Z][\\w]*)\\.([a-zA-Z][\\w]*)\\s*\\(");
        Matcher matcher = pattern.matcher(content);

        while (matcher.find()) {
            String varOrClass = matcher.group(1);
            String method = matcher.group(2);

            if (!varOrClass.equals("System") && !varOrClass.equals("String") &&
                !varOrClass.equals("Arrays") && !varOrClass.equals("Math") &&
                !method.equals("toString") && !method.equals("equals") &&
                !method.equals("hashCode") && !method.equals("getClass")) {

                classMethods.computeIfAbsent(varOrClass, k -> new ArrayList<>());
                if (!classMethods.get(varOrClass).contains(method)) {
                    classMethods.get(varOrClass).add(method);
                }
            }
        }
    }

    private static void extractPublicMethods(String content, Map<String, List<String>> classMethods) {
        Pattern classPattern = Pattern.compile("public\\s+(?:abstract\\s+)?class\\s+(\\w+)");
        Matcher classMatcher = classPattern.matcher(content);
        String className = null;
        if (classMatcher.find()) {
            className = classMatcher.group(1);
        }
        if (className == null) return;

        Pattern methodPattern = Pattern.compile(
            "public\\s+(?:static\\s+)?(?:final\\s+)?(?:<[^>]+>\\s+)?(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*\\([^)]*\\)");
        Matcher methodMatcher = methodPattern.matcher(content);

        List<String> methods = classMethods.computeIfAbsent(className, k -> new ArrayList<>());
        while (methodMatcher.find()) {
            String returnType = methodMatcher.group(1);
            String methodName = methodMatcher.group(2);
            String signature = methodName + "() : " + returnType;
            if (!methods.contains(signature) && !methodName.equals(className)) {
                methods.add(signature);
            }
        }
    }

    private static String extractUsefulExample(String content) {
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
        Pattern titlePattern = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = titlePattern.matcher(readme);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return repo.contains("/") ? repo.split("/")[1] : repo;
    }

    // === OUTPUT GENERATION ===

    private static String generateLibrarySection(
            String libraryName,
            String version,
            String repo,
            Set<String> imports,
            Set<String> classes,
            Map<String, List<String>> classMethods,
            List<String> examples) {

        StringBuilder sb = new StringBuilder();

        sb.append("# ").append(libraryName).append(" v").append(version).append("\n\n");
        sb.append("Fuente: https://github.com/").append(repo).append("\n\n");

        // Imports section
        sb.append("## Imports Verificados\n");
        sb.append("```java\n");
        for (String imp : imports) {
            sb.append("import ").append(imp).append(";\n");
        }
        sb.append("```\n\n");

        // Classes and their methods section
        sb.append("## Clases y Métodos Reales\n\n");
        for (String cls : classes) {
            sb.append("### `").append(cls).append("`\n");
            List<String> methods = classMethods.get(cls);
            if (methods != null && !methods.isEmpty()) {
                for (String method : methods) {
                    sb.append("- `").append(method).append("`\n");
                }
            }
            sb.append("\n");
        }

        // Examples section
        if (!examples.isEmpty()) {
            sb.append("## Ejemplos de Uso (copiados del repo)\n");
            for (String example : examples) {
                sb.append("```java\n");
                sb.append(example).append("\n");
                sb.append("```\n\n");
            }
        }

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
