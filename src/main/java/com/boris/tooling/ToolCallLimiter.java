package com.boris.tooling;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limits the number of tool calls per user message to prevent infinite loops.
 * Reset this at the start of each user message processing.
 */
public class ToolCallLimiter {

    private static final int DEFAULT_MAX_CALLS = 5;
    private static final ThreadLocal<AtomicInteger> callCount = ThreadLocal.withInitial(() -> new AtomicInteger(0));
    private static volatile int maxCalls = DEFAULT_MAX_CALLS;

    private ToolCallLimiter() {}

    public static void reset() {
        callCount.get().set(0);
    }

    public static void setMaxCalls(int max) {
        maxCalls = max;
    }

    public static int getMaxCalls() {
        return maxCalls;
    }

    public static int getCurrentCount() {
        return callCount.get().get();
    }

    /**
     * Increment the call counter and check if limit is reached.
     * @return true if within limit, false if limit exceeded
     */
    public static boolean incrementAndCheck() {
        int current = callCount.get().incrementAndGet();
        return current <= maxCalls;
    }

    /**
     * Check if limit has been reached without incrementing.
     * @return true if limit reached
     */
    public static boolean isLimitReached() {
        return callCount.get().get() >= maxCalls;
    }

    public static String getLimitMessage() {
        return "Tool call limit reached (" + maxCalls + " calls). Please provide your response based on the information gathered so far.";
    }
}
