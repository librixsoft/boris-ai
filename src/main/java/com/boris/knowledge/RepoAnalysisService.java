package com.boris.knowledge;

import com.boris.context.CodeSymbol;
import com.boris.context.ContentRepository;
import com.boris.context.SymbolRepository;
import com.boris.tooling.ToolProgressNotifier;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public final class RepoAnalysisService {

    public record RepoAnalysis(
        String repoName,
        String buildSystem,
        String mainLanguage,
        List<String> dependencies,
        List<String> entryPoints,
        List<String> exampleFiles,
        List<String> relations,
        String markdown
    ) {}

    record ExampleFile(String path, String kind, int weight) {}

    private static final int MAX_FILES = 500;
    private static final int MAX_DEPENDENCIES = 25;
    private static final int MAX_RELATIONS = 15;
    private static final int MAX_EXAMPLE_FILES = 10;
    private static final int MAX_README_SNIPPETS = 3;
    private static final int MAX_SNIPPET_LINES = 40;
    private static final long MAX_README_BYTES = 200_000;

    private static final Pattern POM_DEPENDENCY_PATTERN = Pattern.compile(
        "(?s)<dependency>.*?<groupId>([^<]+)</groupId>.*?<artifactId>([^<]+)</artifactId>.*?</dependency>");
    private static final Pattern NPM_DEPENDENCY_PATTERN = Pattern.compile(
        "\"([A-Za-z@/._-]{2,64})\"\\s*:\\s*\"[^\"]*\"");
    private static final Pattern GRADLE_DEPENDENCY_PATTERN = Pattern.compile(
        "(?m)^\\s*(?:implementation|api|compileOnly|runtimeOnly)\\s+[\"']([^\"']{3,120})[\"']");
    private static final Pattern TEST_METHOD_PATTERN = Pattern.compile("@Test\\b");

    private RepoAnalysisService() {
    }

    public static RepoAnalysis analyzeAndPersist(String repoName, Path repoDir, Path projectRoot) {
        if (repoName == null || repoName.isBlank()) {
            throw new RepoAnalysisException("repoName es requerido para el análisis del repo");
        }
        if (repoDir == null || !Files.isDirectory(repoDir)) {
            throw new RepoAnalysisException("El cache del repo no existe en disco: " + repoDir);
        }
        if (projectRoot == null) {
            throw new RepoAnalysisException("projectRoot es requerido para el análisis del repo");
        }

        ToolProgressNotifier.notify("repo_analysis", "Analizando estructura de " + repoName + "...");

        List<Path> tree = collectTree(repoDir);
        if (tree.isEmpty()) {
            throw new RepoAnalysisException("El repo " + repoName + " no contiene archivos para analizar");
        }

        String buildSystem = detectBuildSystem(tree);
        String mainLanguage = detectMainLanguage(tree);
        List<String> dependencies = extractDependencies(tree, buildSystem);
        List<String> entryPoints = detectEntryPoints(repoName);
        List<ExampleFile> examples = detectExampleFiles(repoDir);
        List<String> relations = extractRelations(repoName);
        List<String> readmeSnippets = extractReadmeSnippets(repoDir);

        String markdown = renderMarkdown(repoName, buildSystem, mainLanguage, dependencies,
            entryPoints, examples, relations, readmeSnippets);

        KnowledgeStorageService.saveRepoAnalysis(projectRoot, repoName, markdown);
        indexAnalysis(repoName, markdown);

        return new RepoAnalysis(repoName, buildSystem, mainLanguage, dependencies,
            entryPoints, examplePaths(examples), relations, markdown);
    }

    private static List<Path> collectTree(Path repoDir) {
        try (var stream = Files.walk(repoDir)) {
            return stream
                .filter(Files::isRegularFile)
                .limit(MAX_FILES)
                .toList();
        } catch (IOException e) {
            throw new RepoAnalysisException("No se pudo recorrer el repo " + repoDir + ": " + e.getMessage(), e);
        }
    }

    private static String detectBuildSystem(List<Path> tree) {
        List<String> names = tree.stream().map(p -> p.getFileName().toString()).toList();
        if (names.contains("pom.xml")) {
            return "maven";
        }
        for (String name : names) {
            if (name.equals("build.gradle") || name.equals("build.gradle.kts")) {
                return "gradle";
            }
        }
        if (names.contains("package.json")) {
            return "npm";
        }
        return "no-detectado";
    }

    private static String detectMainLanguage(List<Path> tree) {
        Map<String, Long> byExt = tree.stream()
            .map(p -> extensionOf(p.getFileName().toString()))
            .filter(ext -> !ext.isBlank())
            .collect(Collectors.groupingBy(ext -> ext, Collectors.counting()));

        if (byExt.getOrDefault("java", 0L) > 0) {
            return "java";
        }
        if (byExt.getOrDefault("kt", 0L) > 0) {
            return "kotlin";
        }
        if (byExt.getOrDefault("ts", 0L) + byExt.getOrDefault("tsx", 0L) > 0) {
            return "typescript";
        }
        if (byExt.getOrDefault("js", 0L) + byExt.getOrDefault("jsx", 0L) + byExt.getOrDefault("mjs", 0L) > 0) {
            return "javascript";
        }
        return "no-detectado";
    }

    private static List<String> extractDependencies(List<Path> tree, String buildSystem) {
        List<String> dependencies = new ArrayList<>();
        for (Path file : tree) {
            if (dependencies.size() >= MAX_DEPENDENCIES) {
                break;
            }
            String name = file.getFileName().toString();
            if (name.equals("pom.xml")) {
                dependencies.addAll(pomDependencies(file));
            } else if (name.equals("package.json")) {
                dependencies.addAll(npmDependencies(file));
            } else if (name.equals("build.gradle") || name.equals("build.gradle.kts")) {
                dependencies.addAll(gradleDependencies(file));
            }
        }
        if (dependencies.size() > MAX_DEPENDENCIES) {
            dependencies = dependencies.subList(0, MAX_DEPENDENCIES);
        }
        return dependencies;
    }

    private static List<String> pomDependencies(Path pomFile) {
        String content = readFile(pomFile);
        List<String> deps = new ArrayList<>();
        Matcher matcher = POM_DEPENDENCY_PATTERN.matcher(content);
        while (matcher.find() && deps.size() < MAX_DEPENDENCIES) {
            deps.add(matcher.group(1).strip() + ":" + matcher.group(2).strip());
        }
        return deps;
    }

    private static List<String> npmDependencies(Path packageJson) {
        String content = readFile(packageJson);
        List<String> deps = new ArrayList<>();
        Matcher matcher = NPM_DEPENDENCY_PATTERN.matcher(content);
        while (matcher.find() && deps.size() < MAX_DEPENDENCIES) {
            String name = matcher.group(1);
            if (!name.contains("$") && !name.isBlank()) {
                deps.add(name);
            }
        }
        return deps;
    }

    private static List<String> gradleDependencies(Path gradleFile) {
        String content = readFile(gradleFile);
        List<String> deps = new ArrayList<>();
        Matcher matcher = GRADLE_DEPENDENCY_PATTERN.matcher(content);
        while (matcher.find() && deps.size() < MAX_DEPENDENCIES) {
            deps.add(matcher.group(1).strip());
        }
        return deps;
    }

    private static String readFile(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RepoAnalysisException("No se pudo leer " + file + " para dependencias: " + e.getMessage(), e);
        }
    }

    private static List<String> detectEntryPoints(String repoName) {
        List<String> entryPoints = new ArrayList<>();
        entryPoints.addAll(SymbolRepository.search("main", CodeSymbol.SymbolType.METHOD, repoName)
            .stream()
            .map(RepoAnalysisService::symbolEntry)
            .filter(s -> !s.isBlank())
            .limit(5)
            .toList());
        entryPoints.addAll(SymbolRepository.findAllClasses(repoName)
            .stream()
            .filter(RepoAnalysisService::isEntryClass)
            .map(RepoAnalysisService::symbolEntry)
            .limit(10)
            .toList());
        return entryPoints;
    }

    private static boolean isEntryClass(CodeSymbol symbol) {
        String name = symbol.getName().toLowerCase(Locale.ROOT);
        return name.endsWith("controller") || name.endsWith("application") || name.endsWith("app") || name.endsWith("launcher");
    }

    private static String symbolEntry(CodeSymbol symbol) {
        return symbol.getType().name().toLowerCase(Locale.ROOT) + " " + symbol.getFile();
    }

    private static List<ExampleFile> detectExampleFiles(Path repoDir) {
        List<ExampleFile> examples = new ArrayList<>();
        try (var stream = Files.walk(repoDir)) {
            stream
                .filter(Files::isRegularFile)
                .forEach(p -> {
                    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    String relPath = repoDir.relativize(p).toString();
                    if (name.contains("example") || name.contains("demo") || name.contains("sample")) {
                        examples.add(new ExampleFile(relPath, "example", 100));
                        return;
                    }
                    if (name.endsWith(".java") && relPath.replace('\\', '/').contains("/test/")) {
                        int score = countTestMethods(p);
                        if (score > 0) {
                            examples.add(new ExampleFile(relPath, "test", score));
                        }
                    }
                });
        } catch (IOException e) {
            throw new RepoAnalysisException("No se pudo recorrer el repo en busca de ejemplos: " + e.getMessage(), e);
        }
        return examples.stream()
            .sorted((a, b) -> Integer.compare(b.weight(), a.weight()))
            .limit(MAX_EXAMPLE_FILES)
            .toList();
    }

    private static int countTestMethods(Path javaFile) {
        try {
            String content = Files.readString(javaFile, StandardCharsets.UTF_8);
            return (int) TEST_METHOD_PATTERN.matcher(content).results().count();
        } catch (IOException e) {
            throw new RepoAnalysisException("No se pudo leer el test " + javaFile + ": " + e.getMessage(), e);
        }
    }

    private static List<String> extractRelations(String repoName) {
        List<String> relations = new ArrayList<>();
        for (CodeSymbol symbol : SymbolRepository.findAllClasses(repoName)) {
            String signature = symbol.getSignature();
            if (signature == null) {
                continue;
            }
            if (signature.contains("extends") || signature.contains("implements")) {
                String parent = symbol.getParent() != null ? symbol.getParent() : symbol.getName();
                relations.add(parent + " (" + signature + ")");
            }
            if (relations.size() >= MAX_RELATIONS) {
                break;
            }
        }
        return relations;
    }

    private static List<String> extractReadmeSnippets(Path repoDir) {
        List<Path> readmes = new ArrayList<>();
        try (var stream = Files.list(repoDir)) {
            stream
                .filter(Files::isRegularFile)
                .forEach(p -> {
                    String name = p.getFileName().toString().toLowerCase(Locale.ROOT);
                    if (name.startsWith("readme") && (name.endsWith(".md") || name.endsWith(".markdown") || name.endsWith(".txt"))) {
                        readmes.add(p);
                    }
                });
        } catch (IOException e) {
            throw new RepoAnalysisException("No se pudo listar la raiz del repo para README: " + e.getMessage(), e);
        }
        List<String> snippets = new ArrayList<>();
        for (Path readme : readmes) {
            if (snippets.size() >= MAX_README_SNIPPETS) {
                break;
            }
            try {
                if (Files.size(readme) > MAX_README_BYTES) {
                    throw new RepoAnalysisException("README demasiado grande para análisis: " + readme);
                }
                String content = Files.readString(readme, StandardCharsets.UTF_8);
                snippets.addAll(GenericKnowledgeExtractor.extractFencedSnippets(content, "md"));
            } catch (IOException e) {
                throw new RepoAnalysisException("No se pudo leer el README " + readme + ": " + e.getMessage(), e);
            }
        }
        return snippets.stream()
            .map(RepoAnalysisService::truncateSnippet)
            .limit(MAX_README_SNIPPETS)
            .collect(Collectors.toList());
    }

    private static String truncateSnippet(String snippet) {
        return String.join("\n", snippet.lines().limit(MAX_SNIPPET_LINES).toList());
    }

    private static String renderMarkdown(String repoName, String buildSystem, String mainLanguage,
                                         List<String> dependencies, List<String> entryPoints,
                                         List<ExampleFile> examples, List<String> relations,
                                         List<String> readmeSnippets) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Análisis de Repositorio: ").append(repoName).append("\n\n");

        sb.append("## Stack y Estructura\n");
        sb.append("- Build system: ").append(buildSystem).append("\n");
        sb.append("- Lenguaje principal: ").append(mainLanguage).append("\n");
        if (dependencies.isEmpty()) {
            sb.append("- Dependencias clave: no-detectado\n");
        } else {
            sb.append("- Dependencias clave:\n");
            for (String dep : dependencies) {
                sb.append("  - ").append(dep).append("\n");
            }
        }
        sb.append("\n");

        sb.append("## Arquitectura\n");
        Map<String, Integer> topClasses = SymbolRepository.methodCountByClass(repoName);
        if (topClasses.isEmpty()) {
            sb.append("- Clases principales: no indexadas (repo sin código Java)\n");
        } else {
            sb.append("- Clases principales (por métodos públicos):\n");
            topClasses.forEach((cls, count) -> sb.append("  - ").append(cls).append(" (").append(count).append(" métodos)\n"));
        }
        if (relations.isEmpty()) {
            sb.append("- Jerarquía: no-detectado\n");
        } else {
            sb.append("- Jerarquía de herencia:\n");
            for (String rel : relations) {
                sb.append("  - ").append(rel).append("\n");
            }
        }
        if (entryPoints.isEmpty()) {
            sb.append("- Entry points: no-detectado\n");
        } else {
            sb.append("- Entry points:\n");
            entryPoints.forEach(ep -> sb.append("  - ").append(ep).append("\n"));
        }
        sb.append("\n");

        sb.append("## Patrones de Uso Detectados\n");
        if (examples.isEmpty()) {
            sb.append("- Archivos de ejemplo: no-detectado\n");
        } else {
            sb.append("- Archivos de ejemplo:\n");
            for (ExampleFile ef : examples) {
                sb.append("  - ").append(ef.path()).append(" (").append(ef.kind()).append(")\n");
            }
        }
        if (readmeSnippets.isEmpty()) {
            sb.append("- Snippets del README: no-detectado\n");
        } else {
            sb.append("- Snippets del README:\n");
            int i = 1;
            for (String snippet : readmeSnippets) {
                sb.append("  - Snippet ").append(i++).append(":\n```java\n").append(snippet).append("\n```\n");
            }
        }
        sb.append("\n");

        return sb.toString();
    }

    private static void indexAnalysis(String repoName, String markdown) {
        if (ContentRepository.repoPath(repoName) != null) {
            ContentRepository.saveBatch(List.of(new ContentRepository.DocChunk(
                repoName, "_repo_analysis.md", 0, "Análisis de Repositorio", markdown, "analysis")));
        } else {
            throw new RepoAnalysisException("El repo " + repoName + " no tiene ruta de cache registrada; el análisis no se puede indexar");
        }
    }

    private static List<String> examplePaths(List<ExampleFile> examples) {
        return examples.stream().map(ExampleFile::path).collect(Collectors.toList());
    }

    private static String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
