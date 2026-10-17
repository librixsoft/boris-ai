package com.boris.edit;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.boris.tooling.tool.SearchContextTool;

public class LineValidator {

    private final SymbolCache symbolCache;

    private static final Pattern NEW_INSTANCE = Pattern.compile("new\\s+([A-Z][\\w]+)\\s*\\(");
    private static final Pattern METHOD_CALL = Pattern.compile("\\.([a-z][\\w]+)\\s*\\(");
    private static final Pattern EXTENDS_PATTERN = Pattern.compile("extends\\s+([A-Z][\\w]+)");
    private static final Pattern IMPLEMENTS_PATTERN = Pattern.compile("implements\\s+([A-Z][\\w,\\s]+)");
    private static final Pattern IMPORT_PATTERN = Pattern.compile("^\\s*import\\s+([\\w.]+);?\\s*$");
    private static final Pattern VARIABLE_DECL = Pattern.compile("([A-Z][\\w]+)\\s+[a-z][\\w]*\\s*[=;]");

    public LineValidator(SymbolCache symbolCache) {
        this.symbolCache = symbolCache;
    }

    public ValidationResult validate(String line) {
        String trimmed = line.trim();

        if (shouldSkipValidation(trimmed)) {
            return ValidationResult.ok();
        }

        Optional<ValidationResult> importResult = validateImport(trimmed);
        if (importResult.isPresent()) {
            return importResult.get();
        }

        Optional<ValidationResult> newInstanceResult = validateNewInstances(trimmed);
        if (newInstanceResult.isPresent() && !newInstanceResult.get().isValid()) {
            return newInstanceResult.get();
        }

        Optional<ValidationResult> extendsResult = validateExtends(trimmed);
        if (extendsResult.isPresent() && !extendsResult.get().isValid()) {
            return extendsResult.get();
        }

        Optional<ValidationResult> variableResult = validateVariableDeclarations(trimmed);
        if (variableResult.isPresent() && !variableResult.get().isValid()) {
            return variableResult.get();
        }

        return ValidationResult.ok();
    }

    private boolean shouldSkipValidation(String line) {
        if (line.isEmpty()) return true;
        if (line.startsWith("//")) return true;
        if (line.startsWith("/*") || line.startsWith("*") || line.endsWith("*/")) return true;
        if (line.equals("{") || line.equals("}")) return true;
        if (line.startsWith("package ")) return true;
        if (line.startsWith("@")) return true;
        if (line.matches("^\\s*(public|private|protected)?\\s*(static)?\\s*(final)?\\s*(void|int|long|double|float|boolean|char|byte|short|String)\\s+.*")) {
            if (!line.contains("new ") && !line.contains("extends ")) {
                return true;
            }
        }
        return false;
    }

    private Optional<ValidationResult> validateImport(String line) {
        Matcher matcher = IMPORT_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return Optional.empty();
        }

        String importPath = matcher.group(1);

        // Java standard imports always OK
        if (importPath.startsWith("java.") || importPath.startsWith("javax.") || importPath.startsWith("jdk.")) {
            return Optional.of(ValidationResult.ok());
        }

        // Wildcard imports (com.example.*) - check if package exists in known imports
        if (importPath.endsWith(".*")) {
            String packagePrefix = importPath.substring(0, importPath.length() - 1);
            boolean hasMatchingImport = symbolCache.findSimilarImports(packagePrefix).stream()
                .anyMatch(imp -> imp.startsWith(packagePrefix));
            if (hasMatchingImport) {
                return Optional.of(ValidationResult.ok());
            }
        }

        // Verificar si el import completo está en los imports verificados
        if (symbolCache.isValidImport(importPath)) {
            return Optional.of(ValidationResult.ok());
        }

        // Si no está en imports verificados, buscar alternativas
        List<String> similarImports = symbolCache.findSimilarImports(importPath);
        String className = importPath.substring(importPath.lastIndexOf('.') + 1);

        if (!similarImports.isEmpty()) {
            return Optional.of(ValidationResult.error(
                "Import '" + importPath + "' no está en los imports verificados",
                similarImports
            ));
        }

        // Fallback: verificar si la clase existe pero no fue consultada
        if (symbolCache.isKnownClass(className)) {
            if (!SearchContextTool.wasConsulted(className)) {
                return Optional.of(ValidationResult.error(
                    "DEBES consultar la API antes de importar '" + className + "'",
                    List.of("Ejecuta: search_context('" + className + "') para ver el import correcto")
                ));
            }
            List<String> suggestions = symbolCache.findSimilarImports(className);
            return Optional.of(ValidationResult.error(
                "Clase '" + className + "' existe pero el import no está verificado",
                suggestions
            ));
        }

