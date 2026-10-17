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

    private static final Set<String> STANDARD_CLASSES = Set.of(
        "ArrayList", "HashMap", "HashSet", "LinkedList", "StringBuilder",
        "String", "Integer", "Long", "Double", "Float", "Boolean",
        "Thread", "Exception", "RuntimeException", "IOException",
        "File", "Path", "Date", "Random", "Arrays", "Collections",
        "List", "Map", "Set", "Queue", "Deque", "Optional",
        "Properties", "System", "Math", "Object", "Class",
        "Consumer", "Supplier", "Function", "Predicate", "Stream",
        // java.text
        "SimpleDateFormat", "DateFormat", "NumberFormat", "DecimalFormat",
        // java.util.concurrent
        "AtomicBoolean", "AtomicInteger", "AtomicLong", "AtomicReference",
        "ConcurrentHashMap", "CopyOnWriteArrayList", "ExecutorService",
        "Executors", "Future", "CompletableFuture", "CountDownLatch",
        // java.io
        "InputStream", "OutputStream", "Reader", "Writer",
        "BufferedReader", "BufferedWriter", "FileReader", "FileWriter",
        "PrintWriter", "StringReader", "StringWriter",
        // java.nio
        "ByteBuffer", "CharBuffer", "Paths", "Files",
        // java.net
        "URL", "URI", "Socket", "ServerSocket",
        // java.time
        "LocalDate", "LocalTime", "LocalDateTime", "Instant",
        "Duration", "Period", "ZonedDateTime", "DateTimeFormatter",
        // java.util.regex
        "Pattern", "Matcher",
        // Otros comunes
        "StringBuffer", "Throwable", "Error",
        "Comparable", "Comparator", "Iterator", "Iterable",
        "Runnable", "Callable", "Timer", "TimerTask"
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
                knownImports.add(importMatch.group(1));
            }
        }
    }

    public boolean isKnownClass(String className) {
        if (STANDARD_CLASSES.contains(className)) {
            return true;
        }
        return knownClasses.contains(className);
    }

    public boolean hasMethod(String className, String methodName) {
        if (STANDARD_CLASSES.contains(className)) {
            return true;
        }
        Set<String> methods = classToMethods.get(className);
        return methods != null && methods.contains(methodName);
    }

    public boolean hasConstructor(String className) {
        if (STANDARD_CLASSES.contains(className)) {
            return true;
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
        Set<String> all = new HashSet<>(STANDARD_CLASSES);
        all.addAll(knownClasses);
        return all;
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

    public boolean isEmpty() {
        return knownClasses.isEmpty();
    }

    public int classCount() {
        return knownClasses.size();
    }
}
