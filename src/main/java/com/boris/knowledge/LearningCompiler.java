package com.boris.knowledge;

import com.boris.context.CodeSymbol;
import com.boris.context.ContentRepository;
import com.boris.context.SymbolRepository;
import com.boris.tooling.LearningLimiter;
import com.boris.tooling.ToolProgressNotifier;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class LearningCompiler {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final int MAX_DOCS = 8;
    private static final int MAX_SYMBOLS = 8;
    private static final int MAX_PATTERNS = 8;
    private static final int MAX_EXAMPLES = 3;
    private static final int MAX_SNIPPET_LINES = 25;
    private static final int MAX_AUTOCARD_CLASSES = 5;

    private static final Pattern EXAMPLE_PATH_PATTERN = Pattern.compile(
        "^\\s{0,6}[-] ([^\\n(]+)[ (]*\\b(example|test)\\)", Pattern.MULTILINE);

    private LearningCompiler() {
    }

    public static String compile(String repo, String focus, String projectPath) {
        if (repo == null || repo.isBlank()) {
            throw new LearningException("repo es requerido para action='learn'");
        }
        if (focus == null || focus.isBlank()) {
            throw new LearningException("focus es requerido para action='learn'");
        }

        Path projectRoot = KnowledgeStorageService.resolveProjectRoot(projectPath);
        String repoShort = shortName(repo);

        boolean indexed = ContentRepository.repoPath(repo) != null
            || SymbolRepository.countByRepo(repoShort) > 0
            || ContentRepository.countByRepo(repoShort) > 0;
        if (!indexed) {
            throw new LearningException("El repo '" + repo + "' no está indexado. Corrá github_repo(repo='owner/repo', action='download') primero.");
        }

        String analysis = KnowledgeStorageService.readRepoAnalysis(projectRoot, repoShort);
        if (analysis == null || analysis.isBlank()) {
            throw new LearningException("No existe _repo_analysis.md para '" + repo + "'. Corrá github_repo(repo, action='download', force=true) para regenerar el análisis.");
        }

        ToolProgressNotifier.notify("github_repo", "Sintetizando conocimiento de " + repoShort + " (focus=" + focus + ")");

        List<String> patterns = extractPatterns(analysis, focus);
        List<String> keyApis = extractKeyApis(repoShort, focus);
        List<String> examplePaths = extractExamplePaths(analysis);
        List<String> usageExamples = loadUsageExamples(examplePaths, repoShort);
        RepoStack stack = extractStack(analysis);

        String markdown = renderLearningMarkdown(repoShort, focus, patterns, keyApis, usageExamples, stack);
        Path saved = KnowledgeStorageService.saveLearningCard(projectRoot, repoShort, focus, markdown);

        List<String> saveErrors = new ArrayList<>();
        try {
            ContentRepository.saveBatch(List.of(new ContentRepository.DocChunk(
                repoShort, saved.getFileName().toString(), 0, "Learning " + repoShort + " " + focus, markdown, "learning")));
        } catch (RuntimeException e) {
            saveErrors.add("index_learning: " + e.getMessage());
        }

        List<String> autoCards = autoSaveKnowledgeCards(projectRoot, repoShort, keyApis, saveErrors);
        LearningLimiter.markRepoLearned(repoShort);

        return buildJson(repoShort, focus, patterns, keyApis, usageExamples, saved, saveErrors, autoCards, stack);
    }

    private static String shortName(String repo) {
        int slash = repo.indexOf('/');
        return slash > 0 && slash < repo.length() - 1 ? repo.substring(slash + 1) : repo;
    }

    private static List<String> extractPatterns(String analysis, String focus) {
        List<String> patterns = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        String focusNorm = focus.toLowerCase(Locale.ROOT);
        List<String> sections = List.of("## Arquitectura", "## Patrones de Uso Detectados", "## Stack y Estructura");
        for (String heading : sections) {
            String body = section(analysis, heading);
            if (body.isBlank()) {
                continue;
            }
            Matcher matcher = Pattern.compile("(?m)^\\s{0,6}[-] (.+)$").matcher(body);
            while (matcher.find() && patterns.size() < MAX_PATTERNS) {
                String line = matcher.group(1).strip();
                String lineNorm = line.toLowerCase(Locale.ROOT);
                if (!lineNorm.contains(focusNorm) || !isMeaningfulPatternLine(line)) {
                    continue;
                }
                String dedupeKey = normalizeLine(lineNorm);
                if (seen.add(dedupeKey)) {
                    patterns.add("Patrón detectado (" + heading.substring(3) + "): " + line);
                }
            }
        }
        return patterns;
    }

    private static boolean isMeaningfulPatternLine(String line) {
        return line.contains("extends") || line.contains("implements")
            || line.contains("(") || line.contains(":") || line.contains("=");
    }

    private static String normalizeLine(String line) {
        return line.replaceAll("\\s+", " ");
    }

    private record RepoStack(String buildSystem, String mainLanguage, List<String> dependencies) {}

    private static RepoStack extractStack(String analysis) {
        String body = section(analysis, "## Stack y Estructura");
        String build = firstMatch(body, "Build system: (.+)");
        String language = firstMatch(body, "Lenguaje principal: (.+)");
        List<String> deps = new ArrayList<>();
        int depsIndex = body.indexOf("Dependencias clave:");
        if (depsIndex >= 0) {
            String depsBlock = blockUntilBlankLine(body.substring(depsIndex));
            Matcher depMatcher = Pattern.compile("(?m)^\\s{2,4}[-] (.+)$").matcher(depsBlock);
            while (depMatcher.find() && deps.size() < 10) {
                deps.add(depMatcher.group(1).strip());
            }
        }
        return new RepoStack(build, language, deps);
    }

    private static String blockUntilBlankLine(String block) {
        int blank = block.indexOf("\n\n");
        return blank >= 0 ? block.substring(0, blank) : block;
    }

    private static String firstMatch(String body, String regex) {
        Matcher matcher = Pattern.compile(regex).matcher(body);
        return matcher.find() ? matcher.group(1).strip() : null;
    }

    private static String section(String markdown, String heading) {
        int start = markdown.indexOf(heading);
        if (start < 0) {
            return "";
        }
        int end = markdown.indexOf("\n## ", start + heading.length());
        String body = end > start ? markdown.substring(start + heading.length(), end)
            : markdown.substring(start + heading.length());
        return body.strip();
    }

    private static List<String> extractKeyApis(String repoShort, String focus) {
        List<String> apis = new ArrayList<>();
        List<CodeSymbol> symbols = SymbolRepository.search(focus, null, repoShort);
        if (symbols.size() > MAX_SYMBOLS) {
            symbols = symbols.subList(0, MAX_SYMBOLS);
        }
        for (CodeSymbol symbol : symbols) {
            apis.add(symbol.getSignature() + " [" + symbol.getType().name().toLowerCase(Locale.ROOT)
                + " @ " + symbol.getFile() + "]");
        }
        if (apis.isEmpty()) {
            Map<String, Integer> top = SymbolRepository.methodCountByClass(repoShort);
            for (Map.Entry<String, Integer> entry : top.entrySet()) {
                if (apis.size() >= MAX_SYMBOLS) {
                    break;
                }
                apis.add(entry.getKey() + " (" + entry.getValue() + " métodos públicos)");
            }
        }
        return apis;
    }

    private static List<String> extractExamplePaths(String analysis) {
        List<String> paths = new ArrayList<>();
        String usage = section(analysis, "## Patrones de Uso Detectados");
        Matcher matcher = EXAMPLE_PATH_PATTERN.matcher(usage);
        while (matcher.find() && paths.size() < MAX_EXAMPLES) {
            String path = matcher.group(1).strip();
            if (!path.isBlank()) {
                paths.add(path);
            }
        }
        return paths;
    }

    private static List<String> loadUsageExamples(List<String> examplePaths, String repoShort) {
        List<String> out = new ArrayList<>();
        String cachePath = ContentRepository.repoPath(repoShort);
        if (cachePath == null) {
            throw new LearningException("No hay ruta de cache registrada para '" + repoShort + "'");
        }
        Path cacheDir = Path.of(cachePath).toAbsolutePath().normalize();
        for (String relative : examplePaths) {
            if (out.size() >= MAX_EXAMPLES) {
                break;
            }
            Path file = cacheDir.resolve(relative).normalize();
            if (!file.startsWith(cacheDir)) {
                throw new LearningException("Path de ejemplo fuera del cache del repo: " + relative);
            }
            if (!Files.isRegularFile(file)) {
                continue;
            }
            try {
                List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
                List<String> snippet = new ArrayList<>();
                for (int i = 0; i < lines.size() && snippet.size() < MAX_SNIPPET_LINES; i++) {
                    String line = lines.get(i);
                    if (line.strip().startsWith("package ")) {
                        continue;
                    }
                    snippet.add(line);
                }
                if (!snippet.isEmpty()) {
                    out.add("// " + relative + "\n" + String.join("\n", snippet));
                }
            } catch (IOException e) {
                throw new LearningException("No se pudo leer el ejemplo " + file + ": " + e.getMessage(), e);
            }
        }
        return out;
    }

    private static List<String> autoSaveKnowledgeCards(Path projectRoot, String repoShort,
                                                       List<String> keyApis, List<String> saveErrors) {
        List<String> saved = new ArrayList<>();
        for (String api : keyApis) {
            if (saved.size() >= MAX_AUTOCARD_CLASSES) {
                break;
            }
            String className = classNameOfSignature(api);
            if (className == null || className.isBlank()) {
                continue;
            }
            try {
                if (!KnowledgeStorageService.hasKnowledge(projectRoot, repoShort, className)) {
                    List<CodeSymbol> symbols = SymbolRepository.search(className, null, repoShort);
                    String card = KnowledgeExtractorService.extractFromSymbols(repoShort, className, symbols);
                    if (card != null && !card.isBlank()) {
                        KnowledgeStorageService.saveKnowledge(projectRoot, repoShort, className, card);
                        saved.add(className);
                    }
                }
            } catch (IOException e) {
                saveErrors.add("autocard_" + className + ": " + e.getMessage());
            } catch (RuntimeException e) {
                saveErrors.add("autocard_" + className + ": " + e.getMessage());
            }
        }
        return saved;
    }

    private static String classNameOfSignature(String api) {
        Matcher matcher = Pattern.compile("(?i)interface ([\\w$]+)|class ([\\w$]+)|enum ([\\w$]+)|([\\w$]+) \\(")
            .matcher(api);
        if (matcher.find()) {
            for (int i = 1; i <= 4; i++) {
                if (matcher.group(i) != null) {
                    return matcher.group(i);
                }
            }
        }
        return null;
    }

    private static String renderLearningMarkdown(String repoShort, String focus, List<String> patterns,
                                                 List<String> keyApis, List<String> usageExamples,
                                                 RepoStack stack) {
        StringBuilder sb = new StringBuilder();
        sb.append("# Learning: ").append(repoShort).append(" / ").append(focus).append("\n\n");

        sb.append("## Patrones\n");
        if (patterns.isEmpty()) {
            sb.append("- Sin patrones que coincidan con el focus; consulte el análisis completo en _repo_analysis.md.\n");
        } else {
            for (String pattern : patterns) {
                sb.append("- ").append(pattern).append("\n");
            }
        }
        sb.append("\n");

        sb.append("## APIs clave\n");
        for (String api : keyApis) {
            sb.append("- `").append(api).append("`\n");
        }
        sb.append("\n");

        sb.append("## Ejemplos de uso (extraídos del repo)\n");
        for (String example : usageExamples) {
            sb.append("```\n").append(example).append("\n```\n\n");
        }
        if (usageExamples.isEmpty()) {
            sb.append("- Sin ejemplos ejecutables detectados.\n");
        }
        sb.append("\n");

        sb.append("## Contrato de adaptación (para el proyecto actual)\n");
        sb.append("- Stack del repo: build=").append(stack.buildSystem() != null ? stack.buildSystem() : "no-detectado")
            .append(", lenguaje=").append(stack.mainLanguage() != null ? stack.mainLanguage() : "no-detectado").append("\n");
        if (stack.dependencies().isEmpty()) {
            sb.append("- Dependencias clave del repo: no-detectado\n");
        } else {
            sb.append("- Dependencias clave del repo:\n");
            for (String dep : stack.dependencies()) {
                sb.append("  - ").append(dep).append("\n");
            }
        }
        sb.append("La implementación debe usar las convenciones del proyecto actual (paquetes, imports,\n");
        sb.append("estilos y coexistencia con los módulos ya presentes). El sistema bloquea mediante\n");
        sb.append("detección de clonación cualquier escritura casi idéntica a un archivo del repo indexado.\n\n");
        return sb.toString();
    }

    private static String buildJson(String repoShort, String focus, List<String> patterns, List<String> keyApis,
                                    List<String> usageExamples, Path saved, List<String> saveErrors,
                                    List<String> autoCards, RepoStack stack) {
        try {
            ObjectNode root = MAPPER.createObjectNode();
            root.put("success", true);
            root.put("repo", repoShort);
            root.put("focus", focus);
            ArrayNode patternsArray = root.putArray("patterns");
            patterns.forEach(patternsArray::add);
            ArrayNode apiArray = root.putArray("key_apis");
            keyApis.forEach(apiArray::add);
            ArrayNode examplesArray = root.putArray("usage_examples");
            usageExamples.forEach(examplesArray::add);
            ArrayNode autoCardsArray = root.putArray("knowledge_cards_generated");
            autoCards.forEach(autoCardsArray::add);
            root.put("saved_as", saved.toAbsolutePath().toString());

            ObjectNode adaptation = root.putObject("adaptation");
            adaptation.put("build_system", stack.buildSystem() != null ? stack.buildSystem() : "no-detectado");
            adaptation.put("main_language", stack.mainLanguage() != null ? stack.mainLanguage() : "no-detectado");
            ArrayNode depsArray = adaptation.putArray("key_dependencies");
            stack.dependencies().forEach(depsArray::add);
            adaptation.put("requirement",
                "Implementaciones propias con convenciones del proyecto actual. La escritura de código "
                + "casi-idéntico a archivos del repo indexado será bloqueada por detección de clonación.");

            root.put("next_step",
                "Escribi tu propia implementación adaptando estos patrones a las convenciones del proyecto actual. "
                + "No repliques archivos del repo. Usá github_repo(action='search'|'symbols') para consultar "
                + "detalles puntuales.");
            if (!saveErrors.isEmpty()) {
                ArrayNode errorsArray = root.putArray("side_errors");
                saveErrors.forEach(errorsArray::add);
            }
            return MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(root);
        } catch (Exception e) {
            throw new LearningException("Error serializando el resultado de learn: " + e.getMessage(), e);
        }
    }
}
