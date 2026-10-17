package com.boris.edit;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Analiza una línea de código y extrae el contexto relevante del SymbolCache.
 * Enfoque "just-in-time": solo busca lo necesario para validar ESA línea.
 */
public class LineContextResolver {

    private final SymbolCache symbolCache;

    private static final Pattern CLASS_USAGE = Pattern.compile("\\b([A-Z][a-zA-Z0-9]+)\\b");
    private static final Pattern METHOD_CALL = Pattern.compile("\\.([a-z][a-zA-Z0-9]+)\\s*\\(");
    private static final Pattern CONSTRUCTOR_CALL = Pattern.compile("new\\s+([A-Z][a-zA-Z0-9]+)\\s*\\(([^)]*)\\)");
    private static final Pattern VARIABLE_TYPE = Pattern.compile("([A-Z][a-zA-Z0-9]+)\\s+[a-z][a-zA-Z0-9]*\\s*[=;]");

    public LineContextResolver(SymbolCache symbolCache) {
        this.symbolCache = symbolCache;
    }

    /**
     * Analiza una línea y devuelve el contexto mínimo necesario.
     */
    public LineContext resolve(String line) {
        Set<String> classesUsed = extractClasses(line);
        Map<String, MethodCall> methodCalls = extractMethodCalls(line);
        List<ConstructorCall> constructorCalls = extractConstructorCalls(line);

        LineContext context = new LineContext(line);

        for (String className : classesUsed) {
            if (symbolCache.isKnownClass(className)) {
                ClassInfo info = new ClassInfo(className);
                info.methods.addAll(symbolCache.getMethodsForClass(className));
                info.constructors.addAll(symbolCache.getConstructorsForClass(className));
                context.knownClasses.put(className, info);
            } else {
                context.unknownClasses.add(className);
                List<String> similar = symbolCache.findSimilarClasses(className);
                if (!similar.isEmpty()) {
                    context.suggestions.put(className, similar);
                }
            }
        }

        for (Map.Entry<String, MethodCall> entry : methodCalls.entrySet()) {
            String methodName = entry.getKey();
            MethodCall call = entry.getValue();

            if (call.targetClass != null && symbolCache.isKnownClass(call.targetClass)) {
                if (!symbolCache.hasMethod(call.targetClass, methodName)) {
                    context.unknownMethods.add(call.targetClass + "." + methodName);
                    Set<String> availableMethods = symbolCache.getMethodsForClass(call.targetClass);
                    context.methodSuggestions.put(call.targetClass + "." + methodName, new ArrayList<>(availableMethods));
                }
            }
        }

        for (ConstructorCall call : constructorCalls) {
            if (symbolCache.isKnownClass(call.className)) {
                List<String> availableConstructors = symbolCache.getConstructorsForClass(call.className);
                if (!matchesAnyConstructor(call.args, availableConstructors)) {
                    context.invalidConstructors.add(call);
                    context.constructorSuggestions.put(call.className, availableConstructors);
                }
            }
        }

        return context;
    }

