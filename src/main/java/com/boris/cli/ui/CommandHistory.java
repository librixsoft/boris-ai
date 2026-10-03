package com.boris.cli.ui;

import java.util.ArrayList;
import java.util.List;

public class CommandHistory {

    private final List<String> entries = new ArrayList<>();
    private int index = -1;
    private String draftBeforeHistory = "";

    public boolean hasEntries() {
        return !entries.isEmpty();
    }

    public boolean canGoOlder() {
        return index < entries.size() - 1;
    }

    public boolean canGoNewer() {
        return index > 0;
    }

    public boolean navigating() {
        return index != -1;
    }

    public void beginNavigation(String currentDraft) {
        if (index == -1) {
            draftBeforeHistory = currentDraft;
        }
    }

    public String goOlder() {
        index++;
        return entries.get(entries.size() - 1 - index);
    }

    public String goNewer() {
        index--;
        return entries.get(entries.size() - 1 - index);
    }

    public String restoreDraft() {
        index = -1;
        return draftBeforeHistory;
    }

    public void resetNavigation() {
        index = -1;
        draftBeforeHistory = "";
    }

    public void record(String command) {
        if (command.isEmpty()) {
            throw new IllegalArgumentException("command must not be empty");
        }
        if (entries.isEmpty() || !entries.get(entries.size() - 1).equals(command)) {
            entries.add(command);
        }
        resetNavigation();
    }
}
