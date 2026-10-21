package com.boris.tooling;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Detecta escrituras que son casi-clones de archivos de repos cacheados por github_repo.
 *
 * Compara fingerprints por shingles de tokens en dos planos:
 * - raw: tokens literales de código (detecta copia literal).
 * - estructural: keywords de lenguaje y separadores con identificadores neutralizados
 *   (detecta copia con renombres superficiales).
 */
public final class CloneGateService {

    public record Match(String repo, String repoFile, double containment) {}

    public record Verdict(Match match, double secondBestContainment, boolean blocked, String message) {}

    private record FingerprintedFile(String relativePath, Set<Long> rawShingles, Set<Long> structuralShingles) {}

    private static final int RAW_SHINGLE_TOKENS = 8;
    private static final int STRUCTURAL_SHINGLE_TOKENS = 10;
    private static final int MIN_CANDIDATE_SHINGLES_WRITE = 24;
    private static final int MIN_CANDIDATE_SHINGLES_EDIT = 12;
    private static final double BLOCK_CONTAINMENT_WRITE = 0.75;
    private static final double BLOCK_CONTAINMENT_EDIT = 0.85;
    private static final double BLOCK_CONTAINMENT_STRUCTURAL = 0.60;
    private static final double WARN_CONTAINMENT = 0.50;
    private static final int MAX_FILES_PER_REPO = 3000;
    private static final int MAX_FILE_BYTES = 200_000;

    private static final Set<String> SKIP_DIR_SEGMENTS = Set.of(
        "node_modules", "dist", "build", "target", "vendor", ".git", "out", "bin", "coverage",
        "__tests__", "testdata", "obj");

    private static final Set<String> SKIP_FILE_MARKERS = Set.of(".min.", ".lock");
    private static final String SKIP_EXTENSION = "lock";

    private static final Set<String> CODE_EXTENSIONS = Set.of(
        "java", "kt", "kts", "scala", "py", "js", "jsx", "ts", "tsx", "mjs", "cjs",
        "c", "cc", "cpp", "h", "hpp", "cs", "go", "rs", "rb", "php", "swift", "lua", "sh",
        "html", "htm", "css", "scss", "sass", "less", "sql", "vue", "svelte", "dart");

    private static final Set<String> KEYWORD_TOKENS = Set.of(
        "public", "private", "protected", "static", "final", "abstract", "class", "interface",
        "enum", "extends", "implements", "import", "package", "return", "if", "else", "for",
        "while", "switch", "case", "break", "continue", "try", "catch", "finally", "throw",
        "throws", "new", "this", "super", "void", "int", "long", "double", "float", "boolean",
        "char", "byte", "short", "var", "let", "const", "function", "def", "self", "struct",
        "impl", "fn", "func", "type", "value", "operator", "and", "or", "not", "in", "of",
        "module", "export", "default", "async", "await", "yield", "lambda", "match", "when",
        "override", "open", "data", "sealed", "suspend", "val", "nil", "null", "true", "false",
        "end", "do", "then", "select", "insert", "update", "delete", "from", "where", "join",
        "group", "order", "by", "with", "as", "on", "into", "set", "values", "create", "table",
        "begin", "endfor", "elif", "foreach", "namespace", "using", "template", "typename",
        "constexpr", "inline", "virtual", "except", "pass", "withlet");

    private static final Pattern TOKEN_PATTERN = Pattern.compile("[A-Za-z0-9_]+");

    private static final Map<String, List<FingerprintedFile>> REPO_FINGERPRINTS = new ConcurrentHashMap<>();

    private CloneGateService() {
    }

    public static void invalidate(String repoName) {
        if (repoName != null && !repoName.isBlank()) {
            REPO_FINGERPRINTS.remove(repoName.toLowerCase(Locale.ROOT).trim());
        }
    }

