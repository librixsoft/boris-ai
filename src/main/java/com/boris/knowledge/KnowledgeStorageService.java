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
        String safeFramework = sanitizeName(framework);
        return projectRoot.resolve(".boris").resolve(safeFramework);
    }

    public static Path getKnowledgeFilePath(Path projectRoot, String framework, String className) {
        Path frameworkDir = getFrameworkDir(projectRoot, framework);
        String safeClass = sanitizeName(className) + ".md";
        return frameworkDir.resolve(safeClass);
    }

    public static boolean hasKnowledge(Path projectRoot, String framework, String className) {
        if (framework == null || framework.isBlank() || className == null || className.isBlank()) {
            return false;
        }
        Path file = getKnowledgeFilePath(projectRoot, framework, className);
        return Files.isRegularFile(file);
    }

    public static String readKnowledge(Path projectRoot, String framework, String className) {
        Path file = getKnowledgeFilePath(projectRoot, framework, className);
        if (!Files.exists(file)) {
            return null;
        }
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public static void saveKnowledge(Path projectRoot, String framework, String className, String content) throws IOException {
        Path frameworkDir = getFrameworkDir(projectRoot, framework);
        Files.createDirectories(frameworkDir);
        ensureGitignore(projectRoot);

        Path file = getKnowledgeFilePath(projectRoot, framework, className);
        Files.writeString(file, content, StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }

    public static List<String> listClasses(Path projectRoot, String framework) {
        Path frameworkDir = getFrameworkDir(projectRoot, framework);
        if (!Files.isDirectory(frameworkDir)) {
            return Collections.emptyList();
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

    private static String sanitizeName(String name) {
        if (name == null) return "unknown";
        return name.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
