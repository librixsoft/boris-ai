package com.boris.cli.ui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static com.boris.cli.ui.RenderUtils.padOrTruncateByDisplayWidth;

public class TableRowRenderer {

    public boolean isTableRow(String line) {
        String trimmed = line.trim();
        return trimmed.startsWith("|") && trimmed.endsWith("|");
    }

    public boolean isSeparatorRow(String line) {
        String trimmed = line.trim().replaceAll("\\|", "").trim();
        return trimmed.matches("^[-+\\s]+$");
    }

    public List<Integer> parseColumnWidths(String line) {
        List<Integer> widths = new ArrayList<>();
        String[] parts = line.split("\\|");
        for (String part : parts) {
            if (!part.trim().isEmpty()) {
                widths.add(part.trim().length() + 2);
            }
        }
        return widths;
    }

    public void renderRow(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean isHeader, List<Integer> columnWidths) {
        renderRow(graphics, line, row, contentWidth, isHeader, columnWidths, false);
    }

    public void renderRow(TextGUIGraphics graphics, String line, int row, int contentWidth, boolean isHeader, List<Integer> columnWidths, boolean isThinking) {
        String[] parts = line.split("\\|");
        int x = 0;

        if (isHeader) {
            graphics.setBackgroundColor(UiTheme.BG_ELEVATED);
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING_BOLD : UiTheme.ACCENT);
        } else {
            graphics.setBackgroundColor(UiTheme.BG);
            graphics.setForegroundColor(isThinking ? UiTheme.THINKING : UiTheme.FG);
        }

        TextColor fg = graphics.getForegroundColor();
        TextColor bg = graphics.getBackgroundColor();
        EnumSet<SGR> modifiers = EnumSet.noneOf(SGR.class);

        CodepointAwareTextRenderer.renderWithStyle(graphics, "│", row, x, contentWidth, fg, bg, modifiers);
        x += 1;

        for (int i = 1; i < parts.length - 1; i++) {
            String cell = parts[i].trim();
            int cellWidth = columnWidths.size() > i - 1 ? columnWidths.get(i - 1) : 15;

            String paddedCell = RenderUtils.padOrTruncateByDisplayWidth(cell, cellWidth - 1);
            CodepointAwareTextRenderer.renderWithStyle(graphics, paddedCell, row, x, contentWidth, fg, bg, modifiers);
            x += cellWidth;

            CodepointAwareTextRenderer.renderWithStyle(graphics, "│", row, x, contentWidth, fg, bg, modifiers);
            x += 1;
        }

        if (x < contentWidth) {
            CodepointAwareTextRenderer.renderWithStyle(graphics, RenderUtils.padOrTruncateByDisplayWidth("", contentWidth - x), row, x, contentWidth, fg, bg, modifiers);
        }
    }
}
