package com.boris.tooling;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.boris.context.CodeIndexerService;
import com.boris.context.CodeSymbol;
import com.boris.context.SymbolRepository;
import com.boris.knowledge.KnowledgeStorageService;

public final class EditGateService {

    private record Candidate(String className, String pkg) {}

    private static final Pattern IMPORT_PATTERN =
        Pattern.compile("\\bimport\\s+(?:static\\s+)?([a-zA-Z_][\\w.]*);");
    private static final Pattern NEW_PATTERN =
        Pattern.compile("\\bnew\\s+([A-Z][A-Za-z0-9_]*)");
    private static final Pattern QUALIFIED_PATTERN =
        Pattern.compile("\\b([A-Z][A-Za-z0-9_]*)\\.[A-Z][A-Za-z0-9_]*");

    private static final Set<String> JDK_PACKAGE_PREFIXES =
        Set.of("java.", "javax.", "jdk.", "sun.", "com.sun.", "org.w3c.", "org.xml.");

    private static final Set<String> JDK_CLASSES = Set.of(
        "Exception", "RuntimeException", "Error", "Throwable", "IllegalArgumentException",
        "IllegalStateException", "NullPointerException", "UnsupportedOperationException",
        "IOException", "InterruptedException", "IndexOutOfBoundsException", "ArithmeticException",
        "String", "StringBuilder", "StringBuffer", "Integer", "Long", "Double", "Float", "Boolean",
        "Byte", "Short", "Character", "Object", "System", "Math", "Thread", "Runnable", "Number",
        "Comparable", "Iterable", "AutoCloseable", "Cloneable", "Record", "Enum", "Optional",
        "List", "Map", "Set", "Queue", "Deque", "Collection", "Iterator", "Entry", "Arrays",
        "Collections", "ArrayList", "HashMap", "HashSet", "LinkedHashMap", "LinkedHashSet",
        "TreeMap", "TreeSet", "ArrayDeque", "LinkedList", "Objects", "Stream", "Collectors",
        "Function", "Supplier", "Consumer", "Predicate", "BiFunction", "BiConsumer", "Runnable2",
        "Files", "Paths", "Path", "Charset", "StandardCharsets", "InputStream", "OutputStream",
        "Reader", "Writer", "Scanner", "Random", "UUID", "BigDecimal", "BigInteger",
        "LocalDate", "LocalDateTime", "LocalTime", "Duration", "Instant", "Clock", "ZoneId",
        "Pattern", "Matcher", "ExecutorService", "Executors", "CompletableFuture", "Future",
        "CountDownLatch", "AtomicInteger", "AtomicLong", "AtomicBoolean", "volatile");

    private static final Set<String> COMMON_PACKAGE_WORDS =
        Set.of("dev", "com", "org", "io", "net", "app", "de", "es", "fr", "uk", "edu", "gov",
            "info", "name", "tools", "ai", "main", "java", "javax", "kotlin", "src", "test");

    private static final Set<String> AUTO_INDEXING_IN_PROGRESS =
        ConcurrentHashMap.newKeySet();

    private EditGateService() {}

    public static String checkEditAllowed(String path, String involvedText) {
        if (LearningLimiter.allowEdit()) {
            return null;
        }

        LinkedHashSet<Candidate> candidates = extractCandidates(path, involvedText);
        if (candidates.isEmpty()) {
            return null;
        }

        LinkedHashSet<Candidate> unknown = classifyUnknown(candidates);
        if (unknown.isEmpty()) {
            LearningLimiter.markDocumented();
            return null;
        }

        autoIndexFromCachedRepos(unknown);

        LinkedHashSet<Candidate> stillUnknown = classifyUnknown(unknown);
        if (stillUnknown.isEmpty()) {
            LearningLimiter.markDocumented();
            return null;
        }

        if (LearningLimiter.wasUnknownLibraryRedirectIssued()) {
            LearningLimiter.markDocumented();
            return null;
        }
        LearningLimiter.markUnknownLibraryRedirectIssued();

        return buildRedirectMessage(stillUnknown);
    }

    private static LinkedHashSet<Candidate> extractCandidates(String path, String involvedText) {
        LinkedHashSet<Candidate> out = new LinkedHashSet<>();
        String text = involvedText != null ? involvedText : "";
        if (path != null) {
            text = path + "\n" + text;
        }

        boolean hasImports = false;
        Matcher importMatcher = IMPORT_PATTERN.matcher(text);
        while (importMatcher.find()) {
            hasImports = true;
            Candidate candidate = toCandidate(importMatcher.group(1));
            if (candidate != null) {
                out.add(candidate);
            }
        }
        if (hasImports) {
            return out;
        }

        Matcher newMatcher = NEW_PATTERN.matcher(text);
        while (newMatcher.find()) {
            String className = newMatcher.group(1);
            if (!JDK_CLASSES.contains(className)) {
                out.add(new Candidate(className, null));
            }
        }
        Matcher qualifiedMatcher = QUALIFIED_PATTERN.matcher(text);
        while (qualifiedMatcher.find()) {
            String className = qualifiedMatcher.group(1);
            if (!JDK_CLASSES.contains(className)) {
                out.add(new Candidate(className, null));
            }
        }
        return out;
    }

