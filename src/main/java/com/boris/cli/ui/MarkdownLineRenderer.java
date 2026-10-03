package com.boris.cli.ui;

import com.googlecode.lanterna.gui2.TextGUIGraphics;

import java.util.ArrayList;
import java.util.List;

import static com.boris.cli.ui.RenderUtils.padOrTruncate;

public class MarkdownLineRenderer {

    private static final String[] OPEN_TAGS = {"<think>", "<thought>", "<thinking>", "<reasoning>"};
    private static final String[] CLOSE_TAGS = {"</think>", "</thought>", "</thinking>", "</reasoning>"};

    public static class TextSegment {
        private final String text;
        private final boolean isThinking;

        public TextSegment(String text, boolean isThinking) {
            this.text = text;
            this.isThinking = isThinking;
        }

        public String getText() {
            return text;
        }

        public boolean isThinking() {
            return isThinking;
        }
    }

    public static int findNextOpenTag(String lower, int fromIndex) {
        int minIndex = -1;
        for (String tag : OPEN_TAGS) {
            int idx = lower.indexOf(tag, fromIndex);
            if (idx != -1 && (minIndex == -1 || idx < minIndex)) {
                minIndex = idx;
            }
        }
        return minIndex;
    }

    public static int findNextCloseTag(String lower, int fromIndex) {
        int minIndex = -1;
        for (String tag : CLOSE_TAGS) {
            int idx = lower.indexOf(tag, fromIndex);
            if (idx != -1 && (minIndex == -1 || idx < minIndex)) {
                minIndex = idx;
            }
        }
        return minIndex;
    }

    public static int getOpenTagLengthAt(String lower, int index) {
        for (String tag : OPEN_TAGS) {
            if (lower.startsWith(tag, index)) {
                return tag.length();
            }
        }
        return 0;
    }

    public static int getCloseTagLengthAt(String lower, int index) {
        for (String tag : CLOSE_TAGS) {
            if (lower.startsWith(tag, index)) {
                return tag.length();
            }
        }
        return 0;
    }

    public static boolean updateThinkingState(String line, boolean currentlyInThinking) {
        if (line == null || line.isEmpty()) {
            return currentlyInThinking;
        }
        String lower = line.toLowerCase();
        int idx = 0;
        boolean state = currentlyInThinking;
        while (idx < lower.length()) {
            int nextOpen = findNextOpenTag(lower, idx);
            int nextClose = findNextCloseTag(lower, idx);

            if (nextOpen == -1 && nextClose == -1) {
                break;
            }

            if (!state && nextOpen != -1 && (nextClose == -1 || nextOpen < nextClose)) {
                state = true;
                idx = nextOpen + getOpenTagLengthAt(lower, nextOpen);
            } else if (state && nextClose != -1 && (nextOpen == -1 || nextClose < nextOpen)) {
                state = false;
                idx = nextClose + getCloseTagLengthAt(lower, nextClose);
            } else if (nextOpen != -1 && nextClose != -1 && nextOpen == nextClose) {
                idx++;
            } else if (nextOpen != -1 && (nextClose == -1 || nextOpen < nextClose)) {
                idx = nextOpen + getOpenTagLengthAt(lower, nextOpen);
            } else if (nextClose != -1) {
                idx = nextClose + getCloseTagLengthAt(lower, nextClose);
            } else {
                idx++;
            }
        }
        return state;
    }

    private static void addSegment(List<TextSegment> segments, String text, boolean isThinking) {
        if (text == null || text.isEmpty()) {
            return;
        }
        if (!segments.isEmpty() && segments.get(segments.size() - 1).isThinking() == isThinking) {
            TextSegment prev = segments.remove(segments.size() - 1);
            segments.add(new TextSegment(prev.getText() + text, isThinking));
        } else {
            segments.add(new TextSegment(text, isThinking));
        }
    }

    public static List<TextSegment> parseLineSegments(String line, boolean startInThinking) {
        List<TextSegment> segments = new ArrayList<>();
        if (line == null || line.isEmpty()) {
            return segments;
        }
        String lower = line.toLowerCase();
        int idx = 0;
        boolean currentThinking = startInThinking;
        int segStart = 0;

        while (idx < line.length()) {
            int nextOpen = findNextOpenTag(lower, idx);
            int nextClose = findNextCloseTag(lower, idx);

            if (nextOpen == -1 && nextClose == -1) {
                break;
            }

            if (!currentThinking && nextOpen != -1 && (nextClose == -1 || nextOpen < nextClose)) {
                if (nextOpen > segStart) {
                    addSegment(segments, line.substring(segStart, nextOpen), false);
                }
                int tagLen = getOpenTagLengthAt(lower, nextOpen);
                addSegment(segments, line.substring(nextOpen, nextOpen + tagLen), true);
                segStart = nextOpen + tagLen;
                idx = segStart;
                currentThinking = true;
            } else if (currentThinking && nextClose != -1 && (nextOpen == -1 || nextClose < nextOpen)) {
                int tagLen = getCloseTagLengthAt(lower, nextClose);
                int tagEnd = nextClose + tagLen;
                if (tagEnd > segStart) {
                    addSegment(segments, line.substring(segStart, tagEnd), true);
                }
                segStart = tagEnd;
                idx = segStart;
                currentThinking = false;
            } else {
                idx++;
            }
        }

        if (segStart < line.length()) {
            addSegment(segments, line.substring(segStart), currentThinking);
        }

        return segments;
    }

