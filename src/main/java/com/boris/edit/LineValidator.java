package com.boris.edit;

import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
        if (importPath.startsWith("java.") || importPath.startsWith("javax.") || importPath.startsWith("jdk.")) {
            return Optional.of(ValidationResult.ok());
        }

        String className = importPath.substring(importPath.lastIndexOf('.') + 1);
        if (symbolCache.isKnownClass(className)) {
            return Optional.of(ValidationResult.ok());
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
            if (!symbolCache.isKnownClass(className)) {
                List<String> similar = symbolCache.findSimilarClasses(className);
                return Optional.of(ValidationResult.error(
                    "Clase '" + className + "' no encontrada en CONTEXT.md",
                    similar
                ));
            }
        }
        return Optional.of(ValidationResult.ok());
    }

    private Optional<ValidationResult> validateExtends(String line) {
        Matcher matcher = EXTENDS_PATTERN.matcher(line);
        if (matcher.find()) {
            String className = matcher.group(1);
            if (!symbolCache.isKnownClass(className)) {
                List<String> similar = symbolCache.findSimilarClasses(className);
                return Optional.of(ValidationResult.error(
                    "Clase padre '" + className + "' no encontrada en CONTEXT.md",
                    similar
                ));
            }
        }
        return Optional.of(ValidationResult.ok());
    }

    private Optional<ValidationResult> validateVariableDeclarations(String line) {
        Matcher matcher = VARIABLE_DECL.matcher(line);
        while (matcher.find()) {
            String className = matcher.group(1);
            if (!symbolCache.isKnownClass(className)) {
                List<String> similar = symbolCache.findSimilarClasses(className);
                return Optional.of(ValidationResult.error(
                    "Tipo '" + className + "' no encontrado en CONTEXT.md",
                    similar
                ));
            }
        }
        return Optional.of(ValidationResult.ok());
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
