package com.boris.cli.ui;

import com.googlecode.lanterna.TerminalPosition;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.KeyType;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.input.MouseActionType;

import java.util.function.IntConsumer;

public class BorisWindow extends BasicWindow {

    private final ChatPanel chatPanel;
    private final int scrollStep;
    private final IntConsumer onScroll;

    public BorisWindow(ChatPanel chatPanel, int scrollStep, IntConsumer onScroll) {
        this.chatPanel = chatPanel;
        this.scrollStep = scrollStep;
        this.onScroll = onScroll;
    }

    public BorisWindow(int scrollStep, IntConsumer onScroll, ChatPanel chatPanel) {
        this(chatPanel, scrollStep, onScroll);
    }

    @Override
    public boolean handleInput(KeyStroke key) {
        if (key != null && key.getKeyType() == KeyType.Escape) {
            if (chatPanel != null && chatPanel.hasSelection()) {
                chatPanel.cancelSelection();
            }
        }

        if (key instanceof MouseAction) {
            MouseAction mouse = (MouseAction) key;
            MouseActionType type = mouse.getActionType();
            int button = mouse.getButton();
            TerminalPosition pos = mouse.getPosition();

            if (type == MouseActionType.SCROLL_UP) {
                onScroll.accept(-scrollStep);
                return true;
            }
            if (type == MouseActionType.SCROLL_DOWN) {
                onScroll.accept(scrollStep);
                return true;
            }

            if (type == MouseActionType.CLICK_DOWN) {
                if (button == 1) {
                    if (isInsideChatPanel(pos)) {
                        chatPanel.onMouseDown(pos);
                        return true;
                    } else if (chatPanel != null && chatPanel.hasSelection()) {
                        chatPanel.cancelSelection();
                    }
                }
            } else if (type == MouseActionType.DRAG) {
                if (chatPanel != null && chatPanel.isSelecting()) {
                    chatPanel.onMouseDrag(pos);
                    return true;
                }
            } else if (type == MouseActionType.CLICK_RELEASE) {
                if (chatPanel != null && chatPanel.isSelecting()) {
                    chatPanel.onMouseUp();
                    return true;
                }
            }
        }
        return super.handleInput(key);
    }

    private boolean isInsideChatPanel(TerminalPosition globalPos) {
        if (chatPanel == null || globalPos == null) {
            return false;
        }
        TerminalPosition local = chatPanel.toLocal(globalPos);
        if (local == null) {
            return false;
        }
        TerminalSize size = chatPanel.getSize();
        int maxCols = (size != null && size.getColumns() > 0) ? size.getColumns()
                : (chatPanel.getPreferredSize() != null && chatPanel.getPreferredSize().getColumns() > 0
                ? chatPanel.getPreferredSize().getColumns() : 80);
        int maxRows = (size != null && size.getRows() > 0) ? size.getRows()
                : (chatPanel.getPreferredSize() != null && chatPanel.getPreferredSize().getRows() > 0
                ? chatPanel.getPreferredSize().getRows() : 20);

        return local.getColumn() >= 0 && local.getColumn() < maxCols
                && local.getRow() >= 0 && local.getRow() < maxRows;
    }
}
