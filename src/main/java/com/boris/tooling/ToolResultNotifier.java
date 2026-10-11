package com.boris.tooling;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ToolResultNotifier {

    public record ToolResult(String toolName, String filePath, String diff, boolean success) {}

    public record ToolTokenUsage(String toolName, int inputTokens, int outputTokens) {}

    private static final List<Consumer<ToolResult>> listeners = new CopyOnWriteArrayList<>();
    private static final List<Consumer<ToolTokenUsage>> tokenListeners = new CopyOnWriteArrayList<>();

    public static void addListener(Consumer<ToolResult> listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    public static void removeListener(Consumer<ToolResult> listener) {
        listeners.remove(listener);
    }

    public static void addTokenListener(Consumer<ToolTokenUsage> listener) {
        if (listener != null) {
            tokenListeners.add(listener);
        }
    }

    public static void removeTokenListener(Consumer<ToolTokenUsage> listener) {
        tokenListeners.remove(listener);
    }

    public static void clearListeners() {
        listeners.clear();
        tokenListeners.clear();
    }

    public static void notify(ToolResult result) {
        for (Consumer<ToolResult> listener : listeners) {
            try {
                listener.accept(result);
            } catch (Exception ignored) {}
        }
    }

    public static void notifyTokenUsage(ToolTokenUsage usage) {
        for (Consumer<ToolTokenUsage> listener : tokenListeners) {
            try {
                listener.accept(usage);
            } catch (Exception ignored) {}
        }
    }

    public static void notifyTokenUsage(String toolName, int inputTokens, int outputTokens) {
        notifyTokenUsage(new ToolTokenUsage(toolName, inputTokens, outputTokens));
    }

    public static void notifyEdit(String filePath, String diff, boolean success) {
        notify(new ToolResult("apply_edit", filePath, diff, success));
    }
}