    public void render(TextGUIGraphics graphics, String line, int row, int contentWidth) {
        render(graphics, line, row, contentWidth, false, true);
    }

    public void render(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean inThinking) {
        render(graphics, line, row, contentWidth, inThinking, true);
    }

    public void render(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean inThinking, boolean thinkingEnabled) {
        if (line == null) {
            line = "";
        }

        List<TextSegment> segments = parseLineSegments(line, inThinking);
        if (segments.isEmpty()) {
            graphics.setBackgroundColor(UiTheme.BG);
            graphics.putString(0, row, padOrTruncate("", contentWidth));
            return;
        }

        if (!thinkingEnabled) {
            List<TextSegment> nonThinking = new ArrayList<>();
            for (TextSegment seg : segments) {
                if (!seg.isThinking()) {
                    nonThinking.add(seg);
                }
            }
            if (nonThinking.isEmpty()) {
                graphics.setBackgroundColor(UiTheme.BG);
                graphics.putString(0, row, padOrTruncate("", contentWidth));
                return;
            }
            if (nonThinking.size() == 1) {
                renderSingleSegmentLine(graphics, nonThinking.get(0).getText(), row, contentWidth, false);
                return;
            }
            renderMultiSegmentLine(graphics, nonThinking, row, contentWidth);
            return;
        }

        if (segments.size() == 1) {
            renderSingleSegmentLine(graphics, line, row, contentWidth, segments.get(0).isThinking());
            return;
        }

        renderMultiSegmentLine(graphics, segments, row, contentWidth);
    }

