package com.boris.task.decomposition;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Opción 4: Agentic Loop con checkpoints
 * Ejecuta un plan de tareas de forma autónoma con puntos de verificación.
 */
public class AgenticExecutor {

    public interface ExecutionCallback {
        void onPlanCreated(TaskPlan plan);
        void onMicrotaskStarted(Microtask task);
        void onMicrotaskCompleted(Microtask task, String result);
        void onMicrotaskFailed(Microtask task, String error);
        void onCheckpointReached(TaskPlan plan, Microtask completedTask);
        void onPlanCompleted(TaskPlan plan);
        void onPlanFailed(TaskPlan plan, String error);
        void onProgressUpdate(TaskPlan plan);
    }

    public static class SimpleCallback implements ExecutionCallback {
        private final Consumer<String> logger;

        public SimpleCallback(Consumer<String> logger) {
            this.logger = logger;
        }

        @Override
        public void onPlanCreated(TaskPlan plan) {
            logger.accept("📋 Plan creado: " + plan.getId() + " (" + plan.getTotalTasks() + " tareas)");
        }

        @Override
        public void onMicrotaskStarted(Microtask task) {
            logger.accept("▶ Iniciando: [" + task.getOrder() + "] " + task.getDescription());
        }

        @Override
        public void onMicrotaskCompleted(Microtask task, String result) {
            logger.accept("✓ Completada: [" + task.getOrder() + "] " + task.getDescription());
        }

        @Override
        public void onMicrotaskFailed(Microtask task, String error) {
            logger.accept("✗ Falló: [" + task.getOrder() + "] " + error);
        }

        @Override
        public void onCheckpointReached(TaskPlan plan, Microtask completedTask) {
            logger.accept("⏸ Checkpoint: " + plan.getProgressBar());
        }

        @Override
        public void onPlanCompleted(TaskPlan plan) {
            logger.accept("✅ Plan completado: " + plan.getId());
        }

        @Override
        public void onPlanFailed(TaskPlan plan, String error) {
            logger.accept("❌ Plan falló: " + error);
        }

        @Override
        public void onProgressUpdate(TaskPlan plan) {
            logger.accept("📊 " + plan.getProgressBar());
        }
    }

    public record ExecutionConfig(
            boolean requireConfirmationBetweenTasks,
            int checkpointEveryNTasks,
            int maxRetries,
            long taskTimeoutMs,
            boolean stopOnFirstFailure
    ) {
        public static ExecutionConfig defaults() {
            return new ExecutionConfig(false, 3, 2, 300_000, false);
        }

        public static ExecutionConfig withConfirmation() {
            return new ExecutionConfig(true, 1, 2, 300_000, false);
        }

        public static ExecutionConfig autonomous() {
            return new ExecutionConfig(false, 5, 3, 600_000, false);
        }
    }

    public record ExecutionResult(
            TaskPlan plan,
            boolean success,
            List<MicrotaskResult> results,
            String summary
    ) {}

    public record MicrotaskResult(
            Microtask task,
            boolean success,
            String output,
            long durationMs
    ) {}

    private final Function<String, String> taskExecutor;
    private final Supplier<Boolean> confirmationProvider;
    private final ExecutionCallback callback;
    private final AtomicBoolean aborted;

    public AgenticExecutor(Function<String, String> taskExecutor) {
        this(taskExecutor, () -> true, new SimpleCallback(System.out::println));
    }

    public AgenticExecutor(Function<String, String> taskExecutor, Consumer<String> logger) {
        this(taskExecutor, () -> true, new SimpleCallback(logger));
    }

    public AgenticExecutor(Function<String, String> taskExecutor,
                          Supplier<Boolean> confirmationProvider,
                          ExecutionCallback callback) {
        this.taskExecutor = taskExecutor;
        this.confirmationProvider = confirmationProvider;
        this.callback = callback;
        this.aborted = new AtomicBoolean(false);
    }

    public void abort() {
        aborted.set(true);
    }

    public void reset() {
        aborted.set(false);
    }

