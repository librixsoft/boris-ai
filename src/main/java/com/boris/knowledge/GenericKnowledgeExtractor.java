package com.boris.knowledge;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class GenericKnowledgeExtractor {

    private static final int MAX_SYMBOLS_PER_FILE = 30;
    private static final int MAX_JSDOC_SNIPPETS = 5;
    private static final int MAX_SNIPPET_LINES = 30;

    private static final Pattern EXPORT_SYMBOL_PATTERN = Pattern.compile(
        "(?m)^export\\s+(?:default\\s+)?(?:abstract\\s+)?(class|interface|function|const|let|var|type|enum)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern JSDOC_PATTERN = Pattern.compile(
        "(?s)/\\*\\*(.*?)\\*/\\s*export\\s+(?:default\\s+)?(?:abstract\\s+)?(class|function|const|interface)\\s+([A-Za-z_$][\\w$]*)");
    private static final Pattern EXAMPLE_TAG_PATTERN = Pattern.compile("@example");
    private static final Pattern FENCE_PATTERN = Pattern.compile("(?m)^[ \t]*```[ \t]*([A-Za-z0-9_+-]*)[ \t]*\\r?\\n(.*?)^[ \t]*```");

    public static Optional<String> extract(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new ExtractionException("El archivo no existe o no es regular: " + file);
        }
        String ext = extensionOf(file.getFileName().toString().toLowerCase(Locale.ROOT));
        String source = readSource(file);
        return switch (ext) {
            case "js", "mjs", "cjs", "ts", "tsx", "jsx" -> extractJsCard(source, ext);
            default -> throw new ExtractionException("Extensión no soportada por GenericKnowledgeExtractor: " + ext);
        };
    }

    public static List<String> extractMarkdownExamples(Path file) {
        if (file == null || !Files.isRegularFile(file)) {
            throw new ExtractionException("El archivo no existe o no es regular: " + file);
        }
        String source = readSource(file);
        return extractFencedSnippets(source, "md");
    }

    public static List<String> extractFencedSnippets(String source, String expectedExt) {
        List<String> snippets = new ArrayList<>();
        Matcher matcher = FENCE_PATTERN.matcher(source);
        while (matcher.find() && snippets.size() < MAX_JSDOC_SNIPPETS) {
            String lang = matcher.group(1).toLowerCase(Locale.ROOT);
            if (!lang.isBlank() && !lang.equals(expectedExt)) {
                String body = matcher.group(2).strip();
                long lineCount = body.lines().count();
                if (lineCount >= 3 && lineCount <= 60) {
                    snippets.add(body);
                }
            }
        }
        return snippets;
    }

    public static List<String> extractPlainJavadocExamples(String source) {
        List<String> snippets = new ArrayList<>();
        Matcher matcher = JSDOC_PATTERN.matcher(source);
        while (matcher.find() && snippets.size() < MAX_JSDOC_SNIPPETS) {
            String doc = matcher.group(1);
            if (!EXAMPLE_TAG_PATTERN.matcher(doc).find()) {
                continue;
            }
            String snippet = stripDocMarkup(doc);
            if (!snippet.isBlank()) {
                snippets.add(snippet);
            }
        }
        return snippets;
    }

    private static Optional<String> extractJsCard(String source, String ext) {
        List<String> exports = new ArrayList<>();
        Matcher matcher = EXPORT_SYMBOL_PATTERN.matcher(source);
        while (matcher.find() && exports.size() < MAX_SYMBOLS_PER_FILE) {
            exports.add(matcher.group(1) + " " + matcher.group(2));
        }
        if (exports.isEmpty()) {
            return Optional.empty();
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# Generic TS/JS Card\n\n");
        sb.append("- **Lenguaje:** `").append(ext).append("`\n");
        sb.append("- **Exports detectados:** ").append(exports.size()).append("\n\n");

        sb.append("## Símbolos Exportados\n");
        for (String symbol : exports) {
            sb.append("- `").append(symbol).append("`\n");
        }
        sb.append("\n");

        List<String> examples = extractPlainJavadocExamples(source);
        if (!examples.isEmpty()) {
            sb.append("## Ejemplos de Uso (JSDoc @example)\n");
            for (String example : examples) {
                sb.append("```\n").append(truncateLines(example)).append("\n```\n\n");
            }
        }
        return Optional.of(sb.toString());
    }

    private static String truncateLines(String snippet) {
        List<String> lines = new ArrayList<>();
        for (String line : snippet.lines().toList()) {
            if (lines.size() >= MAX_SNIPPET_LINES) {
                break;
            }
            lines.add(line);
        }
        return String.join("\n", lines);
    }

    private static String stripDocMarkup(String raw) {
        if (raw == null) {
            return "";
        }
        StringBuilder result = new StringBuilder();
        boolean keepLines = false;
        for (String line : raw.split("\n", -1)) {
            String stripped = line.replaceFirst("^\\s*/?\\*+\\s?", "").strip();
            if (stripped.startsWith("@example") && !keepLines) {
                keepLines = true;
                continue;
            }
            if (keepLines && stripped.startsWith("@")) {
                keepLines = false;
            }
            if (keepLines && !stripped.isBlank()) {
                result.append(stripped).append("\n");
            }
        }
        return result.toString().strip();
    }

    private static String readSource(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new ExtractionException("No se pudo leer " + file + ": " + e.getMessage(), e);
        }
    }

    private static String extensionOf(String lowerName) {
        int dot = lowerName.lastIndexOf('.');
        if (dot < 0 || dot == lowerName.length() - 1) {
            return "";
        }
        return lowerName.substring(dot + 1);
    }

    public static class ExtractionException extends RuntimeException {
        public ExtractionException(String message) {
            super(message);
        }

        public ExtractionException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
