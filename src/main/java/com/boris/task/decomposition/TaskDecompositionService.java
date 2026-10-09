package com.boris.task.decomposition;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Servicio principal que orquesta las 4 opciones de descomposición de tareas.
 * Integra: ComplexityEstimator, TaskPlannerTool, y AgenticExecutor.
 */
public class TaskDecompositionService {

    public enum DecompositionMode {
        DISABLED,           // No decomposition, direct execution
        PROMPT_ONLY,        // Option 1: Let the LLM handle via system prompt
        TOOL_BASED,         // Option 2: LLM calls plan_task tool explicitly
        COMPLEXITY_GATE,    // Option 3: Auto-detect and decompose complex tasks
        AGENTIC_LOOP        // Option 4: Full autonomous execution with checkpoints
    }

    public record DecompositionConfig(
            DecompositionMode mode,
            boolean autoDecompose,
            int complexityThreshold,
            boolean requireConfirmation,
            int checkpointInterval,
            int maxMicrotasks
    ) {
        public static DecompositionConfig defaults() {
            return new DecompositionConfig(
                    DecompositionMode.COMPLEXITY_GATE,
                    true,
                    4,  // complexity score threshold
                    false,
                    3,  // checkpoint every 3 tasks
                    10
            );
        }

        public static DecompositionConfig promptOnly() {
            return new DecompositionConfig(
                    DecompositionMode.PROMPT_ONLY,
                    false, 0, false, 0, 0
            );
        }

        public static DecompositionConfig toolBased() {
            return new DecompositionConfig(
                    DecompositionMode.TOOL_BASED,
                    false, 0, false, 0, 10
            );
        }

        public static DecompositionConfig agenticLoop() {
            return new DecompositionConfig(
                    DecompositionMode.AGENTIC_LOOP,
                    true,
                    4,
                    true,
                    3,
                    15
            );
        }
    }

    public record ProcessingResult(
            String originalTask,
            TaskComplexity complexity,
            boolean decomposed,
            TaskPlan plan,
            String response
    ) {
        public static ProcessingResult simple(String task, String response) {
            return new ProcessingResult(task, TaskComplexity.SIMPLE, false, null, response);
        }

        public static ProcessingResult withPlan(String task, TaskPlan plan, String response) {
            return new ProcessingResult(task, plan.getComplexity(), true, plan, response);
        }
    }

    private final ComplexityEstimator estimator;
    private final TaskPlannerTool plannerTool;
    private final Function<String, String> llmCaller;
    private final Consumer<String> progressCallback;
    private final DecompositionConfig config;
    private final ConcurrentHashMap<String, TaskPlan> activePlans;
    private final AtomicBoolean processing;

    public TaskDecompositionService(Function<String, String> llmCaller) {
        this(llmCaller, System.out::println, DecompositionConfig.defaults());
    }

    public TaskDecompositionService(Function<String, String> llmCaller, Consumer<String> progressCallback) {
        this(llmCaller, progressCallback, DecompositionConfig.defaults());
    }

    public TaskDecompositionService(Function<String, String> llmCaller,
                                    Consumer<String> progressCallback,
                                    DecompositionConfig config) {
        this.llmCaller = llmCaller;
        this.progressCallback = progressCallback;
        this.config = config;
        this.estimator = new ComplexityEstimator(llmCaller);
        this.plannerTool = new TaskPlannerTool(llmCaller);
        this.activePlans = new ConcurrentHashMap<>();
        this.processing = new AtomicBoolean(false);
    }

    /**
     * Procesa una tarea aplicando el modo de descomposición configurado
     */
    public ProcessingResult process(String task) {
        if (task == null || task.isBlank()) {
            return ProcessingResult.simple(task, "Empty task");
        }

        return switch (config.mode()) {
            case DISABLED -> processDirectly(task);
            case PROMPT_ONLY -> processWithPromptHint(task);
            case TOOL_BASED -> processWithToolHint(task);
            case COMPLEXITY_GATE -> processWithComplexityGate(task);
            case AGENTIC_LOOP -> processWithAgenticLoop(task);
        };
    }

