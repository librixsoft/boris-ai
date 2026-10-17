package com.boris.edit.ui;

import com.boris.edit.DiffLine;
import com.boris.edit.DiffLineType;
import com.boris.edit.EditSession;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

import java.util.List;

public class DiffRenderer {

    private static final int LINE_NUM_WIDTH = 4;
    private static final String SEPARATOR = " │ ";

    public void renderHeader(TextGUIGraphics graphics, String filePath, int row, int width) {
        graphics.setForegroundColor(DiffColors.HEADER);
        String header = "@@@ " + filePath + " @@@";
        if (header.length() > width) {
            header = header.substring(0, width - 3) + "...";
        }
        graphics.putString(0, row, padRight(header, width));
    }

    public void renderLine(TextGUIGraphics graphics, DiffLine line, int row, int contentWidth) {
        int col = 0;

        // Número de línea
        graphics.setForegroundColor(DiffColors.LINE_NUMBER);
        String lineNum = String.format("%" + LINE_NUM_WIDTH + "d", line.lineNumber());
        graphics.putString(col, row, lineNum);
        col += LINE_NUM_WIDTH;

        // Separador
        graphics.setForegroundColor(DiffColors.BORDER);
        graphics.putString(col, row, SEPARATOR);
        col += SEPARATOR.length();

        // Prefijo (+/-/~/ )
        TextColor lineColor = DiffColors.forLineType(line.type());
        TextColor bgColor = DiffColors.backgroundForLineType(line.type());

        graphics.setForegroundColor(lineColor);
        if (bgColor != null) {
            graphics.setBackgroundColor(bgColor);
        }
        graphics.putString(col, row, line.prefix());
        col += line.prefix().length();

        // Contenido de la línea
        String content = line.content();
        int availableWidth = contentWidth - col - 6; // Espacio para status icon
        if (content.length() > availableWidth) {
            content = content.substring(0, availableWidth - 3) + "...";
        }
        graphics.putString(col, row, content);
        col += content.length();

        // Padding hasta el status icon
        int paddingNeeded = contentWidth - col - 4;
        if (paddingNeeded > 0) {
            graphics.putString(col, row, " ".repeat(paddingNeeded));
            col += paddingNeeded;
        }

        // Reset background
        graphics.setBackgroundColor(new TextColor.RGB(0, 0, 0));

        // Status icon
        graphics.setForegroundColor(DiffColors.forValidationStatus(line.status()));
        graphics.putString(col, row, " " + line.statusIcon());
    }

    public void renderStats(TextGUIGraphics graphics, int added, int removed, int row, int width) {
        graphics.setForegroundColor(DiffColors.STATS);
        String stats = String.format("─── +%d -%d ───", added, removed);
        graphics.putString(0, row, padRight(stats, width));
    }

    public void renderProgress(TextGUIGraphics graphics, EditSession session, int row, int width) {
        graphics.setForegroundColor(DiffColors.STATS);

        String progress = String.format(
            "Línea %d  │  ✓ %d validadas  │  ❌ %d errores",
            session.getCurrentLineNumber(),
            session.getValidatedCount(),
            session.getErrorCount()
        );

        graphics.putString(0, row, padRight(progress, width));
    }

    public void renderSession(TextGUIGraphics graphics, EditSession session, int startRow, int maxRows, int width) {
        int row = startRow;

        // Header
        renderHeader(graphics, session.getFilePath().toString(), row++, width);

        // Separator
        graphics.setForegroundColor(DiffColors.BORDER);
        graphics.putString(0, row++, "─".repeat(Math.min(width, 60)));

        // Lines
        List<DiffLine> lines = session.getLines();
        int startLine = Math.max(0, lines.size() - (maxRows - 4));

        for (int i = startLine; i < lines.size() && row < startRow + maxRows - 2; i++) {
            renderLine(graphics, lines.get(i), row++, width);
        }

        // Separator
        graphics.setForegroundColor(DiffColors.BORDER);
        graphics.putString(0, row++, "─".repeat(Math.min(width, 60)));

        // Stats/Progress
        if (session.isActive()) {
            renderProgress(graphics, session, row, width);
        } else {
            renderStats(graphics, session.getAddedCount(), session.getRemovedCount(), row, width);
        }
    }

    public String renderToString(EditSession session) {
        StringBuilder sb = new StringBuilder();

        sb.append("@@@ ").append(session.getFilePath().getFileName()).append(" @@@\n");
        sb.append("─".repeat(50)).append("\n");

        for (DiffLine line : session.getLines()) {
            sb.append(String.format("%4d │%s%s  %s\n",
                line.lineNumber(),
                line.prefix(),
                line.content(),
                line.statusIcon()
            ));
        }

        sb.append("─".repeat(50)).append("\n");
        sb.append(String.format("─── +%d -%d ───\n",
            session.getAddedCount(),
            session.getRemovedCount()
        ));

        return sb.toString();
    }

    private String padRight(String s, int width) {
        if (s.length() >= width) return s.substring(0, width);
        return s + " ".repeat(width - s.length());
    }
}
