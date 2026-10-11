package com.boris.cli.ui;

import com.googlecode.lanterna.TextColor;
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

        if (line.trim().startsWith("> ") && !line.trim().startsWith(">> ")) {
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING_MUTED : UiTheme.MUTED);
            graphics.putString(0, row, padOrTruncate(line, contentWidth));
            return;
        }

        if (line.startsWith(":: ")) {
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.ACCENT);
            graphics.putString(0, row, padOrTruncate(line, contentWidth));
            return;
        }

        if (line.matches("^\\s*\\d+\\s*│-\\s.*") || line.matches("^\\s*\\d+\\s*\\|-\\s.*")) {
            renderDiffLineWithNumber(graphics, line, row, contentWidth, isThinking, false);
            return;
        }

        if (line.matches("^\\s*\\d+\\s*│\\+\\s.*") || line.matches("^\\s*\\d+\\s*\\|\\+\\s.*")) {
            renderDiffLineWithNumber(graphics, line, row, contentWidth, isThinking, true);
            return;
        }

        if (line.startsWith("@@@") && line.endsWith("@@@")) {
            graphics.setBackgroundColor(UiTheme.BG_ELEVATED);
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.DIFF_HEADER);
            graphics.putString(0, row, padOrTruncate(line, contentWidth));
            return;
        }

        if (line.matches("^─+\\s*\\+\\d+\\s+-\\d+\\s*─+$")) {
            renderDiffStats(graphics, line, row, contentWidth, isThinking);
            return;
        }

        if (line.startsWith("- ") && !line.startsWith("- [ ]") && !line.startsWith("- [x]")) {
            String trimmed = line.substring(2).trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("[")) {
                renderSimpleDiffLine(graphics, line, row, contentWidth, isThinking, false);
                return;
            }
        }

        if (line.startsWith("+ ")) {
            renderSimpleDiffLine(graphics, line, row, contentWidth, isThinking, true);
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

    private void renderDiffLineWithNumber(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean isThinking, boolean isAdd) {
        int x = 0;
        int separatorIdx = line.indexOf("│");
        if (separatorIdx < 0) separatorIdx = line.indexOf("|");
        if (separatorIdx < 0) {
            graphics.setForegroundColor(isAdd ? UiTheme.DIFF_ADD : UiTheme.DIFF_REMOVE);
            graphics.putString(0, row, padOrTruncate(line, contentWidth));
            return;
        }

        String lineNumPart = line.substring(0, separatorIdx).trim();
        String rest = line.substring(separatorIdx);

        graphics.setBackgroundColor(UiTheme.BG);
        graphics.setForegroundColor(isThinking ? UiTheme.THINKING_MUTED : UiTheme.LINE_NUMBER);
        String numFormatted = String.format("%4s", lineNumPart);
        graphics.putString(x, row, numFormatted);
        x += numFormatted.length();

        graphics.setForegroundColor(isThinking ? UiTheme.THINKING_MUTED : UiTheme.DIFF_BORDER);
        graphics.putString(x, row, " │");
        x += 2;

        if (isAdd) {
            graphics.setBackgroundColor(isThinking ? UiTheme.BG : UiTheme.DIFF_ADD_BG);
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.DIFF_ADD);
        } else {
            graphics.setBackgroundColor(isThinking ? UiTheme.BG : UiTheme.DIFF_REMOVE_BG);
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.DIFF_REMOVE);
        }

        String codeContent = rest.length() > 2 ? rest.substring(2) : "";
        String symbol = isAdd ? "+" : "-";
        graphics.putString(x, row, symbol);
        x += 1;

        graphics.putString(x, row, " ");
        x += 1;

        String remaining = codeContent.startsWith(" ") ? codeContent.substring(1) : codeContent;
        int availableWidth = contentWidth - x;
        if (remaining.length() > availableWidth) {
            remaining = RenderUtils.truncateWithEllipsis(remaining, availableWidth);
        }

        List<SyntaxHighlighter.SyntaxToken> tokens = SyntaxHighlighter.tokenizeLine(remaining);
        TextColor bgColor = isThinking ? UiTheme.BG : (isAdd ? UiTheme.DIFF_ADD_BG : UiTheme.DIFF_REMOVE_BG);
        graphics.setBackgroundColor(bgColor);

        for (SyntaxHighlighter.SyntaxToken token : tokens) {
            if (x >= contentWidth) break;
            TextColor fgColor = isThinking ? UiTheme.THINKING : token.color();
            graphics.setForegroundColor(fgColor);
            String tokenText = token.text();
            int spaceLeft = contentWidth - x;
            if (tokenText.length() > spaceLeft) {
                tokenText = tokenText.substring(0, spaceLeft);
            }
            graphics.putString(x, row, tokenText);
            x += tokenText.length();
        }

        if (x < contentWidth) {
            graphics.setBackgroundColor(bgColor);
            graphics.putString(x, row, padOrTruncate("", contentWidth - x));
        }
    }

    private void renderDiffStats(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean isThinking) {
        graphics.setBackgroundColor(UiTheme.BG);
        int x = 0;

        int plusIdx = line.indexOf('+');
        int minusIdx = line.indexOf('-');

        if (plusIdx < 0 || minusIdx < 0) {
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING_MUTED : UiTheme.DIFF_BORDER);
            graphics.putString(0, row, padOrTruncate(line, contentWidth));
            return;
        }

        String prefix = line.substring(0, plusIdx);
        graphics.setForegroundColor(isThinking ? UiTheme.THINKING_MUTED : UiTheme.DIFF_BORDER);
        graphics.putString(x, row, prefix);
        x += prefix.length();

        int spaceAfterPlus = line.indexOf(' ', plusIdx);
        if (spaceAfterPlus < 0) spaceAfterPlus = minusIdx;
        String addPart = line.substring(plusIdx, spaceAfterPlus);
        graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.DIFF_ADD);
        graphics.putString(x, row, addPart);
        x += addPart.length();

        String middle = line.substring(spaceAfterPlus, minusIdx);
        graphics.setForegroundColor(isThinking ? UiTheme.THINKING_MUTED : UiTheme.DIFF_BORDER);
        graphics.putString(x, row, middle);
        x += middle.length();

        int endOfMinus = minusIdx + 1;
        while (endOfMinus < line.length() && (Character.isDigit(line.charAt(endOfMinus)) || line.charAt(endOfMinus) == ' ')) {
            if (line.charAt(endOfMinus) == ' ' && endOfMinus > minusIdx + 1) break;
            endOfMinus++;
        }
        String removePart = line.substring(minusIdx, endOfMinus);
        graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.DIFF_REMOVE);
        graphics.putString(x, row, removePart);
        x += removePart.length();

        String suffix = line.substring(endOfMinus);
        graphics.setForegroundColor(isThinking ? UiTheme.THINKING_MUTED : UiTheme.DIFF_BORDER);
        graphics.putString(x, row, suffix);
        x += suffix.length();

        if (x < contentWidth) {
            graphics.putString(x, row, padOrTruncate("", contentWidth - x));
        }
    }

    private void renderSimpleDiffLine(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean isThinking, boolean isAdd) {
        TextColor bgColor = isThinking ? UiTheme.BG : (isAdd ? UiTheme.DIFF_ADD_BG : UiTheme.DIFF_REMOVE_BG);
        TextColor symbolColor = isThinking ? UiTheme.THINKING : (isAdd ? UiTheme.DIFF_ADD : UiTheme.DIFF_REMOVE);

        graphics.setBackgroundColor(bgColor);
        graphics.setForegroundColor(symbolColor);

        String prefix = line.substring(0, 2);
        graphics.putString(0, row, prefix);
        int x = 2;

        String code = line.substring(2);
        int availableWidth = contentWidth - x;
        if (code.length() > availableWidth) {
            code = RenderUtils.truncateWithEllipsis(code, availableWidth);
        }

        List<SyntaxHighlighter.SyntaxToken> tokens = SyntaxHighlighter.tokenizeLine(code);
        for (SyntaxHighlighter.SyntaxToken token : tokens) {
            if (x >= contentWidth) break;
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING : token.color());
            String tokenText = token.text();
            int spaceLeft = contentWidth - x;
            if (tokenText.length() > spaceLeft) {
                tokenText = tokenText.substring(0, spaceLeft);
            }
            graphics.putString(x, row, tokenText);
            x += tokenText.length();
        }

        if (x < contentWidth) {
            graphics.putString(x, row, padOrTruncate("", contentWidth - x));
        }
    }
}
