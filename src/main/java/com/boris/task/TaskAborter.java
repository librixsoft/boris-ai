package com.boris.task;

import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Set;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

public class TaskAborter {

    private final Set<Thread> activeTasks = ConcurrentHashMap.newKeySet();
    private final Set<Process> activeProcesses = ConcurrentHashMap.newKeySet();
    private final List<Runnable> abortHooks = new CopyOnWriteArrayList<>();
    private volatile boolean aborted;
    private volatile String ollamaBaseUrl;

    public void startTask(Thread task) {
        if (task == null) return;
        synchronized (this) {
            if (aborted) {
                return;
            }
            activeTasks.add(task);
        }
    }

    public void unregisterTask(Thread task) {
        if (task == null) return;
        activeTasks.remove(task);
    }

    public void registerProcess(Process process) {
        if (process == null) return;
        activeProcesses.add(process);
    }

    public void unregisterProcess(Process process) {
        if (process == null) return;
        activeProcesses.remove(process);
    }

    public void setOllamaBaseUrl(String url) {
        if (url != null && !url.isBlank()) {
            String normalized = url.trim();
            while (normalized.endsWith("/")) {
                normalized = normalized.substring(0, normalized.length() - 1);
            }
            this.ollamaBaseUrl = normalized;
        }
    }

    public String getOllamaBaseUrl() {
        return ollamaBaseUrl;
    }

    public void addAbortHook(Runnable hook) {
        if (hook != null) {
            abortHooks.add(hook);
        }
    }

    public void clearAbortHooks() {
        abortHooks.clear();
    }

    public synchronized void abort() {
        this.aborted = true;

        // Cancel active Ollama generation first (releases GPU)
        cancelOllamaGeneration();

        // Interrupt all registered threads
        for (Thread task : activeTasks) {
            if (task != null && task.isAlive()) {
                task.interrupt();
            }
        }
        activeTasks.clear();

        // Destroy all registered processes
        for (Process process : activeProcesses) {
            if (process != null && process.isAlive()) {
                process.destroyForcibly();
            }
        }
        activeProcesses.clear();

        // Execute abort hooks
        for (Runnable hook : abortHooks) {
            try {
                hook.run();
            } catch (Exception e) {
                // Silently ignore hook exceptions
            }
        }
        abortHooks.clear();
    }

    private void cancelOllamaGeneration() {
        if (ollamaBaseUrl == null || ollamaBaseUrl.isBlank()) {
            return;
        }
        Thread cancelThread = new Thread(() -> {
            try {
                HttpURLConnection conn = (HttpURLConnection)
                    new URL(ollamaBaseUrl + "/api/generate").openConnection();
                conn.setRequestMethod("DELETE");
                conn.setConnectTimeout(2000);
                conn.setReadTimeout(2000);
                conn.getResponseCode();
                conn.disconnect();
            } catch (Exception ignored) {
            }
        });
        cancelThread.setDaemon(true);
        cancelThread.start();
    }

    /**
     * Abort asynchronously in a daemon thread to avoid blocking the UI thread.
     */
    public void abortAsync() {
        Thread abortThread = new Thread(() -> {
            try {
                abort();
                // Brief delay before reset to allow cleanup
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                reset();
            }
        });
        abortThread.setDaemon(true);
        abortThread.start();
    }

    public synchronized boolean isAborted() {
        return aborted;
    }

    public synchronized void reset() {
        this.aborted = false;
        activeTasks.clear();
        activeProcesses.clear();
        abortHooks.clear();
    }

    public int getActiveTaskCount() {
        return activeTasks.size();
    }

    public int getActiveProcessCount() {
        return activeProcesses.size();
    }
}
