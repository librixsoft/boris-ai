package com.boris.tooling;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * Limits the number of tool calls per user message to prevent infinite loops.
 * Reset this at the start of each user message processing.
 */
public class ToolCallLimiter {

    private static final int DEFAULT_MAX_CALLS = 50;
    private static final int MAX_SAME_CALL_CONSECUTIVE = 3;
    private static final ThreadLocal<String> lastToolCall = ThreadLocal.withInitial(() -> "");
    private static final ThreadLocal<AtomicInteger> sameCallCount = ThreadLocal.withInitial(() -> new AtomicInteger(0));
    private static final ThreadLocal<AtomicInteger> callCount = ThreadLocal.withInitial(() -> new AtomicInteger(0));
    private static volatile int maxCalls = DEFAULT_MAX_CALLS;

    private ToolCallLimiter() {}

    public static void reset() {
        callCount.get().set(0);
        lastToolCall.set("");
        sameCallCount.get().set(0);
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

    /**
     * Check if the same tool+args is being called too many times consecutively (loop detection).
     * @param toolName name of the tool being called
     * @param args arguments/parameters of the call (will be converted to string for comparison)
     * @return true if within limit, false if same-call loop detected
     */
    public static boolean checkSameCallLoop(String toolName, Object args) {
        String callSignature = toolName + ":" + (args != null ? args.toString() : "null");
        String last = lastToolCall.get();
        if (callSignature.equals(last)) {
            int count = sameCallCount.get().incrementAndGet();
            if (count > MAX_SAME_CALL_CONSECUTIVE) {
                return false;
            }
        } else {
            lastToolCall.set(callSignature);
            sameCallCount.get().set(1);
        }
        return true;
    }

    public static String getLoopMessage(String toolName) {
        return "Loop detected: " + toolName + " called " + MAX_SAME_CALL_CONSECUTIVE + " times with same arguments. Stopping infinite loop.";
    }
}