    /**
     * Opción 1: Ejecución directa sin descomposición
     */
    private ProcessingResult processDirectly(String task) {
        String response = llmCaller.apply(task);
        return ProcessingResult.simple(task, response);
    }

    /**
     * Opción 1: Añade hint al prompt para que el LLM decida
     */
    private ProcessingResult processWithPromptHint(String task) {
        String enhancedTask = enhanceWithDecompositionHint(task);
        String response = llmCaller.apply(enhancedTask);
        return ProcessingResult.simple(task, response);
    }

    /**
     * Opción 2: Sugiere usar el tool de planning
     */
    private ProcessingResult processWithToolHint(String task) {
        ComplexityEstimator.EstimationResult estimation = estimator.estimate(task);

        if (estimation.requiresDecomposition()) {
            String hint = String.format(
                    "[System: This task appears %s. Consider using plan_task tool to decompose it.]\n\n%s",
                    estimation.complexity().getLabel(), task
            );
            String response = llmCaller.apply(hint);
            return ProcessingResult.simple(task, response);
        }

        return processDirectly(task);
    }

    /**
     * Opción 3: Auto-detecta complejidad y descompone si es necesario
     */
    private ProcessingResult processWithComplexityGate(String task) {
        processing.set(true);
        try {
            // Estimate complexity
            ComplexityEstimator.EstimationResult estimation = estimator.estimate(task);
            progressCallback.accept("📊 Complejidad estimada: " + estimation.complexity().getLabel());

            if (!estimation.requiresDecomposition()) {
                // Simple task - execute directly
                String response = llmCaller.apply(task);
                return ProcessingResult.simple(task, response);
            }

            // Complex task - generate plan
            progressCallback.accept("🔧 Generando plan de ejecución...");
            TaskPlan plan = generatePlan(task, estimation);
            activePlans.put(plan.getId(), plan);

            progressCallback.accept(plan.toSummary());

            // Execute plan step by step
            StringBuilder fullResponse = new StringBuilder();
            fullResponse.append("📋 **Plan de ejecución generado**\n\n");

            for (Microtask microtask : plan.getMicrotasks()) {
                if (!processing.get()) {
                    plan.setStatus(TaskPlan.PlanStatus.ABORTED);
                    break;
                }

                microtask.markInProgress();
                progressCallback.accept("▶ Ejecutando: " + microtask.toProgressString());

                String stepPrompt = buildStepPrompt(task, plan, microtask);
                String stepResult = llmCaller.apply(stepPrompt);

                microtask.markCompleted(stepResult);
                fullResponse.append(String.format("### Paso %d: %s\n%s\n\n",
                        microtask.getOrder(), microtask.getDescription(), stepResult));

                progressCallback.accept("✓ " + microtask.toProgressString());
            }

            plan.setStatus(TaskPlan.PlanStatus.COMPLETED);
            return ProcessingResult.withPlan(task, plan, fullResponse.toString());

        } finally {
            processing.set(false);
        }
    }

    /**
     * Opción 4: Loop agéntico completo con checkpoints
     */
    private ProcessingResult processWithAgenticLoop(String task) {
        processing.set(true);
        try {
            ComplexityEstimator.EstimationResult estimation = estimator.estimate(task);
            progressCallback.accept("📊 Análisis: " + estimation.complexity().getLabel() +
                    " (~" + estimation.estimatedSteps() + " pasos)");

            if (!estimation.requiresDecomposition()) {
                String response = llmCaller.apply(task);
                return ProcessingResult.simple(task, response);
            }

            // Create plan
            TaskPlan plan = generatePlan(task, estimation);
            plan.setRequiresConfirmation(config.requireConfirmation());
            activePlans.put(plan.getId(), plan);

            // Build executor
            AgenticExecutor executor = AgenticExecutor.builder()
                    .taskExecutor(llmCaller)
                    .logger(progressCallback)
                    .confirmationProvider(() -> !config.requireConfirmation() || requestConfirmation())
                    .build();

            // Execute
            AgenticExecutor.ExecutionConfig execConfig = new AgenticExecutor.ExecutionConfig(
                    config.requireConfirmation(),
                    config.checkpointInterval(),
                    2,
                    300_000,
                    false
            );

            AgenticExecutor.ExecutionResult result = executor.execute(plan, execConfig);

            // Build response from results
            StringBuilder response = new StringBuilder();
            response.append("# Resultado de ejecución\n\n");
            response.append("**Estado:** ").append(result.success() ? "✅ Completado" : "❌ Fallido").append("\n");
            response.append("**Resumen:** ").append(result.summary()).append("\n\n");

            for (AgenticExecutor.MicrotaskResult mr : result.results()) {
                response.append(String.format("## Paso %d: %s\n",
                        mr.task().getOrder(), mr.task().getDescription()));
                response.append(mr.success() ? "✓ " : "✗ ");
                response.append(mr.output()).append("\n\n");
            }

            return ProcessingResult.withPlan(task, plan, response.toString());

        } finally {
            processing.set(false);
        }
    }