    public static Verdict checkContent(String content, boolean fullWrite) {
        if (content == null || content.isBlank()) {
            return null;
        }

        List<String> rawTokens = rawTokensOf(content);
        Set<Long> candidateRaw = shinglesOf(rawTokens, RAW_SHINGLE_TOKENS);
        List<String> structuralTokens = structuralTokensOf(rawTokens);
        Set<Long> candidateStructural = isMeaningfulStructure(structuralTokens)
            ? shinglesOf(structuralTokens, STRUCTURAL_SHINGLE_TOKENS)
            : Set.of();

        int minShingles = fullWrite ? MIN_CANDIDATE_SHINGLES_WRITE : MIN_CANDIDATE_SHINGLES_EDIT;
        if (Math.max(candidateRaw.size(), candidateStructural.size()) < minShingles) {
            return null;
        }

        Match best = null;
        Match structuralBest = null;
        double secondBest = 0.0;

        ensureFingerprints();

        for (Map.Entry<String, List<FingerprintedFile>> entry : REPO_FINGERPRINTS.entrySet()) {
            for (FingerprintedFile file : entry.getValue()) {
                double rawContainment = containment(candidateRaw, file.rawShingles());
                double structuralContainment = containment(
                    candidateStructural, file.structuralShingles());

                double score = rawContainment >= structuralContainment ? rawContainment : structuralContainment;
                double currentBest = best == null ? 0.0 : best.containment();

                if (score > currentBest) {
                    secondBest = currentBest;
                    best = new Match(entry.getKey(), file.relativePath(), score);
                } else if (score > secondBest) {
                    secondBest = score;
                }

                if (structuralContainment >= BLOCK_CONTAINMENT_STRUCTURAL
                    && (structuralBest == null || structuralContainment > structuralBest.containment())) {
                    structuralBest = new Match(entry.getKey(), file.relativePath(), structuralContainment);
                }
            }
        }

        double threshold = fullWrite ? BLOCK_CONTAINMENT_WRITE : BLOCK_CONTAINMENT_EDIT;
        Match blocker = null;
        if (best != null && best.containment() >= threshold) {
            blocker = best;
        } else if (fullWrite && structuralBest != null && structuralBest.containment() >= BLOCK_CONTAINMENT_STRUCTURAL
            && (best == null || structuralBest.containment() > best.containment())) {
            blocker = structuralBest;
        }

        return buildVerdict(best, secondBest, blocker, fullWrite);
    }

    private static Verdict buildVerdict(Match best, double secondBest, Match blocker, boolean fullWrite) {
        if (blocker == null) {
            if (best != null && best.containment() >= WARN_CONTAINMENT) {
                return new Verdict(best, secondBest, false, null);
            }
            return null;
        }

        String mode = fullWrite ? "write_file" : "apply_edit";
        String message = "Bloqueado por deteccion de clonacion (" + mode + "): "
            + String.format(Locale.ROOT, "%.0f%%", blocker.containment() * 100)
            + " del contenido coincide con '" + blocker.repoFile()
            + "' del repo '" + blocker.repo() + "'. NO se permite replicar codigo del repo. "
            + "Escribi el archivo como implementacion propia del proyecto (convenciones, paquetes y "
            + "estructura existentes); integra con los modulos actuales, quita lo que el proyecto no "
            + "necesita y basate en los patrones de github_repo(action='learn') para generar la logica, "
            + "no para reutilizar el archivo.";
        if (secondBest >= WARN_CONTAINMENT) {
            message += " Otros archivos del repo tambien coinciden en grado alto: la adaptacion debe ser"
                + " global (estructura y flujo), no archivo por archivo.";
        }
        return new Verdict(blocker, secondBest, true, message);
    }

    private static double containment(Set<Long> candidate, Set<Long> target) {
        if (candidate.isEmpty() || target.isEmpty()) {
            return 0.0;
        }
        int shared = 0;
        for (Long shingle : candidate) {
            if (target.contains(shingle)) {
                shared++;
            }
        }
        return (double) shared / candidate.size();
    }

    private static Set<Long> shinglesOf(List<String> tokens, int size) {
        if (tokens.size() < size) {
            return Set.of();
        }
        Set<Long> out = new HashSet<>(Math.max(16, tokens.size() - size));
        for (int i = 0; i <= tokens.size() - size; i++) {
            out.add(hashShingle(tokens, i, size));
        }
        return out;
    }

    private static long hashShingle(List<String> tokens, int offset, int size) {
        long h = 1125899906842597L;
        for (int i = offset; i < offset + size; i++) {
            h = 31 * h + tokens.get(i).hashCode();
        }
        return h;
    }

