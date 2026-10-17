package com.boris.tooling.tool;

import java.nio.file.Path;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which .boris/CONTEXT.md files have been read in this session.
 * Used to ensure Boris reads context before writing code.
 */
public class ContextTracker {

    private static final Set<String> READ_CONTEXTS = ConcurrentHashMap.newKeySet();

    /**
     * Mark a context file as read.
     * Called when batch_read_files reads a CONTEXT.md file.
     */
    public static void markAsRead(String path) {
        if (path != null && path.contains("CONTEXT.md")) {
            // Normalize to project root
            String normalized = path.replace("/.boris/CONTEXT.md", "")
                                   .replace("\\.boris\\CONTEXT.md", "");
            READ_CONTEXTS.add(normalized);
        }
    }

    /**
     * Check if context for a project has been read.
     */
    public static boolean wasRead(Path projectRoot) {
        if (projectRoot == null) return false;
        return READ_CONTEXTS.contains(projectRoot.toString());
    }

    /**
     * Clear all tracked contexts (for testing or session reset).
     */
    public static void clear() {
        READ_CONTEXTS.clear();
    }
}