    /**
     * Genera el prompt de corrección con el contexto justo para esta línea.
     */
    public String buildCorrectionPrompt(LineContext context) {
        StringBuilder sb = new StringBuilder();

        sb.append("=== CORRIGE ESTA LÍNEA ===\n\n");
        sb.append("Línea original:\n```\n").append(context.originalLine).append("\n```\n\n");

        if (!context.unknownClasses.isEmpty()) {
            sb.append("ERRORES ENCONTRADOS:\n");
            for (String unknown : context.unknownClasses) {
                sb.append("- Clase '").append(unknown).append("' NO EXISTE\n");
                if (context.suggestions.containsKey(unknown)) {
                    sb.append("  Alternativas: ").append(String.join(", ", context.suggestions.get(unknown))).append("\n");
                }
            }
            sb.append("\n");
        }

        if (!context.unknownMethods.isEmpty()) {
            sb.append("MÉTODOS NO EXISTENTES:\n");
            for (String unknown : context.unknownMethods) {
                sb.append("- '").append(unknown).append("' NO EXISTE\n");
                if (context.methodSuggestions.containsKey(unknown)) {
                    sb.append("  Métodos disponibles: ").append(String.join(", ", context.methodSuggestions.get(unknown))).append("\n");
                }
            }
            sb.append("\n");
        }

        if (!context.invalidConstructors.isEmpty()) {
            sb.append("CONSTRUCTORES INVÁLIDOS:\n");
            for (ConstructorCall call : context.invalidConstructors) {
                sb.append("- new ").append(call.className).append("(").append(call.args).append(") NO EXISTE\n");
                if (context.constructorSuggestions.containsKey(call.className)) {
                    List<String> ctors = context.constructorSuggestions.get(call.className);
                    sb.append("  Constructores disponibles:\n");
                    for (String ctor : ctors) {
                        sb.append("    - ").append(call.className).append("(").append(ctor).append(")\n");
                    }
                }
            }
            sb.append("\n");
        }

        if (!context.knownClasses.isEmpty()) {
            sb.append("API DISPONIBLE:\n");
            for (Map.Entry<String, ClassInfo> entry : context.knownClasses.entrySet()) {
                ClassInfo info = entry.getValue();
                sb.append("\n### ").append(entry.getKey()).append("\n");
                if (!info.constructors.isEmpty()) {
                    sb.append("Constructores:\n");
                    for (String ctor : info.constructors) {
                        sb.append("  - ").append(entry.getKey()).append("(").append(ctor).append(")\n");
                    }
                }
                if (!info.methods.isEmpty()) {
                    sb.append("Métodos:\n");
                    for (String method : info.methods) {
                        sb.append("  - ").append(method).append("()\n");
                    }
                }
            }
            sb.append("\n");
        }

        sb.append("=== INSTRUCCIÓN ===\n");
        sb.append("Responde ÚNICAMENTE con la línea corregida, sin explicaciones.\n");
        sb.append("Si la línea no se puede corregir, responde: // ERROR: <razón>\n");

        return sb.toString();
    }

    private Set<String> extractClasses(String line) {
        Set<String> classes = new HashSet<>();

        // Ignorar declaraciones de clase (class X, interface X, enum X)
        // Estas son clases NUEVAS que el agente está creando, no usando
        if (line.matches(".*\\b(class|interface|enum)\\s+[A-Z][a-zA-Z0-9]+.*")) {
            return classes; // No validar declaraciones de clase
        }

        Matcher matcher = CLASS_USAGE.matcher(line);
        while (matcher.find()) {
            String className = matcher.group(1);
            if (!isJavaKeyword(className) && !isPrimitive(className)) {
                classes.add(className);
            }
        }
        return classes;
    }

    private Map<String, MethodCall> extractMethodCalls(String line) {
        Map<String, MethodCall> calls = new HashMap<>();
        Matcher matcher = METHOD_CALL.matcher(line);
        while (matcher.find()) {
            String methodName = matcher.group(1);
            MethodCall call = new MethodCall();
            call.methodName = methodName;
            call.targetClass = inferTargetClass(line, matcher.start());
            calls.put(methodName, call);
        }
        return calls;
    }

    private List<ConstructorCall> extractConstructorCalls(String line) {
        List<ConstructorCall> calls = new ArrayList<>();
        Matcher matcher = CONSTRUCTOR_CALL.matcher(line);
        while (matcher.find()) {
            String className = matcher.group(1);
            // Skip standard Java classes
            if (isJavaKeyword(className)) {
                continue;
            }
            ConstructorCall call = new ConstructorCall();
            call.className = className;
            call.args = matcher.group(2).trim();
            calls.add(call);
        }
        return calls;
    }

    private String inferTargetClass(String line, int methodPosition) {
        String before = line.substring(0, methodPosition);
        Matcher varMatcher = Pattern.compile("([a-z][a-zA-Z0-9]*)\\s*$").matcher(before);
        if (varMatcher.find()) {
            String varName = varMatcher.group(1);
            Matcher typeMatcher = Pattern.compile("([A-Z][a-zA-Z0-9]+)\\s+" + varName + "\\s*=").matcher(line);
            if (typeMatcher.find()) {
                return typeMatcher.group(1);
            }
        }
        return null;
    }