    private static List<String> rawTokensOf(String content) {
        List<String> tokens = new ArrayList<>(content.length() / 5 + 8);
        Matcher matcher = TOKEN_PATTERN.matcher(content);
        while (matcher.find()) {
            tokens.add(matcher.group().toLowerCase(Locale.ROOT));
        }
        return tokens;
    }

    private static List<String> structuralTokensOf(List<String> rawTokens) {
        List<String> out = new ArrayList<>(rawTokens.size());
        for (String token : rawTokens) {
            if (KEYWORD_TOKENS.contains(token) || token.length() <= 2) {
                out.add(token);
            } else {
                out.add("id");
            }
        }
        return out;
    }

    private static Set<Long> meaningfulStructure(String relativePath, List<String> rawTokens) {
        List<String> structuralTokens = structuralTokensOf(rawTokens);
        if (!isMeaningfulStructure(structuralTokens)) {
            return Set.of();
        }
        return shinglesOf(structuralTokens, STRUCTURAL_SHINGLE_TOKENS);
    }

    private static boolean isMeaningfulStructure(List<String> structuralTokens) {
        int nonIdentifier = 0;
        Set<String> distinct = new HashSet<>();
        for (String token : structuralTokens) {
            if (!"id".equals(token)) {
                nonIdentifier++;
            }
            distinct.add(token);
        }
        return nonIdentifier >= 15 && distinct.size() >= 10;
    }

    private static void ensureFingerprints() {
        List<String> repos = com.boris.context.ContentRepository.listRepos();
        for (String name : repos) {
            String normalized = name.toLowerCase(Locale.ROOT).trim();
            if (REPO_FINGERPRINTS.containsKey(normalized)) {
                continue;
            }
            String cachePath = com.boris.context.ContentRepository.repoPath(name);
            if (cachePath == null) {
                continue;
            }
            Path repoDir = Path.of(cachePath).toAbsolutePath().normalize();
            if (!Files.isDirectory(repoDir)) {
                continue;
            }
            REPO_FINGERPRINTS.put(normalized, fingerprints(name, repoDir));
        }
    }

    private static List<FingerprintedFile> fingerprints(String repoName, Path repoDir) {
        List<FingerprintedFile> out = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(repoDir)) {
            for (Path file : (Iterable<Path>) walk::iterator) {
                if (out.size() >= MAX_FILES_PER_REPO) {
                    break;
                }
                if (!Files.isRegularFile(file)) {
                    continue;
                }
                Path relative = repoDir.relativize(file);
                if (isSkippable(relative)) {
                    continue;
                }
                try {
                    if (Files.size(file) > MAX_FILE_BYTES) {
                        continue;
                    }
                    String content = Files.readString(file, StandardCharsets.UTF_8);
                    List<String> raw = rawTokensOf(content);
                    if (raw.size() < RAW_SHINGLE_TOKENS * 4) {
                        continue;
                    }
                    out.add(new FingerprintedFile(relative.toString(),
                        shinglesOf(raw, RAW_SHINGLE_TOKENS),
                        meaningfulStructure(relative.toString(), raw)));
                } catch (IOException readError) {
                    throw new UncheckedIOException(
                        "No se pudo leer el archivo del repo '" + repoName + "': ", readError);
                }
            }
        } catch (IOException walkError) {
            throw new IllegalStateException("No se pudo recorrer el cache del repo '"
                + repoName + "': " + walkError.getMessage(), walkError);
        }
        return out;
    }

    private static boolean isSkippable(Path relativePath) {
        for (Path part : relativePath) {
            String segment = part.toString().toLowerCase(Locale.ROOT);
            if (SKIP_DIR_SEGMENTS.contains(segment)
                || SKIP_FILE_MARKERS.stream().anyMatch(segment::contains)) {
                return true;
            }
        }
        String name = relativePath.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        if (dot < 0 || dot == name.length() - 1) {
            return true;
        }
        String ext = name.substring(dot + 1);
        return ext.equals(SKIP_EXTENSION) || !CODE_EXTENSIONS.contains(ext);
    }
}
