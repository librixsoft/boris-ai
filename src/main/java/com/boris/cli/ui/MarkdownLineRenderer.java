package com.boris.cli.ui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.boris.cli.ui.RenderUtils.padOrTruncateByDisplayWidth;

public class MarkdownLineRenderer {

    private static final String[] OPEN_TAGS = {"<think>", "<thought>", "<thinking>", "<reasoning>"};
    private static final String[] CLOSE_TAGS = {"</think>", "</thought>", "</thinking>", "</reasoning>"};
    private static final String CODE_FENCE = "```";

    private static final java.util.regex.Pattern THINKING_GAP_PATTERN = java.util.regex.Pattern.compile(
            "((?:</think>|</thinking>|</thought>|</reasoning>))\\s*(?!<(?:think|thinking|thought|reasoning)>)(?=\\S)",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    /**
     * Ensures a blank line separates the last thinking block from the assistant response.
     *
     * @param text raw transcript text
     * @return text with a blank line inserted after the closing thinking tag when response text follows
     */
    public static String ensureBlankLineAfterThinking(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        return THINKING_GAP_PATTERN.matcher(text).replaceAll("$1\n\n");
    }

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

    public static boolean isCodeFence(String line) {
        if (line == null) return false;
        String trimmed = line.trim();
        return trimmed.startsWith(CODE_FENCE);
    }

    public static boolean updateCodeBlockState(String line, boolean currentlyInCodeBlock) {
        if (isCodeFence(line)) {
            return !currentlyInCodeBlock;
        }
        return currentlyInCodeBlock;
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
                segStart = nextOpen + tagLen;
                if (segments.isEmpty() || segments.get(segments.size() - 1).getText().endsWith(" ")) {
                    while (segStart < line.length() && line.charAt(segStart) == ' ') {
                        segStart++;
                    }
                }
                idx = segStart;
                currentThinking = true;
            } else if (currentThinking && nextClose != -1 && (nextOpen == -1 || nextClose < nextOpen)) {
                int tagLen = getCloseTagLengthAt(lower, nextClose);
                int tagEnd = nextClose + tagLen;
                if (nextClose > segStart) {
                    addSegment(segments, line.substring(segStart, nextClose), true);
                }
                segStart = tagEnd;
                idx = segStart;
                currentThinking = false;
            } else if (currentThinking && nextOpen != -1 && (nextClose == -1 || nextOpen < nextClose)) {
                if (nextOpen > segStart) {
                    addSegment(segments, line.substring(segStart, nextOpen), true);
                }
                int tagLen = getOpenTagLengthAt(lower, nextOpen);
                segStart = nextOpen + tagLen;
                idx = segStart;
            } else if (!currentThinking && nextClose != -1) {
                idx = nextClose + getCloseTagLengthAt(lower, nextClose);
                segStart = Math.max(segStart, idx);
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
        render(graphics, line, row, contentWidth, false, true, false);
    }

    public void render(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean inThinking) {
        render(graphics, line, row, contentWidth, inThinking, true, false);
    }

    public void render(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean inThinking, boolean thinkingEnabled) {
        render(graphics, line, row, contentWidth, inThinking, thinkingEnabled, false);
    }

    private static final java.util.regex.Pattern QUEUED_PATTERN = java.util.regex.Pattern.compile("<queued:#(\\d+)>");
    private static final java.util.regex.Pattern OLD_TEXT_LABEL_PATTERN =
        java.util.regex.Pattern.compile("^\\s*(?:[-*+]?\\s*)?(?:\\*{0,2}\\s*)old[ _-]?text\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);
    private static final java.util.regex.Pattern NEW_TEXT_LABEL_PATTERN =
        java.util.regex.Pattern.compile("^\\s*(?:[-*+]?\\s*)?(?:\\*{0,2}\\s*)new[ _-]?text\\b",
            java.util.regex.Pattern.CASE_INSENSITIVE);

    public void render(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean inThinking, boolean thinkingEnabled, boolean inCodeBlock) {
        if (line == null) {
            line = "";
        }

        java.util.regex.Matcher queuedMatcher = QUEUED_PATTERN.matcher(line);
        if (queuedMatcher.find()) {
            String displayLine = queuedMatcher.replaceFirst("[QUEUED]");
            graphics.setBackgroundColor(UiTheme.BG);
            graphics.setForegroundColor(UiTheme.QUEUED);
            renderString(graphics, displayLine, row, 0, contentWidth, UiTheme.QUEUED, UiTheme.BG);
            return;
        }

        if (line.startsWith(TextWrap.USER_CONT_MARKER)) {
            String content = line.substring(TextWrap.USER_CONT_MARKER.length());
            graphics.setBackgroundColor(UiTheme.BG);
            graphics.setForegroundColor(UiTheme.USERC);
            renderString(graphics, content, row, 0, contentWidth, UiTheme.USERC, UiTheme.BG);
            return;
        }

        if (line.startsWith(TextWrap.USER_LINE_PREFIX)) {
            graphics.setBackgroundColor(UiTheme.BG);
            graphics.setForegroundColor(UiTheme.USERC);
            renderString(graphics, line, row, 0, contentWidth, UiTheme.USERC, UiTheme.BG);
            return;
        }

        if (inCodeBlock && !isCodeFence(line)) {
            renderCodeLine(graphics, line, row, contentWidth, inThinking);
            return;
        }

        List<TextSegment> segments = parseLineSegments(line, inThinking);
        if (segments.isEmpty()) {
            graphics.setBackgroundColor(UiTheme.BG);
            renderString(graphics, padOrTruncateByDisplayWidth("", contentWidth), row, 0, contentWidth, UiTheme.FG, UiTheme.BG);
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
                renderString(graphics, padOrTruncateByDisplayWidth("", contentWidth), row, 0, contentWidth, UiTheme.FG, UiTheme.BG);
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
            renderSingleSegmentLine(graphics, segments.get(0).getText(), row, contentWidth, segments.get(0).isThinking());
            return;
        }

        renderMultiSegmentLine(graphics, segments, row, contentWidth);
    }

    private static void renderString(TextGUIGraphics graphics, String text, int row, int x, int contentWidth,
                                     TextColor fg, TextColor bg) {
        if (text == null || text.isEmpty()) {
            return;
        }
        CodepointAwareTextRenderer.renderWithStyle(graphics, text, row, x, contentWidth, fg, bg, EnumSet.noneOf(SGR.class));
    }

    private void renderSingleSegmentLine(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean isThinking) {
        graphics.setBackgroundColor(UiTheme.BG);

        // Tool result lines must be checked FIRST, before any other formatting,
        // to prevent raw JSON from leaking through inline code/bold/italic/diff renderers.
        if (ToolResultRenderer.isToolResultLine(line)) {
            String formatted;
            try {
                formatted = ToolResultRenderer.formatToolResult(line);
            } catch (InvalidToolResultPathException e) {
                formatted = ToolResultRenderer.INVALID_PATH_DISPLAY;
            } catch (Exception e) {
                // Si el JSON es inválido, renderizar la línea original con TOOL_DETAIL
                // para mantener consistencia de color sin romper el renderizado.
                formatted = line;
            }
            graphics.setForegroundColor(UiTheme.TOOL_DETAIL);
            renderString(graphics, formatted, row, 0, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
            return;
        }

        if (line.startsWith("###")) {
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING_BOLD : UiTheme.ACCENT);
            String content = line.substring(3).trim();
            renderString(graphics, "   " + content, row, 0, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
            return;
        }
        if (line.startsWith("##")) {
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING_BOLD : UiTheme.ACCENT);
            String content = line.substring(2).trim();
            renderString(graphics, "  " + content, row, 0, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
            return;
        }
        if (line.startsWith("#")) {
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING_BOLD : UiTheme.ACCENT);
            String content = line.substring(1).trim();
            renderString(graphics, " " + content, row, 0, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
            return;
        }

        if (line.trim().startsWith("> ") && !line.trim().startsWith(">> ")) {
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING_MUTED : UiTheme.MUTED);
            renderString(graphics, line, row, 0, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
            return;
        }

        if (line.startsWith(":: ")) {
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.ACCENT);
            renderString(graphics, line, row, 0, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
            return;
        }

        if (line.matches("^\\s*\\d+\\s*│-\\s.*") || line.matches("^\\s*\\d+\\s*\\|-\\s.*")) {
            renderDiffLineWithNumber(graphics, line, row, contentWidth, isThinking, false, false);
            return;
        }

        if (line.matches("^\\s*\\d+\\s*│\\+\\s.*") || line.matches("^\\s*\\d+\\s*\\|\\+\\s.*")) {
            renderDiffLineWithNumber(graphics, line, row, contentWidth, isThinking, true, false);
            return;
        }

        if (line.matches("^\\s*\\d+\\s*│[✗⚠]\\s.*") || line.matches("^\\s*\\d+\\s*\\|[✗⚠]\\s.*")) {
            renderDiffLineWithNumber(graphics, line, row, contentWidth, isThinking, false, true);
            return;
        }

        // Diff continuation lines (wrapped)
        if (line.startsWith(TextWrap.DIFF_CONT_MARKER)) {
            char symbol = line.length() > TextWrap.DIFF_CONT_MARKER.length()
                ? line.charAt(TextWrap.DIFF_CONT_MARKER.length())
                : '+';
            boolean isAdd = symbol == '+';
            String content = line.substring(TextWrap.DIFF_CONT_MARKER.length() + 1);
            renderDiffContinuationLine(graphics, content, row, contentWidth, isThinking, isAdd);
            return;
        }

        if (line.startsWith("@@@") && line.endsWith("@@@")) {
            graphics.setBackgroundColor(UiTheme.BG_ELEVATED);
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.DIFF_HEADER);
            renderString(graphics, line, row, 0, contentWidth, graphics.getForegroundColor(), graphics.getBackgroundColor());
            return;
        }

        // Línea de confirmación de guardado de archivo: "─── +5 ✓ /path guardado ───"
        // Variantes: "─── +5 -2 ✓ /path guardado ───" (edit) y "─── +5 ✓ guardado ───" (bulk)
        if (line.matches("^─+\\s*\\+\\d+(?:\\s*-\\d+)?\\s+✓\\s+.*guardado\\s*─+$")) {
            graphics.setBackgroundColor(UiTheme.DIFF_ADD_BG);
            graphics.setForegroundColor(UiTheme.DIFF_ADD);
            renderString(graphics, line, row, 0, contentWidth, UiTheme.DIFF_ADD, UiTheme.DIFF_ADD_BG);
            return;
        }

        if (line.matches("^─+\\s*\\+\\d+\\s+-\\d+\\s*─+$")) {
            renderDiffStats(graphics, line, row, contentWidth, isThinking);
            return;
        }

        if (renderTextLabelLine(graphics, line, row, contentWidth, isThinking, OLD_TEXT_LABEL_PATTERN, false)) {
            return;
        }

        if (renderTextLabelLine(graphics, line, row, contentWidth, isThinking, NEW_TEXT_LABEL_PATTERN, true)) {
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
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
            graphics.setBackgroundColor(UiTheme.BG_ELEVATED);
            renderString(graphics, line, row, 0, contentWidth, graphics.getForegroundColor(), graphics.getBackgroundColor());
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
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
            renderString(graphics, line, row, 0, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
            return;
        }

        if (line.trim().matches("^\\d+\\..*")) {
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
            renderString(graphics, line, row, 0, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
            return;
        }

        graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
        renderString(graphics, line, row, 0, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
    }

    private void renderMultiSegmentLine(TextGUIGraphics graphics, List<TextSegment> segments, int row, int contentWidth) {
        graphics.setBackgroundColor(UiTheme.BG);

        // Check if the full line (concatenated from segments) is a tool result,
        // to catch JSON that was split across segments by thinking tags.
        StringBuilder fullLine = new StringBuilder();
        for (TextSegment seg : segments) {
            fullLine.append(seg.getText());
        }
        if (ToolResultRenderer.isToolResultLine(fullLine.toString())) {
            String formatted;
            try {
                formatted = ToolResultRenderer.formatToolResult(fullLine.toString());
            } catch (InvalidToolResultPathException e) {
                formatted = ToolResultRenderer.INVALID_PATH_DISPLAY;
            } catch (Exception e) {
                formatted = fullLine.toString();
            }
            graphics.setForegroundColor(UiTheme.TOOL_DETAIL);
            renderString(graphics, formatted, row, 0, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
            return;
        }

        int currentX = 0;

        for (TextSegment segment : segments) {
            if (currentX >= contentWidth) {
                break;
            }
            currentX = renderSegment(graphics, segment.getText(), row, currentX, contentWidth, segment.isThinking());
        }

        if (currentX < contentWidth) {
            graphics.setBackgroundColor(UiTheme.BG);
            renderString(graphics, padOrTruncateByDisplayWidth("", contentWidth - currentX), row, currentX, contentWidth, UiTheme.FG, UiTheme.BG);
        }
    }

    private int renderSegment(TextGUIGraphics graphics, String text, int row, int startX, int contentWidth, boolean isThinking) {
        graphics.setBackgroundColor(UiTheme.BG);
        int x = startX;
        StringBuilder current = new StringBuilder();
        int currentWidth = 0;
        boolean inCode = false;

        for (int i = 0; i < text.length() && x < contentWidth; ) {
            int cp = text.codePointAt(i);
            int cpWidth = RenderUtils.getDisplayWidth(cp);

            if (cp == '`' && (i == 0 || text.codePointAt(i - 1) != '\\')) {
                if (inCode) {
                    graphics.setBackgroundColor(UiTheme.BG_ELEVATED);
                    graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
                    renderString(graphics, current.toString(), row, x, contentWidth, graphics.getForegroundColor(), graphics.getBackgroundColor());
                    x += currentWidth;
                    current.setLength(0);
                    currentWidth = 0;
                    inCode = false;
                    graphics.setBackgroundColor(UiTheme.BG);
                    graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
                } else {
                    if (current.length() > 0) {
                        graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
                        renderString(graphics, current.toString(), row, x, contentWidth, graphics.getForegroundColor(), graphics.getBackgroundColor());
                        x += currentWidth;
                        current.setLength(0);
                        currentWidth = 0;
                    }
                    inCode = true;
                }
            } else {
                current.appendCodePoint(cp);
                currentWidth += cpWidth;
            }
            i += (cp > 0xFFFF ? 2 : 1);
        }

        if (current.length() > 0 && x < contentWidth) {
            if (inCode) {
                graphics.setBackgroundColor(UiTheme.BG_ELEVATED);
                graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
            } else {
                graphics.setBackgroundColor(UiTheme.BG);
                graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
            }
            int available = contentWidth - x;
            String toPrint = current.length() > available ? current.substring(0, available) : current.toString();
            renderString(graphics, toPrint, row, x, contentWidth, graphics.getForegroundColor(), graphics.getBackgroundColor());
            x += currentWidth;
        }

        return x;
    }

    private void renderInlineCode(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean isThinking) {
        graphics.setBackgroundColor(UiTheme.BG);
        int x = 0;
        StringBuilder current = new StringBuilder();
        int currentWidth = 0;
        boolean inCode = false;

        for (int i = 0; i < line.length() && x < contentWidth; ) {
            int cp = line.codePointAt(i);
            int cpWidth = RenderUtils.getDisplayWidth(cp);

            if (cp == '`' && (i == 0 || line.codePointAt(i - 1) != '\\')) {
                if (inCode) {
                    graphics.setBackgroundColor(UiTheme.BG_ELEVATED);
                    graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
                    renderString(graphics, current.toString(), row, x, contentWidth, graphics.getForegroundColor(), graphics.getBackgroundColor());
                    x += currentWidth;
                    current.setLength(0);
                    currentWidth = 0;
                    inCode = false;
                    graphics.setBackgroundColor(UiTheme.BG);
                    graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
                } else {
                    inCode = true;
                }
            } else {
                current.appendCodePoint(cp);
                currentWidth += cpWidth;
            }
            i += (cp > 0xFFFF ? 2 : 1);
        }

        if (current.length() > 0) {
            if (inCode) {
                graphics.setBackgroundColor(UiTheme.BG_ELEVATED);
                graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
            } else {
                graphics.setBackgroundColor(UiTheme.BG);
                graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
            }
            renderString(graphics, current.toString(), row, x, contentWidth, graphics.getForegroundColor(), graphics.getBackgroundColor());
            x += currentWidth;
        }
    }

    private void renderBoldText(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean isThinking) {
        graphics.setBackgroundColor(UiTheme.BG);
        int x = 0;
        StringBuilder current = new StringBuilder();
        int currentWidth = 0;
        boolean inBold = false;

        for (int i = 0; i < line.length() && x < contentWidth; ) {
            if (i + 1 < line.length() && line.charAt(i) == '*' && line.charAt(i + 1) == '*') {
                if (inBold) {
                    graphics.setForegroundColor(isThinking ? UiTheme.THINKING_BOLD : UiTheme.ACCENT);
                    renderString(graphics, current.toString(), row, x, contentWidth, graphics.getForegroundColor(), graphics.getBackgroundColor());
                    x += currentWidth;
                    current.setLength(0);
                    currentWidth = 0;
                    inBold = false;
                    graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
                    i += 2; // skip "**"
                } else {
                    inBold = true;
                    i += 2; // skip "**"
                }
                continue;
            }
            int cp = line.codePointAt(i);
            if (cp == '*' && !inBold && i + 1 < line.length() && line.charAt(i + 1) == '*' && line.codePointAt(i + 1) == '*') {
                // solo el primer '*' de "**"
                inBold = true;
                i++;
                continue;
            }
            current.appendCodePoint(cp);
            currentWidth += RenderUtils.getDisplayWidth(cp);
            i += (cp > 0xFFFF ? 2 : 1);
            x += RenderUtils.getDisplayWidth(cp);
        }

        if (current.length() > 0) {
            if (inBold) {
                graphics.setForegroundColor(isThinking ? UiTheme.THINKING_BOLD : UiTheme.ACCENT);
            } else {
                graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
            }
            renderString(graphics, current.toString(), row, x, contentWidth, graphics.getForegroundColor(), graphics.getBackgroundColor());
            x += currentWidth;
        }
    }

    private void renderItalicText(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean isThinking) {
        graphics.setBackgroundColor(UiTheme.BG);
        int x = 0;
        StringBuilder current = new StringBuilder();
        int currentWidth = 0;
        boolean inItalic = false;

        for (int i = 0; i < line.length() && x < contentWidth; ) {
            int cp = line.codePointAt(i);
            int cpWidth = RenderUtils.getDisplayWidth(cp);

            if (cp == '*' && (i == 0 || line.codePointAt(i - 1) != '*')) {
                if (inItalic) {
                    graphics.setForegroundColor(isThinking ? UiTheme.THINKING_BOLD : UiTheme.SELECTED_BG);
                    renderString(graphics, current.toString(), row, x, contentWidth, graphics.getForegroundColor(), graphics.getBackgroundColor());
                    x += currentWidth;
                    current.setLength(0);
                    currentWidth = 0;
                    inItalic = false;
                    graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
                } else {
                    inItalic = true;
                }
            } else {
                current.appendCodePoint(cp);
                currentWidth += cpWidth;
            }
            i += (cp > 0xFFFF ? 2 : 1);
        }

        if (current.length() > 0) {
            if (inItalic) {
                graphics.setForegroundColor(isThinking ? UiTheme.THINKING_BOLD : UiTheme.SELECTED_BG);
            } else {
                graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
            }
            renderString(graphics, current.toString(), row, x, contentWidth, graphics.getForegroundColor(), graphics.getBackgroundColor());
            x += currentWidth;
        }
    }

    private boolean renderTextLabelLine(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean isThinking,
                                        java.util.regex.Pattern pattern, boolean isAdd) {
        if (!pattern.matcher(line).find() || isThinking || !line.contains(":")) {
            return false;
        }

        TextColor bgColor = isAdd ? UiTheme.DIFF_ADD_BG : UiTheme.DIFF_REMOVE_BG;
        TextColor fgColor = isAdd ? UiTheme.DIFF_ADD : UiTheme.DIFF_REMOVE;

        graphics.setBackgroundColor(bgColor);
        int colonIdx = line.indexOf(':');
        String label = line.substring(0, colonIdx + 1);
        graphics.setForegroundColor(fgColor);
        renderString(graphics, label, row, 0, contentWidth, fgColor, bgColor);
        int x = displayWidth(label);

        String code = line.substring(colonIdx + 1);
        int availableWidth = contentWidth - x;
        if (code.length() > availableWidth) {
            code = RenderUtils.truncateWithEllipsis(code, availableWidth);
        }

        List<SyntaxHighlighter.SyntaxToken> tokens = SyntaxHighlighter.tokenizeLine(code);
        for (SyntaxHighlighter.SyntaxToken token : tokens) {
            if (x >= contentWidth) break;
            graphics.setBackgroundColor(bgColor);
            graphics.setForegroundColor(token.color());
            String tokenText = token.text();
            int spaceLeft = contentWidth - x;
            if (tokenText.length() > spaceLeft) {
                tokenText = tokenText.substring(0, spaceLeft);
            }
            renderString(graphics, tokenText, row, x, contentWidth, token.color(), bgColor);
            x += displayWidth(tokenText);
        }

        graphics.setBackgroundColor(bgColor);
        renderString(graphics, padOrTruncateByDisplayWidth("", contentWidth - x), row, x, contentWidth, fgColor, bgColor);
        return true;
    }

    private static int displayWidth(String s) {
        if (s == null) {
            return 0;
        }
        int width = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            width += RenderUtils.getDisplayWidth(cp);
            i += (cp > 0xFFFF ? 2 : 1);
        }
        return width;
    }

    private void renderDiffLineWithNumber(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean isThinking, boolean isAdd, boolean isError) {
        int x = 0;
        int separatorIdx = line.indexOf("│");
        if (separatorIdx < 0) separatorIdx = line.indexOf("|");
        if (separatorIdx < 0) {
            graphics.setForegroundColor(isError ? UiTheme.DIFF_ERROR : (isAdd ? UiTheme.DIFF_ADD : UiTheme.DIFF_REMOVE));
            renderString(graphics, line, row, 0, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
            return;
        }

        String lineNumPart = line.substring(0, separatorIdx).trim();
        String rest = line.substring(separatorIdx);

        graphics.setBackgroundColor(UiTheme.BG);
        graphics.setForegroundColor(isThinking ? UiTheme.THINKING_MUTED : UiTheme.LINE_NUMBER);
        String numFormatted = String.format("%4s", lineNumPart);
        renderString(graphics, numFormatted, row, x, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
        x += displayWidth(numFormatted);

        graphics.setForegroundColor(isThinking ? UiTheme.THINKING_MUTED : UiTheme.DIFF_BORDER);
        renderString(graphics, " │", row, x, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
        x += displayWidth(" │");

        TextColor bgColor;
        TextColor fgColor;
        String symbol;

        if (isError) {
            bgColor = isThinking ? UiTheme.BG : UiTheme.DIFF_ERROR_BG;
            fgColor = isThinking ? UiTheme.THINKING : UiTheme.DIFF_ERROR;
            symbol = "✗";
        } else if (isAdd) {
            bgColor = isThinking ? UiTheme.BG : UiTheme.DIFF_ADD_BG;
            fgColor = isThinking ? UiTheme.THINKING : UiTheme.DIFF_ADD;
            symbol = "+";
        } else {
            bgColor = isThinking ? UiTheme.BG : UiTheme.DIFF_REMOVE_BG;
            fgColor = isThinking ? UiTheme.THINKING : UiTheme.DIFF_REMOVE;
            symbol = "-";
        }

        graphics.setBackgroundColor(bgColor);
        graphics.setForegroundColor(fgColor);
        renderString(graphics, symbol, row, x, contentWidth, fgColor, bgColor);
        x += displayWidth(symbol);

        renderString(graphics, " ", row, x, contentWidth, fgColor, bgColor);
        x += displayWidth(" ");

        String codeContent = rest.length() > 2 ? rest.substring(2) : "";
        String remaining = codeContent.startsWith(" ") ? codeContent.substring(1) : codeContent;
        int availableWidth = contentWidth - x;
        if (remaining.length() > availableWidth) {
            remaining = RenderUtils.truncateWithEllipsis(remaining, availableWidth);
        }

        List<SyntaxHighlighter.SyntaxToken> tokens = SyntaxHighlighter.tokenizeLine(remaining);
        graphics.setBackgroundColor(bgColor);

        for (SyntaxHighlighter.SyntaxToken token : tokens) {
            if (x >= contentWidth) break;
            TextColor tokenColor = isThinking ? UiTheme.THINKING : token.color();
            graphics.setForegroundColor(tokenColor);
            String tokenText = token.text();
            int spaceLeft = contentWidth - x;
            if (tokenText.length() > spaceLeft) {
                tokenText = tokenText.substring(0, spaceLeft);
            }
            renderString(graphics, tokenText, row, x, contentWidth, tokenColor, bgColor);
            x += displayWidth(tokenText);
        }

        if (x < contentWidth) {
            graphics.setBackgroundColor(bgColor);
            renderString(graphics, padOrTruncateByDisplayWidth("", contentWidth - x), row, x, contentWidth, fgColor, bgColor);
        }
    }

    private void renderDiffStats(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean isThinking) {
        graphics.setBackgroundColor(UiTheme.BG);
        int x = 0;

        int plusIdx = line.indexOf('+');
        int minusIdx = line.indexOf('-');

        if (plusIdx < 0 || minusIdx < 0) {
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING_MUTED : UiTheme.DIFF_BORDER);
            renderString(graphics, line, row, 0, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
            return;
        }

        String prefix = line.substring(0, plusIdx);
        graphics.setForegroundColor(isThinking ? UiTheme.THINKING_MUTED : UiTheme.DIFF_BORDER);
        renderString(graphics, prefix, row, x, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
        x += displayWidth(prefix);

        int spaceAfterPlus = line.indexOf(' ', plusIdx);
        if (spaceAfterPlus < 0) spaceAfterPlus = minusIdx;
        String addPart = line.substring(plusIdx, spaceAfterPlus);
        graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.DIFF_ADD);
        renderString(graphics, addPart, row, x, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
        x += displayWidth(addPart);

        String middle = line.substring(spaceAfterPlus, minusIdx);
        graphics.setForegroundColor(isThinking ? UiTheme.THINKING_MUTED : UiTheme.DIFF_BORDER);
        renderString(graphics, middle, row, x, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
        x += displayWidth(middle);

        int endOfMinus = minusIdx + 1;
        while (endOfMinus < line.length() && (Character.isDigit(line.charAt(endOfMinus)) || line.charAt(endOfMinus) == ' ')) {
            if (line.charAt(endOfMinus) == ' ' && endOfMinus > minusIdx + 1) break;
            endOfMinus++;
        }
        String removePart = line.substring(minusIdx, endOfMinus);
        graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.DIFF_REMOVE);
        renderString(graphics, removePart, row, x, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
        x += displayWidth(removePart);

        String suffix = line.substring(endOfMinus);
        graphics.setForegroundColor(isThinking ? UiTheme.THINKING_MUTED : UiTheme.DIFF_BORDER);
        renderString(graphics, suffix, row, x, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
        x += displayWidth(suffix);

        if (x < contentWidth) {
            renderString(graphics, padOrTruncateByDisplayWidth("", contentWidth - x), row, x, contentWidth, graphics.getForegroundColor(), UiTheme.BG);
        }
    }

    private void renderSimpleDiffLine(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean isThinking, boolean isAdd) {
        TextColor bgColor = isThinking ? UiTheme.BG : (isAdd ? UiTheme.DIFF_ADD_BG : UiTheme.DIFF_REMOVE_BG);
        TextColor symbolColor = isThinking ? UiTheme.THINKING : (isAdd ? UiTheme.DIFF_ADD : UiTheme.DIFF_REMOVE);

        graphics.setBackgroundColor(bgColor);
        graphics.setForegroundColor(symbolColor);

        String prefix = line.substring(0, 2);
        renderString(graphics, prefix, row, 0, contentWidth, symbolColor, bgColor);
        int x = displayWidth(prefix);

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
            renderString(graphics, tokenText, row, x, contentWidth, token.color(), bgColor);
            x += displayWidth(tokenText);
        }

        if (x < contentWidth) {
            renderString(graphics, padOrTruncateByDisplayWidth("", contentWidth - x), row, x, contentWidth, symbolColor, bgColor);
        }
    }

    private void renderCodeLine(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean isThinking) {
        graphics.setBackgroundColor(UiTheme.BG_ELEVATED);

        int availableWidth = contentWidth;
        String code = line;
        if (code.length() > availableWidth) {
            code = RenderUtils.truncateWithEllipsis(code, availableWidth);
        }

        List<SyntaxHighlighter.SyntaxToken> tokens = SyntaxHighlighter.tokenizeLine(code);
        int x = 0;
        for (SyntaxHighlighter.SyntaxToken token : tokens) {
            if (x >= contentWidth) break;
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING : token.color());
            String tokenText = token.text();
            int spaceLeft = contentWidth - x;
            if (tokenText.length() > spaceLeft) {
                tokenText = tokenText.substring(0, spaceLeft);
            }
            renderString(graphics, tokenText, row, x, contentWidth, token.color(), UiTheme.BG_ELEVATED);
            x += displayWidth(tokenText);
        }

        if (x < contentWidth) {
            renderString(graphics, padOrTruncateByDisplayWidth("", contentWidth - x), row, x, contentWidth, graphics.getForegroundColor(), UiTheme.BG_ELEVATED);
        }
    }

    private void renderDiffContinuationLine(TextGUIGraphics graphics, String content, int row, int contentWidth, boolean isThinking, boolean isAdd) {
        TextColor bgColor = isThinking ? UiTheme.BG : (isAdd ? UiTheme.DIFF_ADD_BG : UiTheme.DIFF_REMOVE_BG);
        graphics.setBackgroundColor(bgColor);

        String code = content;
        if (code.length() > contentWidth) {
            code = RenderUtils.truncateWithEllipsis(code, contentWidth);
        }

        List<SyntaxHighlighter.SyntaxToken> tokens = SyntaxHighlighter.tokenizeLine(code);
        int x = 0;
        for (SyntaxHighlighter.SyntaxToken token : tokens) {
            if (x >= contentWidth) break;
            TextColor fgColor = isThinking ? UiTheme.THINKING : token.color();
            graphics.setForegroundColor(fgColor);
            String tokenText = token.text();
            int spaceLeft = contentWidth - x;
            if (tokenText.length() > spaceLeft) {
                tokenText = tokenText.substring(0, spaceLeft);
            }
            renderString(graphics, tokenText, row, x, contentWidth, fgColor, bgColor);
            x += displayWidth(tokenText);
        }

        if (x < contentWidth) {
            graphics.setBackgroundColor(bgColor);
            renderString(graphics, padOrTruncateByDisplayWidth("", contentWidth - x), row, x, contentWidth, graphics.getForegroundColor(), bgColor);
        }
    }
}
