package com.boris.cli.ui;

import com.googlecode.lanterna.gui2.TextGUIGraphics;

import java.util.ArrayList;
import java.util.List;

import static com.boris.cli.ui.RenderUtils.padOrTruncate;

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
        String[] parts = line.split("\\|");
        int x = 0;

        if (isHeader) {
            graphics.setBackgroundColor(UiTheme.BG_ELEVATED);
            graphics.setForegroundColor(UiTheme.ACCENT);
        } else {
            graphics.setBackgroundColor(UiTheme.BG);
            graphics.setForegroundColor(UiTheme.FG);
        }

        graphics.putString(x, row, "│");
        x += 1;

        for (int i = 1; i < parts.length - 1; i++) {
            String cell = parts[i].trim();
            int cellWidth = columnWidths.size() > i - 1 ? columnWidths.get(i - 1) : 15;

            String paddedCell = padOrTruncate(cell, cellWidth - 1);
            graphics.putString(x, row, paddedCell);
            x += cellWidth;

            graphics.putString(x, row, "│");
            x += 1;
        }

        if (x < contentWidth) {
            graphics.putString(x, row, padOrTruncate("", contentWidth - x));
        }
    }
}
