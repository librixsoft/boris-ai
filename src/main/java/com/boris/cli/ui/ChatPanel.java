package com.boris.cli.ui;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.ComponentRenderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChatPanel extends AbstractComponent<ChatPanel> {

    public static class NormalizedRange {
        private final int startLine;
        private final int startCol;
        private final int endLine;
        private final int endCol;

        public NormalizedRange(int startLine, int startCol, int endLine, int endCol) {
            this.startLine = startLine;
            this.startCol = startCol;
            this.endLine = endLine;
            this.endCol = endCol;
        }

        public int getStartLine() {
            return startLine;
        }

        public int getStartCol() {
            return startCol;
        }

        public int getEndLine() {
            return endLine;
        }

        public int getEndCol() {
            return endCol;
        }

        public boolean isEmpty() {
            return startLine == endLine && startCol == endCol;
        }
    }

    private final ChatContentRenderer renderer = new ChatContentRenderer();
    private List<String> lines = new ArrayList<>();
    private int scrollOffset = 0;

    private boolean selecting = false;
    private int selectStartLine = -1;
    private int selectStartCol = -1;
    private int selectEndLine = -1;
    private int selectEndCol = -1;

    private boolean thinkingEnabled = true;

    public ChatPanel() {
        super();
        setPreferredSize(new TerminalSize(80, 20));
    }

    public boolean isThinkingEnabled() {
        return thinkingEnabled;
    }

    public void setThinkingEnabled(boolean thinkingEnabled) {
        this.thinkingEnabled = thinkingEnabled;
        invalidate();
    }

    public boolean isAtBottom() {
        int maxOffset = Math.max(0, lines.size() - visibleRows());
        return scrollOffset >= maxOffset;
    }

    public void setText(String text) {
        this.lines = Arrays.asList(text.split("\n", -1));
        int maxOffset = Math.max(0, lines.size() - visibleRows());
        this.scrollOffset = Math.min(this.scrollOffset, maxOffset);
        if (lines.isEmpty()) {
            cancelSelection();
        }
        invalidate();
    }

    public void scroll(int deltaLines) {
        int maxOffset = Math.max(0, lines.size() - visibleRows());
        scrollOffset = Math.max(0, Math.min(maxOffset, scrollOffset + deltaLines));
        invalidate();
    }

    public void scrollToBottom() {
        int visible = visibleRows();
        scrollOffset = Math.max(0, lines.size() - visible);
        invalidate();
    }

    public int visibleRows() {
        return getEffectiveRows();
    }

    public int getLineCount() {
        return lines.size();
    }

    public String getLine(int index) {
        return lines.get(index);
    }

    public int getScrollOffset() {
        return scrollOffset;
    }

    public boolean isSelecting() {
        return selecting;
    }

    public boolean hasSelection() {
        if (selectStartLine < 0 || selectEndLine < 0) {
            return false;
        }
        NormalizedRange range = normalizedRange();
        return range != null && !range.isEmpty();
    }

    public void onMouseDown(TerminalPosition globalPos) {
        if (globalPos == null) {
            return;
        }
        if (hasSelection()) {
            cancelSelection();
        }

        TerminalPosition localPos = toLocal(globalPos);
        int localCol = localPos.getColumn();
        int localRow = localPos.getRow();

        int maxCols = getEffectiveColumns();
        int maxRows = getEffectiveRows();

        if (localCol < 0 || localCol >= maxCols || localRow < 0 || localRow >= maxRows) {
            return;
        }

        if (lines.isEmpty()) {
            return;
        }

        int docLine = localRowToDocumentLine(localRow);
        docLine = Math.max(0, Math.min(lines.size() - 1, docLine));
        int col = Math.max(0, Math.min(maxCols - 1, localCol));

        this.selecting = true;
        this.selectStartLine = docLine;
        this.selectStartCol = col;
        this.selectEndLine = docLine;
        this.selectEndCol = col;
        invalidate();
    }

    public void onMouseDrag(TerminalPosition globalPos) {
        if (!selecting || globalPos == null) {
            return;
        }

        TerminalPosition localPos = toLocal(globalPos);
        int localCol = localPos.getColumn();
        int localRow = localPos.getRow();

        int maxCols = getEffectiveColumns();
        int maxRows = getEffectiveRows();

        localCol = Math.max(0, Math.min(maxCols - 1, localCol));
        localRow = Math.max(0, Math.min(maxRows - 1, localRow));

        int docLine = localRowToDocumentLine(localRow);
        if (!lines.isEmpty()) {
            docLine = Math.max(0, Math.min(lines.size() - 1, docLine));
        }

        this.selectEndLine = docLine;
        this.selectEndCol = localCol;
        invalidate();
    }

    public void onMouseUp() {
        if (!selecting) {
            return;
        }
        this.selecting = false;
        if (hasSelection()) {
            String text = getSelectedText();
            if (text != null && !text.isEmpty()) {
                ClipboardUtil.copy(text);
            }
        }
        invalidate();
    }

    public void cancelSelection() {
        this.selecting = false;
        this.selectStartLine = -1;
        this.selectStartCol = -1;
        this.selectEndLine = -1;
        this.selectEndCol = -1;
        invalidate();
    }

    public TerminalPosition toLocal(TerminalPosition globalPos) {
        if (globalPos == null) {
            return null;
        }
        try {
            if (getParent() == null) {
                TerminalPosition pos = getPosition();
                if (pos == null) {
                    return globalPos;
                }
                return new TerminalPosition(
                        globalPos.getColumn() - pos.getColumn(),
                        globalPos.getRow() - pos.getRow()
                );
            }
            TerminalPosition origin = toGlobal(TerminalPosition.TOP_LEFT_CORNER);
            if (origin == null) {
                return globalPos;
            }
            return new TerminalPosition(
                    globalPos.getColumn() - origin.getColumn(),
                    globalPos.getRow() - origin.getRow()
            );
        } catch (Exception e) {
            return globalPos;
        }
    }

    public int terminalColToLocalCol(int terminalCol) {
        TerminalPosition local = toLocal(new TerminalPosition(terminalCol, 0));
        return local != null ? local.getColumn() : terminalCol;
    }

    public int terminalRowToLocalRow(int terminalRow) {
        TerminalPosition local = toLocal(new TerminalPosition(0, terminalRow));
        return local != null ? local.getRow() : terminalRow;
    }

    public int localRowToDocumentLine(int localRow) {
        return scrollOffset + localRow;
    }

    private int getEffectiveColumns() {
        TerminalSize size = getSize();
        if (size != null && size.getColumns() > 0) {
            return size.getColumns();
        }
        TerminalSize pref = getPreferredSize();
        if (pref != null && pref.getColumns() > 0) {
            return pref.getColumns();
        }
        return 80;
    }

    private int getEffectiveRows() {
        TerminalSize size = getSize();
        if (size != null && size.getRows() > 0) {
            return size.getRows();
        }
        TerminalSize pref = getPreferredSize();
        if (pref != null && pref.getRows() > 0) {
            return pref.getRows();
        }
        return 20;
    }

    public NormalizedRange normalizedRange() {
        if (selectStartLine < 0 || selectEndLine < 0) {
            return null;
        }
        if (selectStartLine < selectEndLine) {
            return new NormalizedRange(selectStartLine, selectStartCol, selectEndLine, selectEndCol);
        } else if (selectStartLine > selectEndLine) {
            return new NormalizedRange(selectEndLine, selectEndCol, selectStartLine, selectStartCol);
        } else {
            int startCol = Math.min(selectStartCol, selectEndCol);
            int endCol = Math.max(selectStartCol, selectEndCol);
            return new NormalizedRange(selectStartLine, startCol, selectEndLine, endCol);
        }
    }

    public String getSelectedText() {
        NormalizedRange nr = normalizedRange();
        if (nr == null || nr.isEmpty() || lines.isEmpty()) {
            return "";
        }

        int startLine = Math.max(0, Math.min(lines.size() - 1, nr.getStartLine()));
        int endLine = Math.max(0, Math.min(lines.size() - 1, nr.getEndLine()));

        StringBuilder sb = new StringBuilder();
        for (int i = startLine; i <= endLine; i++) {
            String line = lines.get(i);
            if (startLine == endLine) {
                int fromChar = renderer.columnToCharIndex(line, nr.getStartCol());
                int toChar = renderer.columnToCharIndex(line, nr.getEndCol());
                fromChar = Math.min(fromChar, line.length());
                toChar = Math.min(toChar, line.length());
                if (fromChar < toChar) {
                    sb.append(line, fromChar, toChar);
                }
            } else if (i == startLine) {
                int fromChar = renderer.columnToCharIndex(line, nr.getStartCol());
                fromChar = Math.min(fromChar, line.length());
                sb.append(line.substring(fromChar));
                sb.append("\n");
            } else if (i == endLine) {
                int toChar = renderer.columnToCharIndex(line, nr.getEndCol());
                toChar = Math.min(toChar, line.length());
                sb.append(line, 0, toChar);
            } else {
                sb.append(line);
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    public ChatContentRenderer getChatContentRenderer() {
        return renderer;
    }

    @Override
    protected ComponentRenderer<ChatPanel> createDefaultRenderer() {
        return renderer;
    }
}

