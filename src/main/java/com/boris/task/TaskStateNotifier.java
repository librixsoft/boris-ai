package com.boris.task;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Notificador de cambios de estado en la ejecucion de tareas.
 */
public class TaskStateNotifier {

    private static final List<BiConsumer<TaskExecutionState, TaskExecutionState>> listeners = new CopyOnWriteArrayList<>();
    private static volatile TaskExecutionState currentState = TaskExecutionState.IDLE;

    public static void addListener(BiConsumer<TaskExecutionState, TaskExecutionState> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public static void removeListener(BiConsumer<TaskExecutionState, TaskExecutionState> listener) {
        listeners.remove(listener);
    }

    public static void clearListeners() {
        listeners.clear();
    }

    public static TaskExecutionState getCurrentState() {
        return currentState;
    }

    public static void setState(TaskExecutionState newState) {
        if (newState == null) {
            return;
        }
        TaskExecutionState oldState = currentState;
        currentState = newState;
        if (oldState != newState) {
            for (var listener : listeners) {
                try {
                    listener.accept(oldState, newState);
                } catch (Exception ignored) {
                    // Evitar que errores de listeners afecten el loop de ejecucion
                }
            }
        }
    }
}