    private static Candidate toCandidate(String qualifiedName) {
        String[] segments = qualifiedName.split("\\.");
        String simple = segments[segments.length - 1];
        String pkg = segments.length > 1
            ? qualifiedName.substring(0, qualifiedName.length() - simple.length() - 1)
            : null;

        if ("*".equals(simple) || simple.isBlank()) {
            return null;
        }
        if (pkg != null) {
            for (String prefix : JDK_PACKAGE_PREFIXES) {
                if (pkg.startsWith(prefix) || pkg.equals(prefix.substring(0, prefix.length() - 1))) {
                    return null;
                }
            }
        }
        if (Character.isLowerCase(simple.charAt(0))) {
            boolean staticImportWithMember = segments.length >= 2
                && Character.isUpperCase(segments[segments.length - 2].charAt(0));
            if (!staticImportWithMember) {
                return null;
            }
            String classSimple = segments[segments.length - 2];
            String classPkg = qualifiedName.substring(0,
                qualifiedName.length() - classSimple.length() - simple.length() - 1);
            return new Candidate(classSimple, classPkg);
        }
        return new Candidate(simple, pkg);
    }

    private static LinkedHashSet<Candidate> classifyUnknown(LinkedHashSet<Candidate> candidates) {
        LinkedHashSet<Candidate> unknown = new LinkedHashSet<>();
        for (Candidate c : candidates) {
            if (JDK_CLASSES.contains(c.className())) {
                continue;
            }
            if (!isKnown(c.className())) {
                unknown.add(c);
            }
        }
        return unknown;
    }

    private static boolean isKnown(String className) {
        List<CodeSymbol> symbols = SymbolRepository.findExact(className);
        for (CodeSymbol s : symbols) {
            String name = s.getName();
            String parent = s.getParent();
            if (name.equalsIgnoreCase(className)
                || (parent != null && (parent.equalsIgnoreCase(className)
                    || parent.toLowerCase(Locale.ROOT).endsWith("." + className.toLowerCase(Locale.ROOT))))) {
                return true;
            }
        }
        Path projectRoot = KnowledgeStorageService.resolveProjectRoot(null);
        for (String framework : KnowledgeStorageService.listFrameworks(projectRoot)) {
            if (KnowledgeStorageService.hasKnowledge(projectRoot, framework, className)) {
                return true;
            }
        }
        return false;
    }

    private static void autoIndexFromCachedRepos(LinkedHashSet<Candidate> unknown) {
        Set<String> packageWords = new LinkedHashSet<>();
        for (Candidate c : unknown) {
            if (c.pkg() == null) {
                continue;
            }
            for (String segment : c.pkg().split("\\.")) {
                if (!segment.isBlank() && !COMMON_PACKAGE_WORDS.contains(segment.toLowerCase(Locale.ROOT))) {
                    packageWords.add(segment);
                }
            }
        }
        if (packageWords.isEmpty()) {
            return;
        }

        Path projectRoot = KnowledgeStorageService.resolveProjectRoot(null);
        Path reposDir = projectRoot.resolve(".boris").resolve("repos");
        if (!Files.isDirectory(reposDir)) {
            return;
        }

        try (var stream = Files.list(reposDir)) {
            for (Path repoDir : (Iterable<Path>) stream.filter(Files::isDirectory)::iterator) {
                String repoName = repoDir.getFileName().toString();
                if (!matchesPackage(repoName, packageWords)) {
                    continue;
                }
                if (!AUTO_INDEXING_IN_PROGRESS.add(repoName.toLowerCase(Locale.ROOT))) {
                    continue;
                }
                try {
                    if (!SymbolRepository.isIndexed(repoName)) {
                        ToolProgressNotifier.notify("create_library_context",
                            "Indexando " + repoName + " desde cache .boris/repos...");
                        CodeIndexerService.indexRepository(repoName, repoDir);
                    }
                } catch (Exception ignored) {
                } finally {
                    AUTO_INDEXING_IN_PROGRESS.remove(repoName.toLowerCase(Locale.ROOT));
                }
            }
        } catch (Exception ignored) {}
    }

    private static boolean matchesPackage(String repoName, Set<String> packageWords) {
        String normalized = repoName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
        for (String word : packageWords) {
            String w = word.toLowerCase(Locale.ROOT);
            if (normalized.equals(w) || normalized.contains(w)
                || (w.startsWith("tui") && normalized.contains("tui"))) {
                return true;
            }
        }
        return false;
    }

    private static String buildRedirectMessage(LinkedHashSet<Candidate> unknown) {
        StringBuilder classes = new StringBuilder();
        for (Candidate c : unknown) {
            if (classes.length() > 0) {
                classes.append(", ");
            }
            classes.append(c.className());
            if (c.pkg() != null) {
                classes.append(" (").append(c.pkg()).append(")");
            }
        }
        Path projectRoot = KnowledgeStorageService.resolveProjectRoot(null);
        return "Hay errores de compilación sin corregir y las clases/librerías involucradas no están "
            + "indexadas ni tienen knowledge card: " + classes + ". "
            + "Dispara UNA SOLA VEZ create_library_context(repo='owner/repo', version='main' o el tag correcto, "
            + "project_path='" + projectRoot + "') para indexar la librería (cache en .boris/repos), "
            + "y luego query_symbols('Clase') para ver las signaturas o query_framework_knowledge si hay ficha. "
            + "NO uses list_files ni explores el disco para documentarte.";
    }
}