    /**
     * Ejecuta un plan completo de forma autónoma
     */
    public ExecutionResult execute(TaskPlan plan, ExecutionConfig config) {
        aborted.set(false);
        callback.onPlanCreated(plan);
        plan.setStatus(TaskPlan.PlanStatus.IN_PROGRESS);

        List<MicrotaskResult> results = new ArrayList<>();
        int consecutiveFailures = 0;
        int tasksSinceCheckpoint = 0;

        while (plan.hasMoreTasks() && !aborted.get()) {
            Optional<Microtask> optTask = plan.getNextMicrotask();
            if (optTask.isEmpty()) break;

            Microtask task = optTask.get();
            task.markInProgress();
            callback.onMicrotaskStarted(task);

            // Execute task with retries
            MicrotaskResult result = executeWithRetry(task, config);
            results.add(result);

            if (result.success()) {
                task.markCompleted(result.output());
                callback.onMicrotaskCompleted(task, result.output());
                consecutiveFailures = 0;
                tasksSinceCheckpoint++;

                // Check for checkpoint
                if (tasksSinceCheckpoint >= config.checkpointEveryNTasks()) {
                    callback.onCheckpointReached(plan, task);
                    tasksSinceCheckpoint = 0;

                    // Request confirmation if configured
                    if (config.requireConfirmationBetweenTasks() && plan.hasMoreTasks()) {
                        if (!requestConfirmation(plan)) {
                            plan.setStatus(TaskPlan.PlanStatus.ABORTED);
                            return new ExecutionResult(plan, false, results, "Aborted by user");
                        }
                    }
                }
            } else {
                task.markFailed(result.output());
                callback.onMicrotaskFailed(task, result.output());
                consecutiveFailures++;

                if (config.stopOnFirstFailure() || consecutiveFailures > config.maxRetries()) {
                    plan.setStatus(TaskPlan.PlanStatus.FAILED);
                    callback.onPlanFailed(plan, "Too many failures: " + result.output());
                    return new ExecutionResult(plan, false, results, "Failed at task " + task.getOrder());
                }
            }

            callback.onProgressUpdate(plan);
        }

        if (aborted.get()) {
            plan.setStatus(TaskPlan.PlanStatus.ABORTED);
            return new ExecutionResult(plan, false, results, "Execution aborted");
        }

        plan.setStatus(TaskPlan.PlanStatus.COMPLETED);
        callback.onPlanCompleted(plan);
        return new ExecutionResult(plan, true, results, buildSummary(plan, results));
    }

    /**
     * Ejecuta una sola microtarea
     */
    public MicrotaskResult executeSingle(Microtask task) {
        long start = System.currentTimeMillis();
        try {
            String prompt = buildExecutionPrompt(task);
            String result = taskExecutor.apply(prompt);
            long duration = System.currentTimeMillis() - start;
            return new MicrotaskResult(task, true, result, duration);
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            return new MicrotaskResult(task, false, e.getMessage(), duration);
        }
    }

    /**
     * Ejecuta tareas una por una, esperando confirmación entre cada una
     */
    public Optional<MicrotaskResult> executeNext(TaskPlan plan) {
        if (!plan.hasMoreTasks()) {
            return Optional.empty();
        }

        Optional<Microtask> optTask = plan.getNextMicrotask();
        if (optTask.isEmpty()) {
            return Optional.empty();
        }

        Microtask task = optTask.get();
        task.markInProgress();
        MicrotaskResult result = executeSingle(task);

        if (result.success()) {
            task.markCompleted(result.output());
        } else {
            task.markFailed(result.output());
        }

        return Optional.of(result);
    }

    private MicrotaskResult executeWithRetry(Microtask task, ExecutionConfig config) {
        MicrotaskResult result = null;
        int attempts = 0;

        while (attempts <= config.maxRetries() && !aborted.get()) {
            result = executeSingle(task);
            if (result.success()) {
                return result;
            }
            attempts++;

            if (attempts <= config.maxRetries()) {
                // Brief pause before retry
                try {
                    Thread.sleep(500);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }

        return result != null ? result : new MicrotaskResult(task, false, "Max retries exceeded", 0);
    }

    private boolean requestConfirmation(TaskPlan plan) {
        // Log pending confirmation
        callback.onProgressUpdate(plan);
        return confirmationProvider.get();
    }

    private String buildExecutionPrompt(Microtask task) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("Execute the following task:\n\n");
        prompt.append("Task: ").append(task.getDescription()).append("\n\n");

        if (task.getExpectedOutput() != null) {
            prompt.append("Expected output: ").append(task.getExpectedOutput()).append("\n\n");
        }

        prompt.append("Instructions:\n");
        prompt.append("- Complete this specific task only\n");
        prompt.append("- Use the appropriate tools (read_file, apply_edit, execute_command, etc.)\n");
        prompt.append("- Report what was done when finished\n");

        return prompt.toString();
    }

    private String buildSummary(TaskPlan plan, List<MicrotaskResult> results) {
        int successful = (int) results.stream().filter(MicrotaskResult::success).count();
        int failed = results.size() - successful;
        long totalTime = results.stream().mapToLong(MicrotaskResult::durationMs).sum();

        return String.format("Completed %d/%d tasks (%d failed) in %dms",
                successful, results.size(), failed, totalTime);
    }

    /**
     * Builder para configuración fluida
     */
    public static class Builder {
        private Function<String, String> taskExecutor;
        private Supplier<Boolean> confirmationProvider = () -> true;
        private ExecutionCallback callback;
        private Consumer<String> logger = System.out::println;

        public Builder taskExecutor(Function<String, String> executor) {
            this.taskExecutor = executor;
            return this;
        }

        public Builder confirmationProvider(Supplier<Boolean> provider) {
            this.confirmationProvider = provider;
            return this;
        }

        public Builder callback(ExecutionCallback callback) {
            this.callback = callback;
            return this;
        }

        public Builder logger(Consumer<String> logger) {
            this.logger = logger;
            return this;
        }

        public AgenticExecutor build() {
            if (taskExecutor == null) {
                throw new IllegalStateException("taskExecutor is required");
            }
            if (callback == null) {
                callback = new SimpleCallback(logger);
            }
            return new AgenticExecutor(taskExecutor, confirmationProvider, callback);
        }
    }

    public static Builder builder() {
        return new Builder();
    }
}
