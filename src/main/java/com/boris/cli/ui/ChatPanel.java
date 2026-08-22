package com.boris.cli.ui;

import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.ComponentRenderer;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class ChatPanel extends AbstractComponent<ChatPanel> {

    private List<String> lines = new ArrayList<>();
    private int scrollOffset = 0;

    public ChatPanel() {
        super();
        setPreferredSize(new TerminalSize(80, 20));
    }

    public boolean isAtBottom() {
        int maxOffset = Math.max(0, lines.size() - visibleRows());
        return scrollOffset >= maxOffset;
    }

    public void setText(String text) {
        this.lines = Arrays.asList(text.split("\n", -1));
        int maxOffset = Math.max(0, lines.size() - visibleRows());
        this.scrollOffset = Math.min(this.scrollOffset, maxOffset);
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
        TerminalSize size = getSize();
        return size == null ? 10 : Math.max(1, size.getRows());
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

    @Override
    protected ComponentRenderer<ChatPanel> createDefaultRenderer() {
        return new ChatContentRenderer();
    }
}
