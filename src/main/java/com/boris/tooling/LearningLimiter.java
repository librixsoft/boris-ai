package com.boris.tooling;

import java.util.HashMap;
import java.util.Map;

/**
 * Service to limit learning tool calls to prevent infinite loops.
 * Enforces maximum call counts per session and blocks learning after compile errors.
 */
public class LearningLimiter {
    
    private static final int MAX_LEARNING_CALLS_PER_SESSION = 5;
    private static final int MAX_CALLS_PER_CONCEPT = 3;
    
    private static int totalLearningCalls = 0;
    private static final Map<String, Integer> conceptCallCounts = new HashMap<>();
    private static boolean hasCompileErrors = false;
    
    /**
     * Mark that the agent has compile errors and should stop learning.
     * Called after maven_build returns errors.
     */
    public static synchronized void markCompileErrors() {
        hasCompileErrors = true;
    }
    
    /**
     * Clear the compile error flag (called when agent edits a file).
     */
    public static synchronized void clearCompileErrors() {
        hasCompileErrors = false;
    }
    
    /**
     * Check if a learning tool call is allowed.
     * 
     * @param concept The concept being queried (e.g., class name, framework)
     * @return true if allowed, false if limit reached or blocked by compile errors
     */
    public static synchronized boolean allowLearningCall(String concept) {
        // Block learning if there are uncorrected compile errors
        if (hasCompileErrors) {
            return false;
        }
        
        // Check total session limit
        if (totalLearningCalls >= MAX_LEARNING_CALLS_PER_SESSION) {
            return false;
        }
        
        // Check per-concept limit
        String normalizedConcept = concept.toLowerCase().trim();
        int conceptCount = conceptCallCounts.getOrDefault(normalizedConcept, 0);
        if (conceptCount >= MAX_CALLS_PER_CONCEPT) {
            return false;
        }
        
        // Increment counters
        totalLearningCalls++;
        conceptCallCounts.put(normalizedConcept, conceptCount + 1);
        
        return true;
    }
    
    /**
     * Get remaining learning calls for the session.
     */
    public static synchronized int getRemainingCalls() {
        return MAX_LEARNING_CALLS_PER_SESSION - totalLearningCalls;
    }
    
    /**
     * Get remaining calls for a specific concept.
     */
    public static synchronized int getRemainingCallsForConcept(String concept) {
        String normalizedConcept = concept.toLowerCase().trim();
        int conceptCount = conceptCallCounts.getOrDefault(normalizedConcept, 0);
        return MAX_CALLS_PER_CONCEPT - conceptCount;
    }
    
    /**
     * Reset all counters (for new sessions or when editing starts).
     */
    public static synchronized void reset() {
        totalLearningCalls = 0;
        conceptCallCounts.clear();
        hasCompileErrors = false;
    }
    
    /**
     * Get status message for the user.
     */
    public static synchronized String getStatus() {
        if (hasCompileErrors) {
            return "Hay errores de compilación sin corregir. Corrige el código con la información que ya tienes.";
        }
        return String.format("Learning calls: %d/%d (session), remaining: %d",
            totalLearningCalls, MAX_LEARNING_CALLS_PER_SESSION, getRemainingCalls());
    }
}
