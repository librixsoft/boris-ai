package com.boris.cli.ui;

import com.googlecode.lanterna.gui2.TextGUIGraphics;

import static com.boris.cli.ui.RenderUtils.padOrTruncate;

public class MarkdownLineRenderer {

    public void render(TextGUIGraphics graphics, String line, int row, int contentWidth) {
        graphics.setBackgroundColor(UiTheme.BG);

        if (line.startsWith("###")) {
            graphics.setForegroundColor(UiTheme.ACCENT);
            String content = line.substring(3).trim();
            graphics.putString(0, row, padOrTruncate("   " + content, contentWidth));
            return;
        }
        if (line.startsWith("##")) {
            graphics.setForegroundColor(UiTheme.ACCENT);
            String content = line.substring(2).trim();
            graphics.putString(0, row, padOrTruncate("  " + content, contentWidth));
            return;
        }
        if (line.startsWith("#")) {
            graphics.setForegroundColor(UiTheme.ACCENT);
            String content = line.substring(1).trim();
            graphics.putString(0, row, padOrTruncate(" " + content, contentWidth));
            return;
        }

        if (line.trim().startsWith(">")) {
            graphics.setForegroundColor(UiTheme.MUTED);
            graphics.putString(0, row, padOrTruncate(line, contentWidth));
            return;
        }

        if (line.trim().startsWith("```") || line.trim().startsWith("``")) {
            graphics.setForegroundColor(UiTheme.USERC);
            graphics.setBackgroundColor(UiTheme.BG_ELEVATED);
            graphics.putString(0, row, padOrTruncate(line, contentWidth));
            return;
        }

        if (line.contains("`")) {
            renderInlineCode(graphics, line, row, contentWidth);
            return;
        }

        if (line.contains("**")) {
            renderBoldText(graphics, line, row, contentWidth);
            return;
        }

        if (line.contains("*") && !line.contains("**")) {
            renderItalicText(graphics, line, row, contentWidth);
            return;
        }

        if (line.trim().startsWith("- ") || line.trim().startsWith("* ")) {
            graphics.setForegroundColor(UiTheme.USERC);
            graphics.putString(0, row, padOrTruncate(line, contentWidth));
            return;
        }

        if (line.trim().matches("^\\d+\\..*")) {
            graphics.setForegroundColor(UiTheme.USERC);
            graphics.putString(0, row, padOrTruncate(line, contentWidth));
            return;
        }

        graphics.setForegroundColor(UiTheme.FG);
        graphics.putString(0, row, padOrTruncate(line, contentWidth));
    }

    private void renderInlineCode(TextGUIGraphics graphics, String line, int row, int contentWidth) {
        graphics.setBackgroundColor(UiTheme.BG);
        int x = 0;
        StringBuilder current = new StringBuilder();
        boolean inCode = false;

        for (int i = 0; i < line.length() && x < contentWidth; i++) {
            char c = line.charAt(i);

            if (c == '`' && (i == 0 || line.charAt(i - 1) != '\\')) {
                if (inCode) {
                    graphics.setBackgroundColor(UiTheme.BG_ELEVATED);
                    graphics.setForegroundColor(UiTheme.USERC);
                    graphics.putString(x, row, current.toString());
                    x += current.length();
                    current.setLength(0);
                    inCode = false;
                    graphics.setBackgroundColor(UiTheme.BG);
                    graphics.setForegroundColor(UiTheme.FG);
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
                graphics.setForegroundColor(UiTheme.USERC);
            } else {
                graphics.setBackgroundColor(UiTheme.BG);
                graphics.setForegroundColor(UiTheme.FG);
            }
            graphics.putString(x, row, padOrTruncate(current.toString(), contentWidth - x));
        }
    }

    private void renderBoldText(TextGUIGraphics graphics, String line, int row, int contentWidth) {
        graphics.setBackgroundColor(UiTheme.BG);
        int x = 0;
        StringBuilder current = new StringBuilder();
        boolean inBold = false;

        for (int i = 0; i < line.length() && x < contentWidth; i++) {
            char c = line.charAt(i);

            if (c == '*' && i + 1 < line.length() && line.charAt(i + 1) == '*') {
                if (inBold) {
                    graphics.setForegroundColor(UiTheme.ACCENT);
                    graphics.putString(x, row, current.toString());
                    x += current.length();
                    current.setLength(0);
                    inBold = false;
                    i++;
                    graphics.setForegroundColor(UiTheme.FG);
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
                graphics.setForegroundColor(UiTheme.ACCENT);
            } else {
                graphics.setForegroundColor(UiTheme.FG);
            }
            graphics.putString(x, row, padOrTruncate(current.toString(), contentWidth - x));
        }
    }

    private void renderItalicText(TextGUIGraphics graphics, String line, int row, int contentWidth) {
        graphics.setBackgroundColor(UiTheme.BG);
        int x = 0;
        StringBuilder current = new StringBuilder();
        boolean inItalic = false;

        for (int i = 0; i < line.length() && x < contentWidth; i++) {
            char c = line.charAt(i);

            if (c == '*' && (i == 0 || line.charAt(i - 1) != '*')) {
                if (inItalic) {
                    graphics.setForegroundColor(UiTheme.SELECTED_BG);
                    graphics.putString(x, row, current.toString());
                    x += current.length();
                    current.setLength(0);
                    inItalic = false;
                    graphics.setForegroundColor(UiTheme.FG);
                } else {
                    inItalic = true;
                }
            } else {
                current.append(c);
            }
        }

        if (current.length() > 0) {
            if (inItalic) {
                graphics.setForegroundColor(UiTheme.SELECTED_BG);
            } else {
                graphics.setForegroundColor(UiTheme.FG);
            }
            graphics.putString(x, row, padOrTruncate(current.toString(), contentWidth - x));
        }
    }
}