    private TaskPlan generatePlan(String task, ComplexityEstimator.EstimationResult estimation) {
        TaskPlan plan = new TaskPlan(task, estimation.complexity());

        // Use the planner tool to generate microtasks
        String planJson = plannerTool.executePlanTask(java.util.Map.of(
                "task_description", task,
                "max_steps", config.maxMicrotasks()
        ));

        // Parse the generated tasks from JSON response
        try {
            if (planJson.contains("\"microtasks\"")) {
                int start = planJson.indexOf("[", planJson.indexOf("microtasks"));
                int end = planJson.indexOf("]", start) + 1;
                if (start > 0 && end > start) {
                    String tasksJson = planJson.substring(start, end);
                    // Simple parsing - extract descriptions
                    java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("\"description\"\\s*:\\s*\"([^\"]+)\"");
                    java.util.regex.Matcher matcher = pattern.matcher(tasksJson);
                    while (matcher.find()) {
                        plan.addMicrotask(matcher.group(1));
                    }
                }
            }
        } catch (Exception e) {
            // Fallback: add generic tasks
            plan.addMicrotask("Analyze and understand the task");
            plan.addMicrotask("Execute main implementation");
            plan.addMicrotask("Verify and test results");
        }

        return plan;
    }

    private String enhanceWithDecompositionHint(String task) {
        return """
            [TASK DECOMPOSITION GUIDANCE]
            Before executing, analyze this task's complexity:
            - If simple (1-2 steps): Execute directly
            - If medium (3-5 steps): List steps briefly, then execute
            - If complex (6+ steps): Create a numbered plan, execute step by step, report progress

            [TASK]
            %s
            """.formatted(task);
    }

    private String buildStepPrompt(String originalTask, TaskPlan plan, Microtask currentTask) {
        return """
            You are executing step %d of %d in a planned task.

            Original task: %s

            Current step: %s

            Previous completed steps: %d
            Remaining steps: %d

            Instructions:
            - Focus ONLY on this specific step
            - Use appropriate tools to complete it
            - Report what was accomplished

            Execute this step now:
            """.formatted(
                currentTask.getOrder(),
                plan.getTotalTasks(),
                originalTask,
                currentTask.getDescription(),
                currentTask.getOrder() - 1,
                plan.getTotalTasks() - currentTask.getOrder()
        );
    }

    private boolean requestConfirmation() {
        progressCallback.accept("⏸ Esperando confirmación para continuar...");
        // In real implementation, this would wait for user input
        // For now, auto-confirm after logging
        return true;
    }

    // ========== Public API ==========

    public void abort() {
        processing.set(false);
    }

    public boolean isProcessing() {
        return processing.get();
    }

    public Optional<TaskPlan> getActivePlan(String planId) {
        return Optional.ofNullable(activePlans.get(planId));
    }

    public DecompositionConfig getConfig() {
        return config;
    }

    /**
     * Estima la complejidad sin ejecutar
     */
    public ComplexityEstimator.EstimationResult estimateComplexity(String task) {
        return estimator.estimate(task);
    }

    /**
     * Crea un plan sin ejecutar
     */
    public TaskPlan createPlan(String task) {
        ComplexityEstimator.EstimationResult estimation = estimator.estimate(task);
        return generatePlan(task, estimation);
    }
}
