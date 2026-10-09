package com.boris.task.decomposition;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.boris.tooling.ToolDefinition;

/**
 * Opción 2: Tool de Planning dedicado
 * El LLM puede invocar este tool para descomponer tareas complejas en microtasks.
 */
public class TaskPlannerTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Map<String, TaskPlan> activePlans = new ConcurrentHashMap<>();

    private final ComplexityEstimator estimator;
    private final Function<String, String> llmCaller;

    public TaskPlannerTool() {
        this.estimator = new ComplexityEstimator();
        this.llmCaller = null;
    }

    public TaskPlannerTool(Function<String, String> llmCaller) {
        this.estimator = new ComplexityEstimator(llmCaller);
        this.llmCaller = llmCaller;
    }

    // ========== Tool Definitions ==========

    public ToolDefinition plan_task() {
        var props = new LinkedHashMap<String, Object>();
        props.put("task_description", Map.of(
                "type", "string",
                "description", "The task to analyze and decompose into microtasks"
        ));
        props.put("max_steps", Map.of(
                "type", "integer",
                "description", "Maximum number of microtasks to generate (default: 10)"
        ));
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("task_description"));
        return ToolDefinition.of(
                "plan_task",
                "Analyze a task and decompose it into ordered microtasks. Use this BEFORE starting complex tasks that involve multiple files or steps.",
                schema
        );
    }

    public ToolDefinition get_plan() {
        var props = new LinkedHashMap<String, Object>();
        props.put("plan_id", Map.of(
                "type", "string",
                "description", "The ID of the plan to retrieve"
        ));
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("plan_id"));
        return ToolDefinition.of(
                "get_plan",
                "Get the current status and progress of a task plan",
                schema
        );
    }

    public ToolDefinition complete_microtask() {
        var props = new LinkedHashMap<String, Object>();
        props.put("plan_id", Map.of(
                "type", "string",
                "description", "The ID of the plan"
        ));
        props.put("task_order", Map.of(
                "type", "integer",
                "description", "The order number of the completed microtask"
        ));
        props.put("result", Map.of(
                "type", "string",
                "description", "Summary of what was accomplished"
        ));
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", props);
        schema.put("required", List.of("plan_id", "task_order"));
        return ToolDefinition.of(
                "complete_microtask",
                "Mark a microtask as completed and get the next task to execute",
                schema
        );
    }

    public ToolDefinition list_active_plans() {
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", Map.of());
        return ToolDefinition.of(
                "list_active_plans",
                "List all active task plans and their progress",
                schema
        );
    }

    // ========== Tool Execution ==========

    public String executePlanTask(Map<String, Object> args) {
        String taskDescription = (String) args.get("task_description");
        Integer maxSteps = (Integer) args.getOrDefault("max_steps", 10);

        if (taskDescription == null || taskDescription.isBlank()) {
            return formatError("task_description is required");
        }

        // Estimate complexity
        ComplexityEstimator.EstimationResult estimation = estimator.estimate(taskDescription);
        TaskComplexity complexity = estimation.complexity();

        // Create plan
        TaskPlan plan = new TaskPlan(taskDescription, complexity);

        // Generate microtasks
        List<String> microtasks = generateMicrotasks(taskDescription, complexity, maxSteps);
        for (String task : microtasks) {
            plan.addMicrotask(task);
        }

        // Store plan
        activePlans.put(plan.getId(), plan);

        return formatPlanCreated(plan);
    }

    public String executeGetPlan(Map<String, Object> args) {
        String planId = (String) args.get("plan_id");
        if (planId == null) {
            return formatError("plan_id is required");
        }

        TaskPlan plan = activePlans.get(planId);
        if (plan == null) {
            return formatError("Plan not found: " + planId);
        }

        return plan.toJson();
    }

    public String executeCompleteMicrotask(Map<String, Object> args) {
        String planId = (String) args.get("plan_id");
        Integer taskOrder = (Integer) args.get("task_order");
        String result = (String) args.getOrDefault("result", "Completed");

        if (planId == null || taskOrder == null) {
            return formatError("plan_id and task_order are required");
        }

        TaskPlan plan = activePlans.get(planId);
        if (plan == null) {
            return formatError("Plan not found: " + planId);
        }

        // Find and complete the task
        for (Microtask task : plan.getMicrotasks()) {
            if (task.getOrder() == taskOrder) {
                task.markCompleted(result);
                break;
            }
        }

        // Check if plan is complete
        if (!plan.hasMoreTasks()) {
            plan.setStatus(TaskPlan.PlanStatus.COMPLETED);
            return formatPlanCompleted(plan);
        }

        // Return next task
        return formatNextTask(plan);
    }

    public String executeListActivePlans(Map<String, Object> args) {
        if (activePlans.isEmpty()) {
            return "{\"plans\": [], \"message\": \"No active plans\"}";
        }

        StringBuilder json = new StringBuilder();
        json.append("{\"plans\": [");
        List<TaskPlan> plans = new ArrayList<>(activePlans.values());
        for (int i = 0; i < plans.size(); i++) {
            TaskPlan p = plans.get(i);
            json.append(String.format(
                    "{\"id\": \"%s\", \"complexity\": \"%s\", \"progress\": %.1f, \"tasks\": %d}",
                    p.getId(), p.getComplexity().getLabel(), p.getProgress(), p.getTotalTasks()
            ));
            if (i < plans.size() - 1) json.append(",");
        }
        json.append("]}");
        return json.toString();
    }

    // ========== Microtask Generation ==========

    private List<String> generateMicrotasks(String task, TaskComplexity complexity, int maxSteps) {
        if (llmCaller != null) {
            return generateMicrotasksWithLlm(task, complexity, maxSteps);
        }
        return generateMicrotasksLocal(task, complexity, maxSteps);
    }

    private List<String> generateMicrotasksWithLlm(String task, TaskComplexity complexity, int maxSteps) {
        String prompt = buildDecompositionPrompt(task, complexity, maxSteps);
        try {
            String response = llmCaller.apply(prompt);
            return parseMicrotasksFromResponse(response);
        } catch (Exception e) {
            return generateMicrotasksLocal(task, complexity, maxSteps);
        }
    }

    private List<String> generateMicrotasksLocal(String task, TaskComplexity complexity, int maxSteps) {
        List<String> microtasks = new ArrayList<>();
        String lower = task.toLowerCase();

        // Generic decomposition based on common patterns
        if (lower.contains("crear") || lower.contains("create") || lower.contains("build")) {
            microtasks.add("Analyze requirements and identify components needed");
            microtasks.add("Set up project structure and dependencies");
            microtasks.add("Implement core functionality");
            if (complexity.requiresDecomposition()) {
                microtasks.add("Add error handling and edge cases");
                microtasks.add("Write unit tests");
                microtasks.add("Integration testing and validation");
            }
        } else if (lower.contains("modificar") || lower.contains("modify") || lower.contains("update")) {
            microtasks.add("Read and understand existing code");
            microtasks.add("Identify files that need changes");
            microtasks.add("Make required modifications");
            microtasks.add("Verify changes work correctly");
        } else if (lower.contains("fix") || lower.contains("arreglar") || lower.contains("bug")) {
            microtasks.add("Reproduce and understand the issue");
            microtasks.add("Identify root cause");
            microtasks.add("Implement fix");
            microtasks.add("Test the fix");
        } else {
            // Default decomposition
            microtasks.add("Analyze task requirements");
            microtasks.add("Execute main task");
            microtasks.add("Verify results");
        }

        // Limit to maxSteps
        if (microtasks.size() > maxSteps) {
            return microtasks.subList(0, maxSteps);
        }

        return microtasks;
    }

    private String buildDecompositionPrompt(String task, TaskComplexity complexity, int maxSteps) {
        return """
            Decompose the following task into %d or fewer sequential microtasks.
            Each microtask should be:
            - Atomic (one clear action)
            - Verifiable (clear completion criteria)
            - Ordered logically

            Complexity level: %s

            Task: %s

            Respond with a JSON array of strings, each being a microtask description.
            Example: ["Read existing code", "Identify changes needed", "Implement changes", "Test"]

            Microtasks:""".formatted(maxSteps, complexity.getLabel(), task);
    }

    private List<String> parseMicrotasksFromResponse(String response) {
        List<String> tasks = new ArrayList<>();
        try {
            // Try to parse as JSON array
            if (response.contains("[")) {
                int start = response.indexOf('[');
                int end = response.lastIndexOf(']') + 1;
                if (start >= 0 && end > start) {
                    String jsonArray = response.substring(start, end);
                    @SuppressWarnings("unchecked")
                    List<String> parsed = MAPPER.readValue(jsonArray, List.class);
                    return parsed;
                }
            }

            // Fallback: parse numbered list
            String[] lines = response.split("\n");
            for (String line : lines) {
                String trimmed = line.trim();
                if (trimmed.matches("^\\d+[.)].*") || trimmed.startsWith("- ")) {
                    String task = trimmed.replaceFirst("^\\d+[.)\\s]*", "")
                            .replaceFirst("^-\\s*", "")
                            .trim();
                    if (!task.isEmpty()) {
                        tasks.add(task);
                    }
                }
            }
        } catch (Exception e) {
            // Return empty list on parse error
        }
        return tasks.isEmpty() ? List.of("Execute task: " + response.substring(0, Math.min(50, response.length()))) : tasks;
    }

    // ========== Response Formatting ==========

    private String formatError(String message) {
        return String.format("{\"success\": false, \"error\": \"%s\"}", message);
    }

    private String formatPlanCreated(TaskPlan plan) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"success\": true,\n");
        sb.append(String.format("  \"plan_id\": \"%s\",\n", plan.getId()));
        sb.append(String.format("  \"complexity\": \"%s\",\n", plan.getComplexity().getLabel()));
        sb.append(String.format("  \"total_tasks\": %d,\n", plan.getTotalTasks()));
        sb.append(String.format("  \"requires_confirmation\": %b,\n", plan.isRequiresConfirmation()));
        sb.append("  \"microtasks\": [\n");
        List<Microtask> tasks = plan.getMicrotasks();
        for (int i = 0; i < tasks.size(); i++) {
            Microtask t = tasks.get(i);
            sb.append(String.format("    {\"order\": %d, \"description\": \"%s\"}",
                    t.getOrder(), escapeJson(t.getDescription())));
            if (i < tasks.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("  ],\n");
        sb.append(String.format("  \"first_task\": \"%s\",\n",
                escapeJson(tasks.isEmpty() ? "" : tasks.get(0).getDescription())));
        sb.append("  \"instruction\": \"Execute tasks in order. Call complete_microtask after each one.\"\n");
        sb.append("}");
        return sb.toString();
    }

    private String formatNextTask(TaskPlan plan) {
        return plan.getCurrentMicrotask()
                .map(task -> String.format(
                        "{\"success\": true, \"progress\": \"%.0f%%\", \"next_task\": {\"order\": %d, \"description\": \"%s\"}}",
                        plan.getProgress(), task.getOrder(), escapeJson(task.getDescription())
                ))
                .orElse("{\"success\": true, \"message\": \"All tasks completed\"}");
    }

    private String formatPlanCompleted(TaskPlan plan) {
        return String.format(
                "{\"success\": true, \"plan_id\": \"%s\", \"status\": \"completed\", \"total_tasks\": %d, \"message\": \"All microtasks completed successfully\"}",
                plan.getId(), plan.getTotalTasks()
        );
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n");
    }

    // ========== Plan Management ==========

    public static TaskPlan getActivePlan(String planId) {
        return activePlans.get(planId);
    }

    public static void clearPlan(String planId) {
        activePlans.remove(planId);
    }

    public static void clearAllPlans() {
        activePlans.clear();
    }
}
