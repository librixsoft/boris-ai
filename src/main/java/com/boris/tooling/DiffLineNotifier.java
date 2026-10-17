package com.boris.tooling;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/**
 * Notifier for diff lines that should be rendered with colors.
 * Unlike ToolProgressNotifier (which shows gray tool details),
 * this sends lines to be rendered as normal content with diff colors.
 */
public class DiffLineNotifier {

    private static final List<Consumer<String>> listeners = new CopyOnWriteArrayList<>();

    public static void addListener(Consumer<String> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Consumer<String> listener) {
        listeners.remove(listener);
    }

    public static void clearListeners() {
        listeners.clear();
    }

    /**
     * Send a diff line to be rendered with colors.
     * Format should be: "  N │+ content" or "  N │- content"
     */
    public static void notify(String line) {
        for (Consumer<String> listener : listeners) {
            try {
                listener.accept(line);
            } catch (Exception ignored) {
            }
        }
    }
}
