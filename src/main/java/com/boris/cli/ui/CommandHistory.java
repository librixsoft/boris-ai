package com.boris.cli.ui;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages command history navigation (like zsh/bash).
 * Handles storing, retrieving, and navigating through command history.
 */
public class CommandHistory {
    
    private final List<String> history = new ArrayList<>();
    private int historyIndex = -1;   // -1 = not navigating history
    
    /**
     * Add a command to history, avoiding duplicate consecutive entries.
     */
    public void addCommand(String command) {
        if (history.isEmpty() || !history.get(history.size() - 1).equals(command)) {
            history.add(command);
        }
    }
    
    /**
     * Reset navigation before each new prompt.
     */
    public void resetNavigation() {
        historyIndex = -1;
    }
    
    /**
     * Navigate to previous command (Arrow Up).
     * Returns the previous command or null if at the beginning.
     */
    public String navigatePrevious() {
        if (!history.isEmpty()) {
            if (historyIndex < 0) historyIndex = history.size();
            if (historyIndex > 0) {
                historyIndex--;
                return history.get(historyIndex);
            }
        }
        return null;
    }
    
    /**
     * Navigate to next command (Arrow Down).
     * Returns the next command, empty string if past the end, or null if not navigating.
     */
    public String navigateNext() {
        if (historyIndex >= 0) {
            historyIndex++;
            if (historyIndex >= history.size()) {
                historyIndex = history.size(); // past-end = empty line
                return "";
            } else {
                return history.get(historyIndex);
            }
        }
        return null;
    }
    
    /**
     * Check if currently navigating history.
     */
    public boolean isNavigating() {
        return historyIndex >= 0;
    }
    
    /**
     * Get the current history index.
     */
    public int getCurrentIndex() {
        return historyIndex;
    }
    
    /**
     * Get the history list.
     */
    public List<String> getHistory() {
        return new ArrayList<>(history);
    }
    
    /**
     * Get the size of history.
     */
    public int size() {
        return history.size();
    }
}