        List<String> similar = symbolCache.findSimilarClasses(className);
        return Optional.of(ValidationResult.error(
            "Clase '" + className + "' no encontrada en CONTEXT.md",
            similar
        ));
    }

    private Optional<ValidationResult> validateNewInstances(String line) {
        Matcher matcher = NEW_INSTANCE.matcher(line);
        while (matcher.find()) {
            String className = matcher.group(1);

            // Solo validar clases de LIBRERÍA externa (no Java estándar ni clases locales)
            if (!symbolCache.isLibraryClass(className)) {
                continue; // Clase estándar o local, siempre OK
            }

            // Es clase de librería - verificar si fue consultada
            if (!SearchContextTool.wasConsulted(className)) {
                return Optional.of(ValidationResult.error(
                    "DEBES consultar la API antes de usar '" + className + "'",
                    List.of("Ejecuta: search_context('" + className + "') y luego reintenta")
                ));
            }

            // Verificar si existe en CONTEXT.md
            if (!symbolCache.isKnownClass(className)) {
                List<String> similar = symbolCache.findSimilarClasses(className);
                String searchHint = extractSearchTerm(className);
                List<String> suggestions = new ArrayList<>(similar);
                suggestions.add(0, "USA search_context('" + searchHint + "') para encontrar la clase correcta");
                return Optional.of(ValidationResult.error(
                    "CLASE NO EXISTE: '" + className + "' - NO inventes, busca la alternativa real",
                    suggestions
                ));
            }
        }
        return Optional.of(ValidationResult.ok());
    }

    private Optional<ValidationResult> validateExtends(String line) {
        Matcher matcher = EXTENDS_PATTERN.matcher(line);
        if (matcher.find()) {
            String className = matcher.group(1);

            // Solo validar clases de librería externa
            if (!symbolCache.isLibraryClass(className)) {
                return Optional.of(ValidationResult.ok());
            }

            if (!symbolCache.isKnownClass(className)) {
                List<String> similar = symbolCache.findSimilarClasses(className);
                String searchHint = extractSearchTerm(className);
                List<String> suggestions = new ArrayList<>(similar);
                suggestions.add(0, "USA search_context('" + searchHint + "') para encontrar la clase correcta");
                return Optional.of(ValidationResult.error(
                    "CLASE PADRE NO EXISTE: '" + className + "' - NO inventes, busca la alternativa real",
                    suggestions
                ));
            }
        }
        return Optional.of(ValidationResult.ok());
    }

    private Optional<ValidationResult> validateVariableDeclarations(String line) {
        Matcher matcher = VARIABLE_DECL.matcher(line);
        while (matcher.find()) {
            String className = matcher.group(1);

            // Solo validar clases de librería externa
            if (!symbolCache.isLibraryClass(className)) {
                continue; // Clase estándar o local, siempre OK
            }

            // Es clase de librería - verificar si fue consultada
            if (!SearchContextTool.wasConsulted(className)) {
                return Optional.of(ValidationResult.error(
                    "DEBES consultar la API antes de usar '" + className + "'",
                    List.of("Ejecuta: search_context('" + className + "') y luego reintenta")
                ));
            }

            if (!symbolCache.isKnownClass(className)) {
                List<String> similar = symbolCache.findSimilarClasses(className);
                String searchHint = extractSearchTerm(className);
                List<String> suggestions = new ArrayList<>(similar);
                suggestions.add(0, "USA search_context('" + searchHint + "') para encontrar el tipo correcto");
                return Optional.of(ValidationResult.error(
                    "TIPO NO EXISTE: '" + className + "' - NO inventes, busca la alternativa real",
                    suggestions
                ));
            }
        }
        return Optional.of(ValidationResult.ok());
    }

    /**
     * Extrae un término de búsqueda útil del nombre de clase.
     * Ej: "ScreenBuffer" -> "Screen", "TerminalFactory" -> "Terminal"
     */
    private String extractSearchTerm(String className) {
        // Quitar sufijos comunes para obtener término base
        String[] suffixes = {"Buffer", "Factory", "Builder", "Manager", "Handler", "Listener", "Adapter", "Impl", "Helper", "Utils"};
        for (String suffix : suffixes) {
            if (className.endsWith(suffix) && className.length() > suffix.length()) {
                return className.substring(0, className.length() - suffix.length());
            }
        }
        // Si tiene CamelCase, tomar la primera palabra
        StringBuilder firstWord = new StringBuilder();
        for (char c : className.toCharArray()) {
            if (Character.isUpperCase(c) && firstWord.length() > 0) {
                break;
            }
            firstWord.append(c);
        }
        return firstWord.length() > 2 ? firstWord.toString() : className;
    }

    public record ValidationResult(
        boolean isValid,
        String errorMessage,
        List<String> suggestions
    ) {
        public static ValidationResult ok() {
            return new ValidationResult(true, null, List.of());
        }

        public static ValidationResult error(String message, List<String> suggestions) {
            return new ValidationResult(false, message, suggestions != null ? suggestions : List.of());
        }

        public static ValidationResult error(String message) {
            return error(message, List.of());
        }

        public boolean hasSuggestions() {
            return suggestions != null && !suggestions.isEmpty();
        }

        public String formatError() {
            StringBuilder sb = new StringBuilder();
            sb.append("❌ ERROR: ").append(errorMessage);
            if (hasSuggestions()) {
                sb.append("\n   Alternativas: ").append(String.join(", ", suggestions));
            }
            return sb.toString();
        }
    }
}
