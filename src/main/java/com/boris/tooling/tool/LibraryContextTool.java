package com.boris.tooling.tool;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.tooling.ToolDefinition;
import com.boris.tooling.ToolProgressNotifier;

/**
 * Tool that creates/updates .boris/CONTEXT.md with REAL API documentation
 * by downloading and analyzing GitHub repository source code locally.
 *
 * OPTIMIZATIONS:
 * - Downloads ZIP once instead of multiple API calls
 * - Analyzes code locally (no rate limits)
 * - Caches downloaded repos in .boris/{repo}/
 * - Smart cache: skips download if context exists and is fresh
 */
public class LibraryContextTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
    private static final int CACHE_DAYS = 15;

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
            "Library version or branch (e.g., 3.1.5 or main)");
        var projectPathProp = Map.of("type", "string", "description",
            "Path to project where .boris/CONTEXT.md will be created");
        var forceProp = Map.of("type", "boolean", "description",
            "Force re-download and recreation even if context exists (default: false)");

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
            "Downloads GitHub repo as ZIP, analyzes source code locally, and creates .boris/CONTEXT.md. " +
            "No API rate limits. Supports multiple libraries. Use force=true to re-download.",
            schema);
    }

    public static String execute(Map<String, Object> args) {
        String repo = (String) args.getOrDefault("repo", "");
        String version = (String) args.getOrDefault("version", "main");
        String projectPath = (String) args.getOrDefault("project_path", ".");
        boolean force = Boolean.TRUE.equals(args.get("force"));

        if (repo == null || repo.isBlank() || !repo.contains("/")) {
            return formatOutput(false, "Error: repo is required (format: owner/repo)");
        }

        Path borisDir = Paths.get(projectPath, ".boris");
        Path contextPath = borisDir.resolve("CONTEXT.md");
        String repoName = repo.split("/")[1];
        Path repoDir = borisDir.resolve("repos").resolve(repoName);

        // === CACHE CHECK ===
        if (!force && Files.exists(contextPath)) {
            try {
                ContextFile existing = parseContextFile(contextPath);
                if (existing.hasLibrary(repo, version)) {
                    ToolProgressNotifier.notify("create_library_context",
                        "Cache hit: " + repo + " v" + version);
                    return formatOutput(true,
                        "CONTEXT.md ya contiene " + repo + " v" + version +
                        " (cache válido por " + CACHE_DAYS + " días). " +
                        "NO busques en web, usa el CONTEXT.md existente. Usa force=true para recrear.");
                }
            } catch (Exception e) {
                // If parsing fails, continue to recreate
            }
        }

        try {
            // === ENSURE .boris IS IN .gitignore ===
            ensureGitignore(Paths.get(projectPath));

            // === CHECK IF REPO ALREADY DOWNLOADED ===
            boolean needsDownload = force || !Files.exists(repoDir);

            // Si el repo ya existe y hay CONTEXT.md reciente, no re-descargar
            if (!needsDownload && Files.exists(contextPath)) {
                try {
                    ContextFile existing = parseContextFile(contextPath);
                    boolean hasRecent = existing.libraries.stream()
                        .anyMatch(lib -> lib.repo.equals(repo) &&
                                 lib.created.isAfter(LocalDateTime.now().minusDays(CACHE_DAYS)));
                    if (hasRecent) {
                        // Repo ya descargado y contexto reciente - solo re-analizar si es necesario
                        ToolProgressNotifier.notify("create_library_context",
                            "Repo ya descargado, usando cache local");
                        needsDownload = false;
                    }
                } catch (Exception e) {
                    // Si no puede parsear, re-descargar
                    needsDownload = true;
                }
            }

            // === DOWNLOAD ZIP IF NEEDED ===
            if (needsDownload) {
                ToolProgressNotifier.notify("create_library_context",
                    "Descargando " + repo + "...");
                downloadAndExtractZip(repo, version, repoDir);
            } else {
                ToolProgressNotifier.notify("create_library_context",
                    "Usando repo descargado previamente");
            }

            // === ANALYZE LOCAL CODE ===
            ToolProgressNotifier.notify("create_library_context",
                "Analizando código fuente...");

            Set<String> realImports = new TreeSet<>();
            Set<String> realClasses = new TreeSet<>();
            Map<String, List<String>> classMethods = new LinkedHashMap<>();
            List<String> codeExamples = new ArrayList<>();
            String libraryName = repoName;

            // Find and analyze Java files
            List<Path> javaFiles = findJavaFiles(repoDir);
            ToolProgressNotifier.notify("create_library_context",
                "Encontrados " + javaFiles.size() + " archivos Java");

            for (Path javaFile : javaFiles) {
                try {
                    String content = Files.readString(javaFile);
                    String fileName = javaFile.getFileName().toString();

                    // Extract imports (excluding standard Java)
                    realImports.addAll(extractImports(content));

                    // Extract class usage
                    realClasses.addAll(extractClassUsage(content));

                    // Extract method calls
                    extractMethodCalls(content, classMethods);

                    // Extract public methods from source files
                    extractPublicMethods(content, classMethods);

                    // Get examples from test/example files
                    if (codeExamples.size() < 3 &&
                        (fileName.contains("Test") || fileName.contains("Example") ||
                         fileName.contains("Demo") || fileName.contains("Sample"))) {
                        String example = extractUsefulExample(content);
                        if (example != null && example.length() > 50) {
                            codeExamples.add(example);
                        }
                    }
                } catch (IOException e) {
                    // Skip unreadable files
                }
            }

            // Try to get library name from README
            Path readmePath = findReadme(repoDir);
            if (readmePath != null) {
                try {
                    String readmeContent = Files.readString(readmePath);
                    String extractedName = extractLibraryName(readmeContent);
                    if (extractedName != null && !extractedName.isBlank()) {
                        libraryName = extractedName;
                    }
                } catch (IOException e) {
                    // Use default name
                }
            }

            // === GENERATE CONTEXT ===
            String librarySection = generateLibrarySection(
                libraryName, version, repo, realImports, realClasses, classMethods, codeExamples);

            String finalContent = mergeOrCreateContext(contextPath, repo, version, librarySection);

            Files.createDirectories(contextPath.getParent());
            Files.writeString(contextPath, finalContent);

            ToolProgressNotifier.notify("create_library_context",
                "Contexto creado: " + realImports.size() + " imports, " +
                realClasses.size() + " clases, " + javaFiles.size() + " archivos analizados");

            return formatOutput(true,
                "CONTEXT.md actualizado con " + repo + " v" + version + ": " +
                realImports.size() + " imports, " + realClasses.size() + " clases " +
                "(de " + javaFiles.size() + " archivos)");

        } catch (Exception e) {
            // Fallback: if download fails but context exists, use it
            if (Files.exists(contextPath)) {
                return formatOutput(true,
                    "Error descargando " + repo + ": " + e.getMessage() +
                    ". Usando CONTEXT.md existente.");
            }
            return formatOutput(false, "Error: " + e.getMessage());
        }
    }

    // === ZIP DOWNLOAD & EXTRACTION ===

    private static void downloadAndExtractZip(String repo, String branch, Path targetDir) throws IOException {
        // Clean target directory if exists
        if (Files.exists(targetDir)) {
            deleteDirectory(targetDir);
        }
        Files.createDirectories(targetDir);

        // Download ZIP from GitHub
        String zipUrl = "https://github.com/" + repo + "/archive/refs/heads/" + branch + ".zip";

        // Try branch first, then try as tag
        byte[] zipData;
        try {
            zipData = downloadUrl(zipUrl);
        } catch (IOException e) {
            // Try as tag
            zipUrl = "https://github.com/" + repo + "/archive/refs/tags/" + branch + ".zip";
            try {
                zipData = downloadUrl(zipUrl);
            } catch (IOException e2) {
                // Try as tag with 'v' prefix
                zipUrl = "https://github.com/" + repo + "/archive/refs/tags/v" + branch + ".zip";
                zipData = downloadUrl(zipUrl);
            }
        }

        // Extract ZIP
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipData))) {
            ZipEntry entry;
            String rootFolder = null;

            while ((entry = zis.getNextEntry()) != null) {
                String name = entry.getName();

                // Get root folder name (e.g., "lanterna-main/")
                if (rootFolder == null && name.contains("/")) {
                    rootFolder = name.substring(0, name.indexOf('/') + 1);
                }

                // Remove root folder from path
                if (rootFolder != null && name.startsWith(rootFolder)) {
                    name = name.substring(rootFolder.length());
                }

                if (name.isEmpty()) continue;

                Path outPath = targetDir.resolve(name);

                if (entry.isDirectory()) {
                    Files.createDirectories(outPath);
                } else {
                    Files.createDirectories(outPath.getParent());
                    Files.copy(zis, outPath, StandardCopyOption.REPLACE_EXISTING);
                }

                zis.closeEntry();
            }
        }
    }

    private static byte[] downloadUrl(String urlStr) throws IOException {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setFollowRedirects(true);
        conn.setConnectTimeout(30000);
        conn.setReadTimeout(60000);
        conn.setRequestProperty("User-Agent", "Boris-AI/1.0");

        int status = conn.getResponseCode();
        if (status == HttpURLConnection.HTTP_MOVED_PERM ||
            status == HttpURLConnection.HTTP_MOVED_TEMP ||
            status == 307 || status == 308) {
            String newUrl = conn.getHeaderField("Location");
            conn.disconnect();
            return downloadUrl(newUrl);
        }

        if (status != HttpURLConnection.HTTP_OK) {
            conn.disconnect();
            throw new IOException("HTTP " + status + " for " + urlStr);
        }

        try (InputStream is = conn.getInputStream();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        } finally {
            conn.disconnect();
        }
    }

    private static void deleteDirectory(Path dir) throws IOException {
        if (Files.exists(dir)) {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try { Files.delete(path); } catch (IOException e) { }
                });
        }
    }

    // === LOCAL FILE ANALYSIS ===

    private static List<Path> findJavaFiles(Path rootDir) throws IOException {
        if (!Files.exists(rootDir)) {
            return List.of();
        }
        return Files.walk(rootDir)
            .filter(p -> p.toString().endsWith(".java"))
            .filter(p -> !p.toString().contains("/test/") ||
                        p.getFileName().toString().contains("Example") ||
                        p.getFileName().toString().contains("Demo"))
            .limit(200) // Limit to avoid huge repos
            .collect(Collectors.toList());
    }

    private static Path findReadme(Path rootDir) {
        String[] readmeNames = {"README.md", "readme.md", "README.MD", "Readme.md", "README"};
        for (String name : readmeNames) {
            Path readme = rootDir.resolve(name);
            if (Files.exists(readme)) {
                return readme;
            }
        }
        return null;
    }

    // === GITIGNORE ===

    private static void ensureGitignore(Path projectRoot) {
        Path gitignore = projectRoot.resolve(".gitignore");
        try {
            String content = "";
            if (Files.exists(gitignore)) {
                content = Files.readString(gitignore);
            }

            if (!content.contains(".boris/")) {
                String newContent = content;
                if (!content.endsWith("\n") && !content.isEmpty()) {
                    newContent += "\n";
                }
                newContent += "\n# Boris AI context and downloaded repos\n.boris/\n";
                Files.writeString(gitignore, newContent);
            }
        } catch (IOException e) {
            // Ignore gitignore errors
        }
    }

    // === PARSING & MERGING ===

    private static ContextFile parseContextFile(Path path) throws IOException {
        String content = Files.readString(path);
        ContextFile ctx = new ContextFile();

        if (content.startsWith("---")) {
            int endIdx = content.indexOf("---", 3);
            if (endIdx > 0) {
                String frontmatter = content.substring(3, endIdx).trim();
                parseLibrariesFromYaml(frontmatter, ctx);
                content = content.substring(endIdx + 3).trim();
            }
        }

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

        if (Files.exists(contextPath)) {
            try {
                ContextFile existing = parseContextFile(contextPath);
                for (LibraryMeta lib : existing.libraries) {
                    if (!lib.repo.equals(repo)) {
                        libraries.add(lib);
                    }
                }
                String repoKey = repo.split("/")[1].toLowerCase();
                for (Map.Entry<String, String> entry : existing.librarySections.entrySet()) {
                    if (!entry.getKey().contains(repoKey)) {
                        sections.put(entry.getKey(), entry.getValue());
                    }
                }
            } catch (Exception e) {
                // Start fresh
            }
        }

        libraries.add(new LibraryMeta(repo, version, LocalDateTime.now()));
        String repoKey = repo.split("/")[1].toLowerCase();
        sections.put(repoKey, newSection);

        StringBuilder sb = new StringBuilder();

        sb.append("---\n");
        sb.append("libraries:\n");
        for (LibraryMeta lib : libraries) {
            sb.append("  - repo: ").append(lib.repo).append("\n");
            sb.append("    version: ").append(lib.version).append("\n");
            sb.append("    created: ").append(lib.created.format(DATE_FORMAT)).append("\n");
        }
        sb.append("---\n\n");

        sb.append("**IMPORTANTE: Este archivo contiene API REAL extraída del código fuente.**\n");
        sb.append("**Solo usa clases y métodos listados aquí. No inventes otros.**\n\n");

        for (String section : sections.values()) {
            sb.append(section).append("\n\n");
        }

        sb.append("---\n\n");
        sb.append("## PROHIBIDO\n");
        sb.append("- NO usar clases que no estén listadas arriba\n");
        sb.append("- NO inventar métodos que no aparezcan aquí\n");
        sb.append("- Si necesitas algo que no está, usa force=true para re-analizar\n");

        return sb.toString();
    }

    // === EXTRACTION HELPERS ===

    private static Set<String> extractImports(String content) {
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
        Pattern classPattern = Pattern.compile("public\\s+(?:abstract\\s+)?(?:class|interface)\\s+(\\w+)");
        Matcher classMatcher = classPattern.matcher(content);
        String className = null;
        if (classMatcher.find()) {
            className = classMatcher.group(1);
        }
        if (className == null) return;

        Pattern methodPattern = Pattern.compile(
            "public\\s+(?:static\\s+)?(?:final\\s+)?(?:abstract\\s+)?(?:<[^>]+>\\s+)?(\\w+(?:<[^>]+>)?)\\s+(\\w+)\\s*\\([^)]*\\)");
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
            if (main.length() < 1500) {
                return main;
            }
        }

        Pattern testPattern = Pattern.compile(
            "(@Test[^{]*\\{[^}]+\\})",
            Pattern.DOTALL);
        matcher = testPattern.matcher(content);
        if (matcher.find()) {
            String test = matcher.group(1);
            if (test.length() < 1000) {
                return test;
            }
        }

        return null;
    }

    private static String extractLibraryName(String readme) {
        Pattern titlePattern = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
        Matcher matcher = titlePattern.matcher(readme);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    // === OUTPUT ===

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

        sb.append("## Imports Verificados\n");
        sb.append("```java\n");
        int importCount = 0;
        for (String imp : imports) {
            sb.append("import ").append(imp).append(";\n");
            if (++importCount >= 50) {
                sb.append("// ... y ").append(imports.size() - 50).append(" más\n");
                break;
            }
        }
        sb.append("```\n\n");

        sb.append("## Clases y Métodos Reales\n\n");
        int classCount = 0;
        for (String cls : classes) {
            if (++classCount > 30) {
                sb.append("*... y ").append(classes.size() - 30).append(" clases más*\n\n");
                break;
            }
            sb.append("### `").append(cls).append("`\n");
            List<String> methods = classMethods.get(cls);
            if (methods != null && !methods.isEmpty()) {
                int methodCount = 0;
                for (String method : methods) {
                    sb.append("- `").append(method).append("`\n");
                    if (++methodCount >= 15) {
                        sb.append("- *... y ").append(methods.size() - 15).append(" métodos más*\n");
                        break;
                    }
                }
            }
            sb.append("\n");
        }

        if (!examples.isEmpty()) {
            sb.append("## Ejemplos de Uso\n");
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
