package com.boris.knowledge;

import com.boris.context.ProjectContext;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;

/**
 * Manages persistent local markdown knowledge cache under $PROJECT_ROOT/.boris/<framework>/<CLASE>.md.
 */
public class KnowledgeStorageService {

    public static Path resolveProjectRoot(String projectPath) {
        if (projectPath != null && !projectPath.isBlank()) {
            return Paths.get(projectPath).toAbsolutePath().normalize();
        }
        String ctxPath = ProjectContext.getProjectPath();
        if (ctxPath != null && !ctxPath.isBlank()) {
            return Paths.get(ctxPath).toAbsolutePath().normalize();
        }
        return Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
    }

    public static Path getFrameworkDir(Path projectRoot, String framework) {
        String cleanFw = cleanFrameworkName(framework);
        return projectRoot.resolve(".boris").resolve(cleanFw);
    }

    public static Path getKnowledgeFilePath(Path projectRoot, String framework, String className) {
        Path frameworkDir = getFrameworkDir(projectRoot, framework);
        String safeClass = sanitizeName(className) + ".md";
        return frameworkDir.resolve(safeClass);
    }

    public static Path findExistingKnowledgeFile(Path projectRoot, String framework, String className) {
        if (framework == null || className == null || className.isBlank()) {
            return null;
        }
        String safeClass = sanitizeName(className) + ".md";

        // 1. Direct path in .boris/<cleanFramework>/<Class>.md
        Path direct = getKnowledgeFilePath(projectRoot, framework, className);
        if (Files.isRegularFile(direct)) {
            return direct;
        }

        // 2. Direct path with raw framework string
        Path raw = projectRoot.resolve(".boris").resolve(sanitizeName(framework)).resolve(safeClass);
        if (Files.isRegularFile(raw)) {
            return raw;
        }

        // 3. Search directories in .boris/ matching the framework alias
        Path borisDir = projectRoot.resolve(".boris");
        if (Files.isDirectory(borisDir)) {
            try (Stream<Path> stream = Files.list(borisDir)) {
                List<Path> dirs = stream
                    .filter(Files::isDirectory)
                    .filter(p -> matchesFramework(p.getFileName().toString(), framework))
                    .toList();

                for (Path dir : dirs) {
                    Path file = dir.resolve(safeClass);
                    if (Files.isRegularFile(file)) {
                        return file;
                    }
                }
            } catch (Exception ignored) {}
        }
        return null;
    }

    public static boolean hasKnowledge(Path projectRoot, String framework, String className) {
        return findExistingKnowledgeFile(projectRoot, framework, className) != null;
    }

