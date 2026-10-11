package com.boris.tooling;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ToolResultNotifier {

    public record ToolResult(String toolName, String filePath, String diff, boolean success) {}

    private static final List<Consumer<ToolResult>> listeners = new CopyOnWriteArrayList<>();

    public static void addListener(Consumer<ToolResult> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Consumer<ToolResult> listener) {
        listeners.remove(listener);
    }

    public static void clearListeners() {
        listeners.clear();
    }

    public static void notify(ToolResult result) {
        for (Consumer<ToolResult> listener : listeners) {
            try {
                listener.accept(result);
            } catch (Exception ignored) {}
        }
    }

    public static void notifyEdit(String filePath, String diff, boolean success) {
        notify(new ToolResult("apply_edit", filePath, diff, success));
    }
}
