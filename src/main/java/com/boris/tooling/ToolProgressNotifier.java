package com.boris.tooling;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;

/**
 * Notifier for tool progress messages.
 * Tools can emit progress updates that are displayed in the chat UI
 * so the user knows what the agent is doing at each step.
 */
public class ToolProgressNotifier {

    /**
     * Progress message with tool name and description.
     */
    public record ProgressMessage(String toolName, String message) {}

    private static final List<BiConsumer<String, String>> listeners = new CopyOnWriteArrayList<>();

    /**
     * Add a listener for progress notifications.
     * Listener receives (toolName, message).
     */
    public static void addListener(BiConsumer<String, String> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * Remove a progress listener.
     */
    public static void removeListener(BiConsumer<String, String> listener) {
        listeners.remove(listener);
    }

    /**
     * Clear all listeners.
     */
    public static void clearListeners() {
        listeners.clear();
    }

    /**
     * Notify all listeners of a progress update.
     *
     * @param toolName Name of the tool emitting the progress
     * @param message Description of what the tool is doing
     */
    public static void notify(String toolName, String message) {
        for (BiConsumer<String, String> listener : listeners) {
            try {
                listener.accept(toolName, message);
            } catch (Exception ignored) {
                // Don't let listener errors break tool execution
            }
        }
    }

    /**
     * Format a progress message for display.
     */
    public static String format(String toolName, String message) {
        return "[" + toolName + "] " + message;
    }
}