    public static String readKnowledge(Path projectRoot, String framework, String className) {
        Path file = findExistingKnowledgeFile(projectRoot, framework, className);
        if (file == null || !Files.exists(file)) {
            return null;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public static void saveKnowledge(Path projectRoot, String framework, String className, String content) throws IOException {
        String cleanFw = cleanFrameworkName(framework);
        Path frameworkDir = projectRoot.resolve(".boris").resolve(cleanFw);
        Files.createDirectories(frameworkDir);
        ensureGitignore(projectRoot);

        Path file = frameworkDir.resolve(sanitizeName(className) + ".md");
        Files.writeString(file, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        // Si el framework original tenía guiones (ej. tambo-tui vs tamboui), guardar también en el alias
        String rawSanitized = sanitizeName(framework);
        if (!rawSanitized.equals(cleanFw) && !rawSanitized.contains("/")) {
            try {
                Path aliasDir = projectRoot.resolve(".boris").resolve(rawSanitized);
                Files.createDirectories(aliasDir);
                Path aliasFile = aliasDir.resolve(sanitizeName(className) + ".md");
                Files.writeString(aliasFile, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            } catch (Exception ignored) {}
        }
    }

    public static Path saveRepoAnalysis(Path projectRoot, String framework, String content) {
        if (projectRoot == null) {
            throw new RepoAnalysisException("projectRoot es requerido para guardar el análisis del repo");
        }
        if (framework == null || framework.isBlank()) {
            throw new RepoAnalysisException("framework es requerido para guardar el análisis del repo");
        }
        if (content == null || content.isBlank()) {
            throw new RepoAnalysisException("content es requerido para guardar el análisis del repo");
        }
        String cleanFw = cleanFrameworkName(framework);
        Path frameworkDir = projectRoot.resolve(".boris").resolve(cleanFw);
        try {
            Files.createDirectories(frameworkDir);
            ensureGitignore(projectRoot);
            Path file = frameworkDir.resolve("_repo_analysis.md");
            Files.writeString(file, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return file;
        } catch (IOException e) {
            throw new RepoAnalysisException("No se pudo guardar el análisis del repo en " + frameworkDir + ": " + e.getMessage(), e);
        }
    }

    public static Path saveLearningCard(Path projectRoot, String framework, String focus, String content) {
        if (projectRoot == null) {
            throw new LearningException("projectRoot es requerido para guardar la learning card");
        }
        if (framework == null || framework.isBlank()) {
            throw new LearningException("framework es requerido para guardar la learning card");
        }
        if (focus == null || focus.isBlank()) {
            throw new LearningException("focus es requerido para guardar la learning card");
        }
        if (content == null || content.isBlank()) {
            throw new LearningException("content es requerido para guardar la learning card");
        }
        String cleanFw = cleanFrameworkName(framework);
        Path frameworkDir = projectRoot.resolve(".boris").resolve(cleanFw);
        try {
            Files.createDirectories(frameworkDir);
            ensureGitignore(projectRoot);
            Path file = frameworkDir.resolve("_learning_" + sanitizeName(focus) + ".md");
            Files.writeString(file, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            return file;
        } catch (IOException e) {
            throw new LearningException("No se pudo guardar la learning card en " + frameworkDir + ": " + e.getMessage(), e);
        }
    }

    public static String readRepoAnalysis(Path projectRoot, String framework) {
        if (projectRoot == null || framework == null || framework.isBlank()) {
            return null;
        }
        String cleanFw = cleanFrameworkName(framework);
        Path file = projectRoot.resolve(".boris").resolve(cleanFw).resolve("_repo_analysis.md");
        if (!Files.isRegularFile(file)) {
            Path raw = projectRoot.resolve(".boris").resolve(sanitizeName(framework)).resolve("_repo_analysis.md");
            file = raw;
        }
        if (!Files.isRegularFile(file)) {
            return null;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RepoAnalysisException("No se pudo leer el análisis del repo en " + file + ": " + e.getMessage(), e);
        }
    }

    /**
     * Ingesta automática a nivel código: cuando se lee un archivo Java en el proyecto o un repo descargado,
     * genera y persiste la ficha técnica en .boris/<framework>/<CLASE>.md directamente en disco.
     */
    public static void autoRecordJavaFile(Path javaFile, Path projectRoot) {
        if (javaFile == null || !Files.isRegularFile(javaFile) || !javaFile.toString().endsWith(".java")) {
            return;
        }
        try {
            Path root = projectRoot != null ? projectRoot : resolveProjectRoot(null);
            String framework = deduceFramework(javaFile, root);
            String className = javaFile.getFileName().toString().replace(".java", "");

            if (hasKnowledge(root, framework, className)) {
                return;
            }

            String card = KnowledgeExtractorService.extractFromJavaFile(javaFile, framework);
            if (card != null && !card.isBlank()) {
                saveKnowledge(root, framework, className, card);
                com.boris.tooling.ToolProgressNotifier.notify("knowledge_cache",
                    "Ficha generada automáticamente: .boris/" + framework + "/" + className + ".md");
            }
        } catch (Exception ignored) {}
    }

    public static String deduceFramework(Path javaFile, Path projectRoot) {
        String pathStr = javaFile.toAbsolutePath().normalize().toString();
        if (pathStr.contains(".boris/repos/")) {
            int idx = pathStr.indexOf(".boris/repos/");
            String sub = pathStr.substring(idx + ".boris/repos/".length());
            int nextSlash = sub.indexOf('/');
            if (nextSlash != -1) {
                return sub.substring(0, nextSlash);
            }
        }
        // Deduce from package if possible
        try {
            var cu = com.github.javaparser.StaticJavaParser.parse(javaFile);
            var pkg = cu.getPackageDeclaration().map(p -> p.getNameAsString()).orElse("");
            if (!pkg.isBlank()) {
                String[] parts = pkg.split("\\.");
                if (parts.length >= 2) {
                    return parts[1]; // e.g. dev.tamboui -> tamboui
                }
                return parts[0];
            }
        } catch (Exception ignored) {}

        if (projectRoot != null && projectRoot.getFileName() != null) {
            return projectRoot.getFileName().toString();
        }
        return "framework";
    }

    public static List<String> listClasses(Path projectRoot, String framework) {
        String cleanFw = cleanFrameworkName(framework);
        Path frameworkDir = projectRoot.resolve(".boris").resolve(cleanFw);
        if (!Files.isDirectory(frameworkDir)) {
            // Probar con el nombre sanitizado directo
            frameworkDir = projectRoot.resolve(".boris").resolve(sanitizeName(framework));
            if (!Files.isDirectory(frameworkDir)) {
                return Collections.emptyList();
            }
        }
        List<String> classes = new ArrayList<>();
        try (Stream<Path> stream = Files.list(frameworkDir)) {
            stream.filter(p -> p.toString().endsWith(".md"))
                  .filter(p -> !p.getFileName().toString().startsWith("_"))
                  .map(p -> {
                      String name = p.getFileName().toString();
                      return name.substring(0, name.length() - 3);
                  })
                  .sorted()
                  .forEach(classes::add);
        } catch (IOException ignored) {}
        return classes;
    }

    public static List<String> listFrameworks(Path projectRoot) {
        Path borisDir = projectRoot.resolve(".boris");
        if (!Files.isDirectory(borisDir)) {
            return Collections.emptyList();
        }
        List<String> frameworks = new ArrayList<>();
        try (Stream<Path> stream = Files.list(borisDir)) {
            stream.filter(Files::isDirectory)
                  .map(p -> p.getFileName().toString())
                  .filter(name -> !name.startsWith(".") && !name.equals("repos") && !name.equals("cache"))
                  .sorted()
                  .forEach(frameworks::add);
        } catch (IOException ignored) {}
        return frameworks;
    }

    public static void ensureGitignore(Path projectRoot) {
        try {
            Path borisDir = projectRoot.resolve(".boris");
            if (!Files.exists(borisDir)) {
                Files.createDirectories(borisDir);
            }
            Path gitignore = borisDir.resolve(".gitignore");
            if (!Files.exists(gitignore)) {
                Files.writeString(gitignore, "*\n!.gitignore\n", StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {}
    }

    public static String cleanFrameworkName(String name) {
        if (name == null || name.isBlank()) return "common";
        String clean = name.trim();
        if (clean.contains("/")) {
            clean = clean.substring(clean.lastIndexOf('/') + 1);
        }
        return sanitizeName(clean);
    }

    public static boolean matchesFramework(String dirName, String framework) {
        if (dirName.equalsIgnoreCase(framework)) return true;
        String c1 = dirName.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        String c2 = framework.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
        return c1.contains(c2) || c2.contains(c1) || (c1.startsWith("tambo") && c2.startsWith("tambo"));
    }

    private static String sanitizeName(String name) {
        if (name == null) return "unknown";
        return name.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
