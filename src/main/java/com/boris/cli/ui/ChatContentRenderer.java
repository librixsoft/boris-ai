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

    public List<String> getVisibleLines(ChatPanel component) {
        List<String> visible = new ArrayList<>();
        int visibleRows = component.visibleRows();
        for (int i = 0; i < visibleRows && component.getScrollOffset() + i < component.getLineCount(); i++) {
            visible.add(component.getLine(component.getScrollOffset() + i));
        }
        return visible;
    }

    public int columnToCharIndex(String line, int displayCol) {
        if (line == null || line.isEmpty() || displayCol <= 0) {
            return 0;
        }
        int currentDisplayCol = 0;
        for (int i = 0; i < line.length(); i++) {
            if (currentDisplayCol >= displayCol) {
                return i;
            }
            char c = line.charAt(i);
            int charWidth = com.googlecode.lanterna.TerminalTextUtils.isCharDoubleWidth(c) ? 2 : 1;
            currentDisplayCol += charWidth;
        }
        return line.length();
    }

    public int columnToCharIndex(ChatPanel component, int documentLine, int displayCol) {
        if (documentLine < 0 || documentLine >= component.getLineCount()) {
            return 0;
        }
        return columnToCharIndex(component.getLine(documentLine), displayCol);
    }

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

        boolean inThinking = false;
        for (int lineIdx = 0; lineIdx < component.getScrollOffset() && lineIdx < component.getLineCount(); lineIdx++) {
            inThinking = MarkdownLineRenderer.updateThinkingState(component.getLine(lineIdx), inThinking);
        }

        for (int i = 0; i < visibleRows && component.getScrollOffset() + i < component.getLineCount(); i++) {
            int lineIdx = component.getScrollOffset() + i;
            String line = component.getLine(lineIdx);

            if (tableRowRenderer.isTableRow(line)) {
                if (tableRowRenderer.isSeparatorRow(line)) {
                    inTable = true;
                    isHeader = false;
                    graphics.setBackgroundColor(UiTheme.BG);
                    graphics.setForegroundColor(inThinking ? UiTheme.THINKING_MUTED : UiTheme.MUTED);
                    graphics.putString(0, i, padOrTruncate(line, contentWidth));
                    inThinking = MarkdownLineRenderer.updateThinkingState(line, inThinking);
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
                markdownLineRenderer.render(graphics, line, i, contentWidth, inThinking);
            }

            inThinking = MarkdownLineRenderer.updateThinkingState(line, inThinking);
        }

        for (int i = component.getLineCount() - component.getScrollOffset(); i < visibleRows; i++) {
            graphics.putString(0, i, padOrTruncate("", contentWidth));
        }

        drawSelectionHighlight(graphics, component, visibleRows, contentWidth);

        drawScrollbar(graphics, size.getColumns(), visibleRows, maxOffset, component.getScrollOffset());
    }

    private void drawSelectionHighlight(TextGUIGraphics graphics, ChatPanel component, int visibleRows, int contentWidth) {
        if (!component.isSelecting() && !component.hasSelection()) {
            return;
        }

        ChatPanel.NormalizedRange range = component.normalizedRange();
        if (range == null || range.isEmpty()) {
            return;
        }

        int startLine = range.getStartLine();
        int endLine = range.getEndLine();

        for (int row = 0; row < visibleRows; row++) {
            int docLine = component.getScrollOffset() + row;
            if (docLine < startLine || docLine > endLine || docLine >= component.getLineCount()) {
                continue;
            }

            int fromCol;
            int toCol;

            if (startLine == endLine) {
                fromCol = range.getStartCol();
                toCol = range.getEndCol();
            } else if (docLine == startLine) {
                fromCol = range.getStartCol();
                toCol = contentWidth;
            } else if (docLine == endLine) {
                fromCol = 0;
                toCol = range.getEndCol();
            } else {
                fromCol = 0;
                toCol = contentWidth;
            }

            fromCol = Math.max(0, Math.min(contentWidth, fromCol));
            toCol = Math.max(0, Math.min(contentWidth, toCol));

            for (int col = fromCol; col < toCol; col++) {
                com.googlecode.lanterna.TextCharacter tc = graphics.getCharacter(col, row);
                if (tc != null) {
                    graphics.setCharacter(col, row, tc.withBackgroundColor(UiTheme.SELECT_BG).withForegroundColor(UiTheme.SELECT_FG));
                } else {
                    graphics.setCharacter(col, row, new com.googlecode.lanterna.TextCharacter(' ', UiTheme.SELECT_FG, UiTheme.SELECT_BG));
                }
            }
        }
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
