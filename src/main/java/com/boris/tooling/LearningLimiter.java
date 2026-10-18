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
    private static final int MAX_LEARNING_CALLS_DURING_ERROR_FIX = 5;

    private static int totalLearningCalls = 0;
    private static final Map<String, Integer> conceptCallCounts = new HashMap<>();
    private static boolean hasCompileErrors = false;
    private static boolean hasDocumentedSinceError = false;
    private static int learningCallsDuringErrorFix = 0;
    private static final java.util.Set<String> reviewedClassesDuringErrorFix = new java.util.HashSet<>();
    
    /**
     * Mark that the agent has compile errors and should stop learning.
     * Called after maven_build returns errors.
     */
    public static synchronized void markCompileErrors() {
        hasCompileErrors = true;
        learningCallsDuringErrorFix = 0;
    }
    
    /**
     * Clear the compile error flag (called when agent edits a file).
     */
    public static synchronized void clearCompileErrors() {
        hasCompileErrors = false;
        hasDocumentedSinceError = false;
        learningCallsDuringErrorFix = 0;
        reviewedClassesDuringErrorFix.clear();
    }

    /**
     * Mark that the agent has documented itself after compile errors.
     * Called when query_framework_knowledge or similar learning tools are used.
     */
    public static synchronized void markDocumented() {
        hasDocumentedSinceError = true;
    }

    /**
     * Check if the agent has documented since the last compile error.
     */
    public static synchronized boolean hasDocumentedSinceError() {
        return hasDocumentedSinceError;
    }
    
    /**
     * Check if a learning tool call is allowed.
     *
     * @param concept The concept being queried (e.g., class name, framework)
     * @return true if allowed, false if limit reached or blocked by compile errors
     */
    public static synchronized boolean allowLearningCall(String concept) {
        String normalizedConcept = concept.toLowerCase().trim();

        // When fixing errors, use the error-fix limit (max 5 classes)
        if (hasCompileErrors) {
            // If class was already reviewed during this error fix, allow it without counting
            if (reviewedClassesDuringErrorFix.contains(normalizedConcept)) {
                // Mark that agent has documented itself
                hasDocumentedSinceError = true;
                return true;
            }

            if (learningCallsDuringErrorFix >= MAX_LEARNING_CALLS_DURING_ERROR_FIX) {
                return false;
            }
            learningCallsDuringErrorFix++;
            reviewedClassesDuringErrorFix.add(normalizedConcept);
        } else {
            // Normal session: check total session limit
            if (totalLearningCalls >= MAX_LEARNING_CALLS_PER_SESSION) {
                return false;
            }
            totalLearningCalls++;
        }

        // Check per-concept limit
        int conceptCount = conceptCallCounts.getOrDefault(normalizedConcept, 0);
        if (conceptCount >= MAX_CALLS_PER_CONCEPT) {
            return false;
        }
        conceptCallCounts.put(normalizedConcept, conceptCount + 1);

        // Mark that agent has documented itself
        hasDocumentedSinceError = true;

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
        hasDocumentedSinceError = false;
    }

    /**
     * Check if edit tools are allowed.
     * If there are compile errors, editing is only allowed if the agent has documented itself first.
     * 
     * @return true if editing is allowed, false otherwise
     */
    public static synchronized boolean allowEdit() {
        // If no compile errors, editing is always allowed
        if (!hasCompileErrors) {
            return true;
        }

        // If there are compile errors, only allow editing if documented
        return hasDocumentedSinceError;
    }
    
    /**
     * Get status message for the user.
     */
    public static synchronized String getStatus() {
        if (hasCompileErrors) {
            return String.format("Corrigiendo errores: %d/%d clases documentadas (max 5 por corrección). Documenta las clases antes de editar.",
                learningCallsDuringErrorFix, MAX_LEARNING_CALLS_DURING_ERROR_FIX);
        }
        return String.format("Learning calls: %d/%d (session), remaining: %d",
            totalLearningCalls, MAX_LEARNING_CALLS_PER_SESSION, getRemainingCalls());
    }
}
