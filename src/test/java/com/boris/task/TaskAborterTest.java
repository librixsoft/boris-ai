package com.boris.task;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.*;
import java.util.concurrent.CountDownLatch;

public class TaskAborterTest {

    @Test
    void abort_running_task_stops_it() throws Exception {
        TaskAborter aborter = new TaskAborter();
        CountDownLatch latch = new CountDownLatch(1);
        Thread task = new Thread(() -> {
            try { latch.await(); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        });
        task.setDaemon(true);
        task.start();
        aborter.startTask(task);
        Thread.sleep(100);
        assertTrue(task.isAlive());
        aborter.abort();
        latch.countDown();
        task.join(2000);
        assertFalse(task.isAlive());
    }

    @Test
    void abort_without_task_no_error() {
        TaskAborter aborter = new TaskAborter();
        assertDoesNotThrow(() -> aborter.abort());
    }

    @Test
    void multiple_abort_calls_are_idempotent() {
        TaskAborter aborter = new TaskAborter();
        assertDoesNotThrow(() -> {
            aborter.abort();
            aborter.abort();
            aborter.abort();
        });
    }

    @Test
    void isAborted_returns_true_after_abort() {
        TaskAborter aborter = new TaskAborter();
        assertFalse(aborter.isAborted());
        aborter.abort();
        assertTrue(aborter.isAborted());
    }

    @Test
    void generation_tracking_works() {
        TaskAborter aborter = new TaskAborter();
        assertFalse(aborter.isGenerationInProgress());
        aborter.markGenerationStart();
        assertTrue(aborter.isGenerationInProgress());
        aborter.markGenerationEnd();
        assertFalse(aborter.isGenerationInProgress());
    }

    @Test
    void active_model_is_set() {
        TaskAborter aborter = new TaskAborter();
        aborter.setActiveModel("llama3");
        aborter.setOllamaBaseUrl("http://localhost:11434");
        assertEquals("http://localhost:11434", aborter.getOllamaBaseUrl());
    }

    @Test
    void reset_clears_generation_state() {
        TaskAborter aborter = new TaskAborter();
        aborter.markGenerationStart();
        aborter.abort();
        assertTrue(aborter.isAborted());
        aborter.reset();
        assertFalse(aborter.isAborted());
        assertFalse(aborter.isGenerationInProgress());
    }

    @Test
    void abort_without_ollama_url_does_not_throw() {
        TaskAborter aborter = new TaskAborter();
        aborter.markGenerationStart();
        assertDoesNotThrow(() -> aborter.abort());
    }
}
