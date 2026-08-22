package com.boris.tooling.tool;

public final class FileSizePolicy {

    private static final double CHARS_PER_TOKEN = 3.5;
    private static final int CONTEXT_WINDOW_FILE_SHARE_DIVISOR = 4;

    private FileSizePolicy() {
    }

    public static int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / CHARS_PER_TOKEN);
    }

    public static int maxFileTokens(int contextWindow) {
        return contextWindow / CONTEXT_WINDOW_FILE_SHARE_DIVISOR;
    }

    public static int maxFileChars(int contextWindow) {
        return (int) Math.floor(maxFileTokens(contextWindow) * CHARS_PER_TOKEN);
    }

    public static String tooLargeMessage(String toolName, String target, int estimatedTokens, int maxTokens, int maxChars) {
        return "Error: " + toolName + " rejected because content exceeds the model context limit."
                + " Target: " + target
                + ". Estimated content tokens: " + estimatedTokens
                + ". Max allowed per tool call: " + maxTokens + " tokens (~" + maxChars + " characters)."
                + " You MUST split this work into smaller parts:"
                + " create the file with write_file containing only the first part,"
                + " then append the remaining parts using apply_edit"
                + " or split the task into several smaller files.";
    }
}
