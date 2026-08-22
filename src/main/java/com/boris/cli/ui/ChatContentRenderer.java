package com.boris.cli.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

import java.util.ArrayList;
import java.util.List;

import static com.boris.cli.ui.RenderUtils.SCROLLBAR_WIDTH;
import static com.boris.cli.ui.RenderUtils.padOrTruncate;

public class ChatContentRenderer implements ComponentRenderer<ChatPanel> {

    private final MarkdownLineRenderer markdownLineRenderer = new MarkdownLineRenderer();
    private final TableRowRenderer tableRowRenderer = new TableRowRenderer();

    @Override
    public TerminalSize getPreferredSize(ChatPanel component) {
        return component.getPreferredSize();
    }

    @Override
    public void drawComponent(TextGUIGraphics graphics, ChatPanel component) {
        TerminalSize size = graphics.getSize();
        if (size == null) {
            return;
        }

        int visibleRows = size.getRows();
        int contentHeight = Math.max(1, component.getLineCount());
        int maxOffset = Math.max(0, contentHeight - visibleRows);
        int contentWidth = size.getColumns() - SCROLLBAR_WIDTH;

        graphics.setBackgroundColor(UiTheme.BG);

        boolean inTable = false;
        boolean isHeader = false;
        List<Integer> columnWidths = new ArrayList<>();

        for (int i = 0; i < visibleRows && component.getScrollOffset() + i < component.getLineCount(); i++) {
            String line = component.getLine(component.getScrollOffset() + i);

            if (tableRowRenderer.isTableRow(line)) {
                if (tableRowRenderer.isSeparatorRow(line)) {
                    inTable = true;
                    isHeader = false;
                    graphics.setBackgroundColor(UiTheme.BG);
                    graphics.setForegroundColor(UiTheme.MUTED);
                    graphics.putString(0, i, padOrTruncate(line, contentWidth));
                    continue;
                }

                if (!inTable) {
                    columnWidths = tableRowRenderer.parseColumnWidths(line);
                    inTable = true;
                    isHeader = true;
                } else {
                    isHeader = false;
                }

                tableRowRenderer.renderRow(graphics, line, i, contentWidth, isHeader, columnWidths);
            } else {
                inTable = false;
                isHeader = false;
                columnWidths.clear();
                markdownLineRenderer.render(graphics, line, i, contentWidth);
            }
        }

        for (int i = component.getLineCount() - component.getScrollOffset(); i < visibleRows; i++) {
            graphics.putString(0, i, padOrTruncate("", contentWidth));
        }

        drawScrollbar(graphics, size.getColumns(), visibleRows, maxOffset, component.getScrollOffset());
    }

    private void drawScrollbar(TextGUIGraphics graphics, int totalWidth, int visibleRows, int maxOffset, int currentOffset) {
        if (maxOffset <= 0) {
            return;
        }

        int barX = Math.max(0, totalWidth - SCROLLBAR_WIDTH);
        int barHeight = Math.max(1, visibleRows - 2);
        int thumbHeight = Math.max(5, (visibleRows * visibleRows) / Math.max(1, visibleRows + maxOffset));
        int thumbTop = (currentOffset * (barHeight - thumbHeight)) / Math.max(1, maxOffset);

        for (int row = 0; row < visibleRows; row++) {
            graphics.putString(barX, row, "│");
        }

        for (int row = thumbTop; row < thumbTop + thumbHeight && row < visibleRows; row++) {
            graphics.putString(barX, row, "█");
        }
    }
}
