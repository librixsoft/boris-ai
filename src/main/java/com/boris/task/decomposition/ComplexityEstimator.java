package com.boris.task.decomposition;

import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;

/**
 * Opción 3: Complexity Gate
 * Estima la complejidad de una tarea antes de enviarla al LLM principal.
 * Puede usar heurísticas locales o una llamada rápida al LLM.
 */
public class ComplexityEstimator {

    private static final List<Pattern> COMPLEX_PATTERNS = List.of(
            Pattern.compile("(?i)(crear|create|build|construir).*(?:aplicación|application|sistema|system|proyecto|project)"),
            Pattern.compile("(?i)(migrar|migrate|refactor|refactorizar).*(?:todo|all|completo|entire)"),
            Pattern.compile("(?i)(implementar|implement).*(?:desde cero|from scratch|nuevo|new)"),
            Pattern.compile("(?i)(múltiples|multiple|varios|several).*(?:archivos|files|componentes|components)"),
            Pattern.compile("(?i)(integrar|integrate|conectar|connect).*(?:api|servicio|service|base de datos|database)"),
            Pattern.compile("(?i)(configurar|configure|setup).*(?:ci/cd|pipeline|deployment|kubernetes|docker)"),
            Pattern.compile("(?i)(analizar|analyze|revisar|review).*(?:todo el|all the|completo|entire)")
    );

    private static final List<Pattern> MEDIUM_PATTERNS = List.of(
            Pattern.compile("(?i)(añadir|add|agregar).*(?:funcionalidad|feature|característica)"),
            Pattern.compile("(?i)(modificar|modify|cambiar|change).*(?:varios|multiple|algunos|some)"),
            Pattern.compile("(?i)(crear|create).*(?:endpoint|api|servicio|service)"),
            Pattern.compile("(?i)(escribir|write).*(?:tests|pruebas)"),
            Pattern.compile("(?i)(actualizar|update).*(?:dependencias|dependencies|versión|version)")
    );

    private static final List<String> COMPLEXITY_KEYWORDS_HIGH = List.of(
            "arquitectura", "architecture", "diseño", "design", "sistema completo", "full system",
            "desde cero", "from scratch", "reestructurar", "restructure", "migración", "migration"
    );

    private static final List<String> COMPLEXITY_KEYWORDS_MEDIUM = List.of(
            "integrar", "integrate", "conectar", "connect", "endpoint", "api",
            "refactorizar", "refactor", "optimizar", "optimize"
    );

    private final Function<String, String> llmCaller;

    public ComplexityEstimator() {
        this.llmCaller = null;
    }

    public ComplexityEstimator(Function<String, String> llmCaller) {
        this.llmCaller = llmCaller;
    }

    /**
     * Estimación rápida usando heurísticas locales (sin LLM)
     */
    public TaskComplexity estimateLocal(String task) {
        if (task == null || task.isBlank()) {
            return TaskComplexity.SIMPLE;
        }

        String lower = task.toLowerCase();
        int score = 0;

        // Check complex patterns
        for (Pattern p : COMPLEX_PATTERNS) {
            if (p.matcher(task).find()) {
                score += 3;
            }
        }

        // Check medium patterns
        for (Pattern p : MEDIUM_PATTERNS) {
            if (p.matcher(task).find()) {
                score += 2;
            }
        }

        // Check keywords
        for (String kw : COMPLEXITY_KEYWORDS_HIGH) {
            if (lower.contains(kw)) score += 2;
        }
        for (String kw : COMPLEXITY_KEYWORDS_MEDIUM) {
            if (lower.contains(kw)) score += 1;
        }

        // Length heuristic (longer tasks tend to be more complex)
        int wordCount = task.split("\\s+").length;
        if (wordCount > 50) score += 2;
        else if (wordCount > 25) score += 1;

        // Check for lists/enumerations (multiple steps implied)
        if (task.contains("\n") || task.matches(".*\\d+\\..*") || task.contains("- ")) {
            score += 2;
        }

        // Determine complexity from score
        if (score >= 6) return TaskComplexity.VERY_COMPLEX;
        if (score >= 4) return TaskComplexity.COMPLEX;
        if (score >= 2) return TaskComplexity.MEDIUM;
        return TaskComplexity.SIMPLE;
    }