    private void renderSingleSegmentLine(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean isThinking) {
        graphics.setBackgroundColor(UiTheme.BG);

        if (line.startsWith("###")) {
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING_BOLD : UiTheme.ACCENT);
            String content = line.substring(3).trim();
            graphics.putString(0, row, padOrTruncate("   " + content, contentWidth));
            return;
        }
        if (line.startsWith("##")) {
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING_BOLD : UiTheme.ACCENT);
            String content = line.substring(2).trim();
            graphics.putString(0, row, padOrTruncate("  " + content, contentWidth));
            return;
        }
        if (line.startsWith("#")) {
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING_BOLD : UiTheme.ACCENT);
            String content = line.substring(1).trim();
            graphics.putString(0, row, padOrTruncate(" " + content, contentWidth));
            return;
        }

        if (line.trim().startsWith(">")) {
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING_MUTED : UiTheme.MUTED);
            graphics.putString(0, row, padOrTruncate(line, contentWidth));
            return;
        }

        if (line.trim().startsWith("```") || line.trim().startsWith("``")) {
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.USERC);
            graphics.setBackgroundColor(UiTheme.BG_ELEVATED);
            graphics.putString(0, row, padOrTruncate(line, contentWidth));
            return;
        }

        if (line.contains("`")) {
            renderInlineCode(graphics, line, row, contentWidth, isThinking);
            return;
        }

        if (line.contains("**")) {
            renderBoldText(graphics, line, row, contentWidth, isThinking);
            return;
        }

        if (line.contains("*") && !line.contains("**")) {
            renderItalicText(graphics, line, row, contentWidth, isThinking);
            return;
        }

        if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.USERC);
            graphics.putString(0, row, padOrTruncate(line, contentWidth));
            return;
        }

        if (line.trim().matches("^\\d+\\..*")) {
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.USERC);
            graphics.putString(0, row, padOrTruncate(line, contentWidth));
            return;
        }

        graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
        graphics.putString(0, row, padOrTruncate(line, contentWidth));
    }

    private void renderMultiSegmentLine(TextGUIGraphics graphics, List<TextSegment> segments, int row, int contentWidth) {
        graphics.setBackgroundColor(UiTheme.BG);
        int currentX = 0;

        for (TextSegment segment : segments) {
            if (currentX >= contentWidth) {
                break;
            }
            currentX = renderSegment(graphics, segment.getText(), row, currentX, contentWidth, segment.isThinking());
        }

        if (currentX < contentWidth) {
            graphics.setBackgroundColor(UiTheme.BG);
            graphics.putString(currentX, row, padOrTruncate("", contentWidth - currentX));
        }
    }

    private int renderSegment(TextGUIGraphics graphics, String text, int row, int startX, int contentWidth, boolean isThinking) {
        graphics.setBackgroundColor(UiTheme.BG);
        int x = startX;
        StringBuilder current = new StringBuilder();
        boolean inCode = false;

        for (int i = 0; i < text.length() && x < contentWidth; i++) {
            char c = text.charAt(i);

            if (c == '`' && (i == 0 || text.charAt(i - 1) != '\\')) {
                if (inCode) {
                    graphics.setBackgroundColor(UiTheme.BG_ELEVATED);
                    graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.USERC);
                    graphics.putString(x, row, current.toString());
                    x += current.length();
                    current.setLength(0);
                    inCode = false;
                    graphics.setBackgroundColor(UiTheme.BG);
                    graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
                } else {
                    if (current.length() > 0) {
                        graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
                        graphics.putString(x, row, current.toString());
                        x += current.length();
                        current.setLength(0);
                    }
                    inCode = true;
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0 && x < contentWidth) {
            if (inCode) {
                graphics.setBackgroundColor(UiTheme.BG_ELEVATED);
                graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.USERC);
            } else {
                graphics.setBackgroundColor(UiTheme.BG);
                graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
            }
            int available = contentWidth - x;
            String toPrint = current.length() > available ? current.substring(0, available) : current.toString();
            graphics.putString(x, row, toPrint);
            x += toPrint.length();
        }

        return x;
    }

    private void renderInlineCode(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean isThinking) {
        graphics.setBackgroundColor(UiTheme.BG);
        int x = 0;
        StringBuilder current = new StringBuilder();
        boolean inCode = false;

        for (int i = 0; i < line.length() && x < contentWidth; i++) {
            char c = line.charAt(i);

            if (c == '`' && (i == 0 || line.charAt(i - 1) != '\\')) {
                if (inCode) {
                    graphics.setBackgroundColor(UiTheme.BG_ELEVATED);
                    graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.USERC);
                    graphics.putString(x, row, current.toString());
                    x += current.length();
                    current.setLength(0);
                    inCode = false;
                    graphics.setBackgroundColor(UiTheme.BG);
                    graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
                } else {
                    inCode = true;
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            if (inCode) {
                graphics.setBackgroundColor(UiTheme.BG_ELEVATED);
                graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.USERC);
            } else {
                graphics.setBackgroundColor(UiTheme.BG);
                graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
            }
            graphics.putString(x, row, padOrTruncate(current.toString(), contentWidth - x));
        }
    }

    private void renderBoldText(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean isThinking) {
        graphics.setBackgroundColor(UiTheme.BG);
        int x = 0;
        StringBuilder current = new StringBuilder();
        boolean inBold = false;

        for (int i = 0; i < line.length() && x < contentWidth; i++) {
            char c = line.charAt(i);

            if (c == '*' && i + 1 < line.length() && line.charAt(i + 1) == '*') {
                if (inBold) {
                    graphics.setForegroundColor(isThinking ? UiTheme.THINKING_BOLD : UiTheme.ACCENT);
                    graphics.putString(x, row, current.toString());
                    x += current.length();
                    current.setLength(0);
                    inBold = false;
                    i++;
                    graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
                } else {
                    inBold = true;
                    i++;
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            if (inBold) {
                graphics.setForegroundColor(isThinking ? UiTheme.THINKING_BOLD : UiTheme.ACCENT);
            } else {
                graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
            }
            graphics.putString(x, row, padOrTruncate(current.toString(), contentWidth - x));
        }
    }

    private void renderItalicText(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean isThinking) {
        graphics.setBackgroundColor(UiTheme.BG);
        int x = 0;
        StringBuilder current = new StringBuilder();
        boolean inItalic = false;

        for (int i = 0; i < line.length() && x < contentWidth; i++) {
            char c = line.charAt(i);

            if (c == '*' && (i == 0 || line.charAt(i - 1) != '*')) {
                if (inItalic) {
                    graphics.setForegroundColor(isThinking ? UiTheme.THINKING_BOLD : UiTheme.SELECTED_BG);
                    graphics.putString(x, row, current.toString());
                    x += current.length();
                    current.setLength(0);
                    inItalic = false;
                    graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
                } else {
                    inItalic = true;
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            if (inItalic) {
                graphics.setForegroundColor(isThinking ? UiTheme.THINKING_BOLD : UiTheme.SELECTED_BG);
            } else {
                graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
            }
            graphics.putString(x, row, padOrTruncate(current.toString(), contentWidth - x));
        }
    }
}
