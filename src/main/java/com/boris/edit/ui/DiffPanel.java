package com.boris.edit.ui;

import com.boris.edit.DiffLine;
import com.boris.edit.EditSession;
import com.googlecode.lanterna.TerminalSize;
import com.googlecode.lanterna.gui2.AbstractComponent;
import com.googlecode.lanterna.gui2.ComponentRenderer;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

import java.util.ArrayList;
import java.util.List;

public class DiffPanel extends AbstractComponent<DiffPanel> {

    private EditSession session;
    private final DiffRenderer renderer = new DiffRenderer();
    private int preferredHeight = 15;
    private int preferredWidth = 70;

    public DiffPanel() {
        super();
    }

    public DiffPanel(EditSession session) {
        this();
        this.session = session;
    }

    public void setSession(EditSession session) {
        this.session = session;
        invalidate();
    }

    public EditSession getSession() {
        return session;
    }

    public void refresh() {
        invalidate();
    }

    public void setPreferredHeight(int height) {
        this.preferredHeight = height;
    }

    public void setPreferredWidth(int width) {
        this.preferredWidth = width;
    }

    @Override
    protected ComponentRenderer<DiffPanel> createDefaultRenderer() {
        return new DiffPanelRenderer();
    }

    private class DiffPanelRenderer implements ComponentRenderer<DiffPanel> {

        @Override
        public TerminalSize getPreferredSize(DiffPanel component) {
            int height = preferredHeight;
            if (session != null) {
                height = Math.min(preferredHeight, session.getLines().size() + 4);
            }
            return new TerminalSize(preferredWidth, Math.max(5, height));
        }

        @Override
        public void drawComponent(TextGUIGraphics graphics, DiffPanel component) {
            TerminalSize size = graphics.getSize();
            if (size == null || session == null) {
                return;
            }

            graphics.setBackgroundColor(new com.googlecode.lanterna.TextColor.RGB(0, 0, 0));
            graphics.fill(' ');

            renderer.renderSession(graphics, session, 0, size.getRows(), size.getColumns());
        }
    }
}
