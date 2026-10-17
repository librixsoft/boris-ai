package com.boris.edit;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SymbolCache {

    private final Map<String, Set<String>> classToMethods = new HashMap<>();
    private final Map<String, List<String>> classToConstructors = new HashMap<>();
    private final Set<String> knownClasses = new HashSet<>();
    private final Set<String> knownImports = new HashSet<>();
    private final Set<String> libraryPackages = new HashSet<>();

    // Packages estándar que NUNCA se validan (cualquier lenguaje puede agregar los suyos)
    private static final Set<String> STANDARD_PACKAGES = Set.of(
        "java.", "javax.", "jdk.",           // Java
        "kotlin.", "kotlinx.",               // Kotlin
        "scala.",                            // Scala
        "android.", "androidx.",             // Android
        "org.junit.", "org.testng.",         // Testing
        "org.slf4j.", "org.apache.logging."  // Logging
    );

    private static final Pattern CLASS_HEADER = Pattern.compile("###\\s*`([A-Z][\\w]+)`");
    private static final Pattern METHOD_PATTERN = Pattern.compile("-\\s*`?([a-z][\\w]+)\\s*\\([^)]*\\)`?");
    private static final Pattern CONSTRUCTOR_PATTERN = Pattern.compile("-\\s*`?([A-Z][\\w]+)\\s*\\(([^)]*)\\)`?");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("import\\s+([\\w.]+);?");

    public void loadFromContext(Path contextFile) throws IOException {
        if (!Files.exists(contextFile)) {
            return;
        }

        String content = Files.readString(contextFile);
        parseContextContent(content);
    }

    private void parseContextContent(String content) {
        String[] lines = content.split("\n");
        String currentClass = null;

        for (String line : lines) {
            Matcher classMatch = CLASS_HEADER.matcher(line);
            if (classMatch.find()) {
                currentClass = classMatch.group(1);
                knownClasses.add(currentClass);
                classToMethods.putIfAbsent(currentClass, new HashSet<>());
                classToConstructors.putIfAbsent(currentClass, new ArrayList<>());
                continue;
            }

            if (currentClass != null) {
                Matcher constructorMatch = CONSTRUCTOR_PATTERN.matcher(line);
                if (constructorMatch.find() && constructorMatch.group(1).equals(currentClass)) {
                    String params = constructorMatch.group(2).trim();
                    classToConstructors.get(currentClass).add(params);
                    continue;
                }

                Matcher methodMatch = METHOD_PATTERN.matcher(line);
                if (methodMatch.find()) {
                    classToMethods.get(currentClass).add(methodMatch.group(1));
                }
            }

            Matcher importMatch = IMPORT_PATTERN.matcher(line);
            if (importMatch.find()) {
                String importPath = importMatch.group(1);
                knownImports.add(importPath);
                // Extraer package de librería externa (no estándar)
                if (!isStandardPackage(importPath)) {
                    int lastDot = importPath.lastIndexOf('.');
                    if (lastDot > 0) {
                        // Guardar package base (ej: com.googlecode.lanterna de com.googlecode.lanterna.gui2.Panel)
                        String[] parts = importPath.split("\\.");
                        if (parts.length >= 3) {
                            libraryPackages.add(parts[0] + "." + parts[1] + "." + parts[2]);
                        }
                    }
                }
            }
        }
    }

    private boolean isStandardPackage(String importPath) {
        for (String stdPkg : STANDARD_PACKAGES) {
            if (importPath.startsWith(stdPkg)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Verifica si una clase es válida para usar.
     * - Si es clase del CONTEXT.md (librería externa) → debe estar en knownClasses
     * - Si NO es de librería externa → siempre OK (es clase local o estándar)
     */
    public boolean isKnownClass(String className) {
        // Si está en el CONTEXT.md, es válida
        if (knownClasses.contains(className)) {
            return true;
        }
        // Si NO está en knownClasses, verificar si es de librería externa
        // Si es de librería externa y no está en knownClasses → inválida
        // Si NO es de librería externa → válida (clase local o estándar de Java)
        return !isLibraryClass(className);
    }

    /**
     * Verifica si una clase pertenece a una librería del CONTEXT.md
     */
    public boolean isLibraryClass(String className) {
        // Buscar si hay un import de esta clase en las librerías conocidas
        for (String imp : knownImports) {
            if (imp.endsWith("." + className) && !isStandardPackage(imp)) {
                return true;
            }
        }
        return false;
    }

    public boolean hasMethod(String className, String methodName) {
        if (!isLibraryClass(className)) {
            return true; // Clases no-librería siempre OK
        }
        Set<String> methods = classToMethods.get(className);
        return methods != null && methods.contains(methodName);
    }

    public boolean hasConstructor(String className) {
        if (!isLibraryClass(className)) {
            return true; // Clases no-librería siempre OK
        }
        return classToConstructors.containsKey(className);
    }

    public Set<String> getMethodsForClass(String className) {
        return classToMethods.getOrDefault(className, Set.of());
    }

    public List<String> getConstructorsForClass(String className) {
        return classToConstructors.getOrDefault(className, List.of());
    }

    public Set<String> getAllKnownClasses() {
        return new HashSet<>(knownClasses);
    }

    public Set<String> getLibraryPackages() {
        return new HashSet<>(libraryPackages);
    }

    public boolean isValidImport(String fullImportPath) {
        // Packages estándar siempre OK
        if (isStandardPackage(fullImportPath)) {
            return true;
        }
        // Si no hay imports verificados, hacer fallback a verificar la clase
        if (knownImports.isEmpty()) {
            String className = fullImportPath.substring(fullImportPath.lastIndexOf('.') + 1);
            return isKnownClass(className);
        }
        return knownImports.contains(fullImportPath);
    }

    public boolean hasVerifiedImports() {
        return !knownImports.isEmpty();
    }

    public List<String> findSimilarImports(String importPath) {
        List<String> similar = new ArrayList<>();
        String className = importPath.substring(importPath.lastIndexOf('.') + 1).toLowerCase();

        for (String known : knownImports) {
            String knownClass = known.substring(known.lastIndexOf('.') + 1).toLowerCase();
            if (knownClass.contains(className) || className.contains(knownClass)) {
                similar.add(known);
            }
        }
        return similar;
    }

    public List<String> findSimilarClasses(String unknownClass) {
        List<String> similar = new ArrayList<>();
        String lower = unknownClass.toLowerCase();

        for (String known : knownClasses) {
            if (known.toLowerCase().contains(lower) || lower.contains(known.toLowerCase())) {
                similar.add(known);
            }
        }

        if (similar.isEmpty()) {
            int minDistance = Integer.MAX_VALUE;
            String closest = null;
            for (String known : knownClasses) {
                int distance = levenshteinDistance(lower, known.toLowerCase());
                if (distance < minDistance && distance <= 3) {
                    minDistance = distance;
                    closest = known;
                }
            }
            if (closest != null) {
                similar.add(closest);
            }
        }

        return similar;
    }

    private int levenshteinDistance(String a, String b) {
        int[][] dp = new int[a.length() + 1][b.length() + 1];

        for (int i = 0; i <= a.length(); i++) {
            for (int j = 0; j <= b.length(); j++) {
                if (i == 0) {
                    dp[i][j] = j;
                } else if (j == 0) {
                    dp[i][j] = i;
                } else {
                    dp[i][j] = Math.min(
                        dp[i - 1][j - 1] + (a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1),
                        Math.min(dp[i - 1][j] + 1, dp[i][j - 1] + 1)
                    );
                }
            }
        }

        return dp[a.length()][b.length()];
    }

    public void clear() {
        classToMethods.clear();
        classToConstructors.clear();
        knownClasses.clear();
        knownImports.clear();
    }

    /**
     * Devuelve los packages únicos disponibles (para hint inicial)
     */
    public Set<String> getAvailablePackages() {
        Set<String> packages = new TreeSet<>();
        for (String imp : knownImports) {
            int lastDot = imp.lastIndexOf('.');
            if (lastDot > 0) {
                packages.add(imp.substring(0, lastDot) + ".*");
            }
        }
        return packages;
    }

    /**
     * Devuelve hint de métodos para una clase (máx 10 métodos)
     */
    public String getMethodsHint(String className) {
        Set<String> methods = classToMethods.get(className);
        if (methods == null || methods.isEmpty()) {
            return null;
        }
        List<String> methodList = new ArrayList<>(methods);
        if (methodList.size() > 10) {
            methodList = methodList.subList(0, 10);
        }
        return className + ": " + String.join(", ", methodList) + (methods.size() > 10 ? "..." : "");
    }

    /**
     * Devuelve hint de clases principales (máx 20)
     */
    public String getClassesHint() {
        if (knownClasses.isEmpty()) {
            return null;
        }
        List<String> sorted = new ArrayList<>(knownClasses);
        Collections.sort(sorted);
        if (sorted.size() > 20) {
            sorted = sorted.subList(0, 20);
        }
        return String.join(", ", sorted) + (knownClasses.size() > 20 ? "..." : "");
    }

    /**
     * Detecta qué clases del CONTEXT se usan en una línea y devuelve hints
     */
    public String getHintForLine(String line) {
        String trimmed = line.trim();

        // Detectar clase en import
        if (trimmed.startsWith("import ")) {
            String importPath = trimmed.substring(7).replace(";", "").trim();
            String className = importPath.substring(importPath.lastIndexOf('.') + 1);
            if (!className.equals("*") && knownClasses.contains(className)) {
                return getMethodsHint(className);
            }
            return null;
        }

        // Detectar clase en "new ClassName("
        java.util.regex.Pattern newPattern = java.util.regex.Pattern.compile("new\\s+([A-Z][\\w]+)\\s*\\(");
        java.util.regex.Matcher newMatcher = newPattern.matcher(line);
        if (newMatcher.find()) {
            String className = newMatcher.group(1);
            if (knownClasses.contains(className)) {
                return getMethodsHint(className);
            }
        }

        // Detectar declaración de variable: "ClassName varName"
        java.util.regex.Pattern varPattern = java.util.regex.Pattern.compile("([A-Z][\\w]+)\\s+[a-z][\\w]*\\s*[=;]");
        java.util.regex.Matcher varMatcher = varPattern.matcher(line);
        if (varMatcher.find()) {
            String className = varMatcher.group(1);
            if (knownClasses.contains(className)) {
                return getMethodsHint(className);
            }
        }

        return null;
    }

    public boolean isEmpty() {
        return knownClasses.isEmpty();
    }

    public int classCount() {
        return knownClasses.size();
    }
}
