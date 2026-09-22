package com.boris.agent;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import com.boris.settings.ModelConfig;
import com.boris.settings.Settings;
import com.boris.task.TaskAborter;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MultiAgentExecutorTest {

    private Settings settingsEnabled;
    private Settings settingsDisabled;

    @BeforeEach
    void setUp() {
        settingsEnabled = new Settings();
        settingsEnabled.setMultiAgent("yes");

        settingsDisabled = new Settings();
        settingsDisabled.setMultiAgent("no");
    }

    @Test
    void constructor_createsExecutorWithDefaults() {
        MultiAgentExecutor executor = new MultiAgentExecutor(settingsEnabled);
        assertNotNull(executor);
        executor.shutdown();
    }

    @Test
    void constructor_acceptsNullTaskAborter() {
        MultiAgentExecutor executor = new MultiAgentExecutor(settingsEnabled, null);
        assertNotNull(executor);
        executor.shutdown();
    }

    @Test
    void runParallelTasks_withNullList_returnsNoTasks() {
        MultiAgentExecutor executor = new MultiAgentExecutor(settingsEnabled);
        String result = executor.runParallelTasks(null);
        assertEquals("No tasks provided for parallel execution.", result);
        executor.shutdown();
    }

    @Test
    void runParallelTasks_withEmptyList_returnsNoTasks() {
        MultiAgentExecutor executor = new MultiAgentExecutor(settingsEnabled);
        String result = executor.runParallelTasks(List.of());
        assertEquals("No tasks provided for parallel execution.", result);
        executor.shutdown();
    }

    @Test
    void runParallelTasks_withAbortedState_returnsAborted() {
        TaskAborter aborter = new TaskAborter();
        aborter.abort();
        MultiAgentExecutor executor = new MultiAgentExecutor(settingsEnabled, aborter);
        String result = executor.runParallelTasks(List.of("task1", "task2"));
        assertEquals("Parallel task execution aborted.", result);
        executor.shutdown();
    }

    @Test
    void runParallelTasks_executesTasksAndReturnsResults() {
        // Without a real model configured, executeWorkerTask returns a formatted string
        MultiAgentExecutor executor = new MultiAgentExecutor(settingsEnabled);
        String result = executor.runParallelTasks(List.of("analyze code", "check tests"));
        assertNotNull(result);
        assertTrue(result.contains("PARALLEL MULTI-AGENT EXECUTION"));
        assertTrue(result.contains("2 tasks"));
        assertTrue(result.contains("Agent Worker #1"));
        assertTrue(result.contains("Agent Worker #2"));
        assertTrue(result.contains("END PARALLEL EXECUTION"));
        executor.shutdown();
    }

    @Test
    void spawnSubagent_withNullTask_returnsError() {
        MultiAgentExecutor executor = new MultiAgentExecutor(settingsEnabled);
        String result = executor.spawnSubagent(null, "researcher");
        assertEquals("Task description cannot be empty.", result);
        executor.shutdown();
    }

    @Test
    void spawnSubagent_withBlankTask_returnsError() {
        MultiAgentExecutor executor = new MultiAgentExecutor(settingsEnabled);
        String result = executor.spawnSubagent("   ", "researcher");
        assertEquals("Task description cannot be empty.", result);
        executor.shutdown();
    }

    @Test
    void spawnSubagent_withAbortedState_returnsAborted() {
        TaskAborter aborter = new TaskAborter();
        aborter.abort();
        MultiAgentExecutor executor = new MultiAgentExecutor(settingsEnabled, aborter);
        String result = executor.spawnSubagent("do something", "coder");
        assertEquals("Subagent execution aborted.", result);
        executor.shutdown();
    }

    @Test
    void spawnSubagent_withNullRole_usesDefaultRole() {
        MultiAgentExecutor executor = new MultiAgentExecutor(settingsEnabled);
        String result = executor.spawnSubagent("analyze this code", null);
        assertNotNull(result);
        assertTrue(result.contains("specialized_assistant") || result.contains("analyze this code"));
        executor.shutdown();
    }

    @Test
    void spawnSubagent_withValidTaskAndRole_returnsResult() {
        MultiAgentExecutor executor = new MultiAgentExecutor(settingsEnabled);
        String result = executor.spawnSubagent("review the code", "code_reviewer");
        assertNotNull(result);
        assertFalse(result.isEmpty());
        executor.shutdown();
    }

    @Test
    void shutdown_doesNotThrow() {
        MultiAgentExecutor executor = new MultiAgentExecutor(settingsEnabled);
        assertDoesNotThrow(executor::shutdown);
    }

    @Test
    void workerResult_recordFieldsAreAccessible() {
        MultiAgentExecutor.WorkerResult result = new MultiAgentExecutor.WorkerResult(1, "test task", "output data");
        assertEquals(1, result.workerIndex());
        assertEquals("test task", result.task());
        assertEquals("output data", result.output());
    }

    @Test
    void runParallelTasks_singleTask_executesCorrectly() {
        MultiAgentExecutor executor = new MultiAgentExecutor(settingsEnabled);
        String result = executor.runParallelTasks(List.of("single task"));
        assertNotNull(result);
        assertTrue(result.contains("1 tasks"));
        assertTrue(result.contains("Agent Worker #1"));
        executor.shutdown();
    }

    @Test
    void statusListeners_receiveStatusEventsOnSpawnSubagent() {
        MultiAgentExecutor executor = new MultiAgentExecutor(settingsEnabled);
        java.util.List<String> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        executor.addStatusListener(events::add);

        executor.spawnSubagent("search the docs", "researcher");

        assertFalse(events.isEmpty());
        assertTrue(events.stream().anyMatch(e -> e.contains("[status]") && e.contains("Desplegando nuevo subagente")));
        assertTrue(events.stream().anyMatch(e -> e.contains("researcher")));
        executor.shutdown();
    }

    @Test
    void statusListeners_receiveStatusEventsOnRunParallelTasks() {
        MultiAgentExecutor executor = new MultiAgentExecutor(settingsEnabled);
        java.util.List<String> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        executor.addStatusListener(events::add);

        executor.runParallelTasks(List.of("task alpha", "task beta"));

        assertFalse(events.isEmpty());
        assertTrue(events.stream().anyMatch(e -> e.contains("[status]") && e.contains("Desplegando 2 subagentes en paralelo")));
        assertTrue(events.stream().anyMatch(e -> e.contains("Subagente #1")));
        assertTrue(events.stream().anyMatch(e -> e.contains("Subagente #2")));
        executor.shutdown();
    }

    @Test
    void globalStatusListener_receivesAndCanBeRemoved() {
        java.util.List<String> globalEvents = new java.util.concurrent.CopyOnWriteArrayList<>();
        java.util.function.Consumer<String> listener = globalEvents::add;
        MultiAgentExecutor.addGlobalStatusListener(listener);

        MultiAgentExecutor executor = new MultiAgentExecutor(settingsEnabled);
        executor.spawnSubagent("quick test", "tester");

        assertFalse(globalEvents.isEmpty());
        assertTrue(globalEvents.stream().anyMatch(e -> e.contains("[status]")));

        globalEvents.clear();
        MultiAgentExecutor.removeGlobalStatusListener(listener);

        executor.spawnSubagent("another test", "tester");
        assertTrue(globalEvents.isEmpty());

        executor.shutdown();
    }

    // --- Auto-Integration Tests ---

    @Test
    void extractFilePaths_findsAbsolutePaths() {
        String text = "Create a landing page in /Users/dev/project/landing.html and styles in /Users/dev/project/styles.css";
        java.util.Set<String> paths = MultiAgentExecutor.extractFilePaths(text);
        assertEquals(2, paths.size());
        assertTrue(paths.contains("/Users/dev/project/landing.html"));
        assertTrue(paths.contains("/Users/dev/project/styles.css"));
    }

    @Test
    void extractFilePaths_filtersOutSystemPaths() {
        String text = "Read from /usr/local/bin/tool and /etc/config.json and write to /Users/dev/output.md";
        java.util.Set<String> paths = MultiAgentExecutor.extractFilePaths(text);
        assertEquals(1, paths.size());
        assertTrue(paths.contains("/Users/dev/output.md"));
    }

    @Test
    void extractFilePaths_returnsEmptyForNullOrBlank() {
        assertTrue(MultiAgentExecutor.extractFilePaths(null).isEmpty());
        assertTrue(MultiAgentExecutor.extractFilePaths("").isEmpty());
        assertTrue(MultiAgentExecutor.extractFilePaths("   ").isEmpty());
    }

    @Test
    void extractFilePaths_returnsEmptyWhenNoPathsFound() {
        String text = "Just a simple task with no file paths mentioned";
        assertTrue(MultiAgentExecutor.extractFilePaths(text).isEmpty());
    }

    @Test
    void buildIntegrationPrompt_containsAllFilesAndTasks() {
        java.util.Set<String> paths = new java.util.LinkedHashSet<>();
        paths.add("/project/index.html");
        paths.add("/project/styles.css");
        List<String> tasks = List.of("Create HTML", "Create CSS");

        String prompt = MultiAgentExecutor.buildIntegrationPrompt(paths, tasks);

        assertTrue(prompt.contains("INTEGRATOR"));
        assertTrue(prompt.contains("/project/index.html"));
        assertTrue(prompt.contains("/project/styles.css"));
        assertTrue(prompt.contains("Task 1: Create HTML"));
        assertTrue(prompt.contains("Task 2: Create CSS"));
        assertTrue(prompt.contains("read_file"));
        assertTrue(prompt.contains("apply_edit"));
        assertTrue(prompt.contains("link rel=\"stylesheet\""));
    }

    @Test
    void runParallelTasks_withFilePaths_triggersAutoIntegration() {
        MultiAgentExecutor executor = new MultiAgentExecutor(settingsEnabled);
        java.util.List<String> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        executor.addStatusListener(events::add);

        String result = executor.runParallelTasks(List.of(
                "Create landing page in /Users/dev/project/landing.html",
                "Create styles in /Users/dev/project/styles.css"
        ));

        assertNotNull(result);
        assertTrue(result.contains("PARALLEL MULTI-AGENT EXECUTION"));
        assertTrue(result.contains("AUTO-INTEGRATION PHASE"));
        assertTrue(events.stream().anyMatch(e -> e.contains("integración automática")));
        executor.shutdown();
    }

    @Test
    void runParallelTasks_withoutFilePaths_alsoTriggersAutoIntegration() {
        MultiAgentExecutor executor = new MultiAgentExecutor(settingsEnabled);
        java.util.List<String> events = new java.util.concurrent.CopyOnWriteArrayList<>();
        executor.addStatusListener(events::add);

        String result = executor.runParallelTasks(List.of(
                "Research topic alpha",
                "Research topic beta"
        ));

        assertNotNull(result);
        assertTrue(result.contains("PARALLEL MULTI-AGENT EXECUTION"));
        assertTrue(result.contains("AUTO-INTEGRATION PHASE"),
                "Generic integration should run for any parallel tasks (2+ tasks)");
        assertTrue(events.stream().anyMatch(e -> e.contains("integración automática")));
        executor.shutdown();
    }

    @Test
    void runParallelTasks_withSingleTask_skipsAutoIntegration() {
        MultiAgentExecutor executor = new MultiAgentExecutor(settingsEnabled);

        String result = executor.runParallelTasks(List.of(
                "Single task execution"
        ));

        assertNotNull(result);
        assertFalse(result.contains("AUTO-INTEGRATION PHASE"),
                "Should not run integration phase for single task execution");
        executor.shutdown();
    }
}