    private boolean matchesAnyConstructor(String args, List<String> availableConstructors) {
        args = args.trim();

        // Empty args matches empty constructor
        if (args.isEmpty()) {
            return availableConstructors.contains("") || availableConstructors.stream().anyMatch(String::isEmpty);
        }

        // Check if args look like a number (int literal)
        boolean argsIsNumeric = args.matches("\\d+");
        // Check if args look like a string literal
        boolean argsIsString = args.startsWith("\"") && args.endsWith("\"");

        for (String ctor : availableConstructors) {
            if (ctor == null || ctor.isEmpty()) continue;

            String ctorLower = ctor.toLowerCase();

            // If args is numeric, constructor must accept int/long/number
            if (argsIsNumeric) {
                if (ctorLower.contains("int") || ctorLower.contains("long") ||
                    ctorLower.contains("number") || ctorLower.contains("size") ||
                    ctorLower.contains("count") || ctorLower.contains("capacity")) {
                    return true;
                }
            }
            // If args is string literal, constructor must accept String
            else if (argsIsString) {
                if (ctorLower.contains("string") || ctorLower.contains("text") ||
                    ctorLower.contains("label") || ctorLower.contains("name") ||
                    ctorLower.contains("title") || ctorLower.contains("initial")) {
                    return true;
                }
            }
            // For other cases, just check arg count
            else {
                int argCount = args.split(",").length;
                int ctorArgCount = ctor.split(",").length;
                if (ctorArgCount == argCount) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isJavaKeyword(String word) {
        return Set.of(
            // Wrapper types
            "String", "Integer", "Long", "Double", "Float", "Boolean", "Character", "Byte", "Short",
            // Core types
            "Object", "Class", "Void", "Exception", "Error", "Throwable", "RuntimeException",
            // Annotations
            "Override", "Deprecated", "SuppressWarnings", "FunctionalInterface",
            // Collections
            "List", "Map", "Set", "Collection", "ArrayList", "HashMap", "HashSet", "LinkedList",
            // Functional
            "Consumer", "Supplier", "Function", "Predicate", "BiFunction", "BiConsumer",
            // IO
            "InputStream", "OutputStream", "Reader", "Writer", "File", "Path",
            // Other common
            "Optional", "Stream", "Arrays", "Collections", "System", "Math", "StringBuilder"
        ).contains(word);
    }

    private boolean isPrimitive(String word) {
        return Set.of("int", "long", "double", "float", "boolean", "char", "byte", "short", "void").contains(word);
    }

    public static class LineContext {
        public final String originalLine;
        public final Map<String, ClassInfo> knownClasses = new LinkedHashMap<>();
        public final Set<String> unknownClasses = new LinkedHashSet<>();
        public final Set<String> unknownMethods = new LinkedHashSet<>();
        public final List<ConstructorCall> invalidConstructors = new ArrayList<>();
        public final Map<String, List<String>> suggestions = new HashMap<>();
        public final Map<String, List<String>> methodSuggestions = new HashMap<>();
        public final Map<String, List<String>> constructorSuggestions = new HashMap<>();

        public LineContext(String originalLine) {
            this.originalLine = originalLine;
        }

        public boolean hasErrors() {
            return !unknownClasses.isEmpty() || !unknownMethods.isEmpty() || !invalidConstructors.isEmpty();
        }

        public boolean isValid() {
            return !hasErrors();
        }
    }

    public static class ClassInfo {
        public final String name;
        public final Set<String> methods = new LinkedHashSet<>();
        public final List<String> constructors = new ArrayList<>();

        public ClassInfo(String name) {
            this.name = name;
        }
    }

    public static class MethodCall {
        public String methodName;
        public String targetClass;
    }

    public static class ConstructorCall {
        public String className;
        public String args;
    }
}
