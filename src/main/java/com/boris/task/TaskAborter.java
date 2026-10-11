package com.boris.task;

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