    /**
     * Estimación usando el LLM (más precisa pero más lenta)
     */
    public TaskComplexity estimateWithLlm(String task) {
        if (llmCaller == null) {
            return estimateLocal(task);
        }

        String prompt = buildComplexityPrompt(task);
        try {
            String response = llmCaller.apply(prompt);
            return parseComplexityResponse(response);
        } catch (Exception e) {
            return estimateLocal(task);
        }
    }

    /**
     * Estimación híbrida: usa heurísticas primero, LLM solo si es ambiguo
     */
    public TaskComplexity estimateHybrid(String task) {
        TaskComplexity local = estimateLocal(task);

        // Si es claramente simple o muy complejo, confiar en la heurística
        if (local == TaskComplexity.SIMPLE || local == TaskComplexity.VERY_COMPLEX) {
            return local;
        }

        // Para casos intermedios, consultar al LLM si está disponible
        if (llmCaller != null) {
            return estimateWithLlm(task);
        }

        return local;
    }

    /**
     * Genera el número estimado de pasos para una tarea
     */
    public int estimateSteps(String task, TaskComplexity complexity) {
        int baseSteps = switch (complexity) {
            case SIMPLE -> 1;
            case MEDIUM -> 3;
            case COMPLEX -> 6;
            case VERY_COMPLEX -> 10;
        };

        // Ajustar basado en indicadores específicos
        String lower = task.toLowerCase();
        if (lower.contains("test") || lower.contains("prueba")) baseSteps += 1;
        if (lower.contains("documentar") || lower.contains("document")) baseSteps += 1;
        if (lower.contains("revisar") || lower.contains("review")) baseSteps += 1;

        return Math.min(baseSteps, complexity.getMaxSteps());
    }

    private String buildComplexityPrompt(String task) {
        return """
            Analyze the following task and classify its complexity.
            Respond with ONLY one word: simple, medium, complex, or very_complex

            Criteria:
            - simple: Single action, one file, quick fix
            - medium: Multiple related actions, 2-5 files, clear scope
            - complex: Multiple systems/components, 5+ files, requires planning
            - very_complex: Architectural changes, new systems, extensive refactoring

            Task: %s

            Complexity:""".formatted(task);
    }

    private TaskComplexity parseComplexityResponse(String response) {
        if (response == null) return TaskComplexity.MEDIUM;
        String lower = response.toLowerCase().trim();

        if (lower.contains("very_complex") || lower.contains("very complex")) {
            return TaskComplexity.VERY_COMPLEX;
        }
        if (lower.contains("complex")) {
            return TaskComplexity.COMPLEX;
        }
        if (lower.contains("medium") || lower.contains("moderate")) {
            return TaskComplexity.MEDIUM;
        }
        if (lower.contains("simple") || lower.contains("trivial")) {
            return TaskComplexity.SIMPLE;
        }

        return TaskComplexity.MEDIUM;
    }

    /**
     * Resultado de estimación con metadata
     */
    public record EstimationResult(
            TaskComplexity complexity,
            int estimatedSteps,
            boolean requiresDecomposition,
            String reasoning
    ) {
        public static EstimationResult of(TaskComplexity complexity, int steps) {
            return new EstimationResult(
                    complexity,
                    steps,
                    complexity.requiresDecomposition(),
                    null
            );
        }

        public static EstimationResult of(TaskComplexity complexity, int steps, String reasoning) {
            return new EstimationResult(
                    complexity,
                    steps,
                    complexity.requiresDecomposition(),
                    reasoning
            );
        }
    }

    public EstimationResult estimate(String task) {
        TaskComplexity complexity = estimateHybrid(task);
        int steps = estimateSteps(task, complexity);
        return EstimationResult.of(complexity, steps);
    }
}
