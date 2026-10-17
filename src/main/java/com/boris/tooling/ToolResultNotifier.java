package com.boris.tooling;

import java.util.concurrent.CopyOnWriteArrayList;
import java.util.List;
import java.util.function.Consumer;

public class ToolResultNotifier {

    public record ToolResult(String toolName, String filePath, String diff, boolean success) {}

    public record ToolTokenUsage(String toolName, int inputTokens, int outputTokens) {}

    /**
     * Query results from tools like github_api_search, web_fetch, etc.
     * These are cached to provide context in subsequent conversation turns.
     */
    public record ToolQueryResult(String toolName, String query, String result, long timestamp) {
        private static final int MAX_RESULT_LENGTH = 8000;

        public String truncatedResult() {
            if (result == null) return "";
            if (result.length() <= MAX_RESULT_LENGTH) return result;
            return result.substring(0, MAX_RESULT_LENGTH) + "\n... [truncated, " + result.length() + " chars total]";
        }
    }

    private static final List<Consumer<ToolResult>> listeners = new CopyOnWriteArrayList<>();
    private static final List<Consumer<ToolTokenUsage>> tokenListeners = new CopyOnWriteArrayList<>();
    private static final List<Consumer<ToolQueryResult>> queryListeners = new CopyOnWriteArrayList<>();

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

    public static void addQueryListener(Consumer<ToolQueryResult> listener) {
        if (listener != null) {
            queryListeners.add(listener);
        }
    }

    public static void removeQueryListener(Consumer<ToolQueryResult> listener) {
        queryListeners.remove(listener);
    }

    public static void clearListeners() {
        listeners.clear();
        tokenListeners.clear();
        queryListeners.clear();
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

    public static void notifyQueryResult(String toolName, String query, String result) {
        ToolQueryResult queryResult = new ToolQueryResult(toolName, query, result, System.currentTimeMillis());
        for (Consumer<ToolQueryResult> listener : queryListeners) {
            try {
                listener.accept(queryResult);
            } catch (Exception ignored) {}
        }
    }
}
