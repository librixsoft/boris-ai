package com.boris.cli.ui;

import com.googlecode.lanterna.gui2.BasicWindow;
import com.googlecode.lanterna.input.KeyStroke;
import com.googlecode.lanterna.input.MouseAction;
import com.googlecode.lanterna.input.MouseActionType;

import java.util.function.IntConsumer;

public class BorisWindow extends BasicWindow {

    private final int scrollStep;
    private final IntConsumer onScroll;

    public BorisWindow(int scrollStep, IntConsumer onScroll) {
        this.scrollStep = scrollStep;
        this.onScroll = onScroll;
    }

    @Override
    public boolean handleInput(KeyStroke key) {
        if (key instanceof MouseAction) {
            MouseAction mouse = (MouseAction) key;
            MouseActionType type = mouse.getActionType();
            if (type == MouseActionType.SCROLL_UP) {
                onScroll.accept(-scrollStep);
                return true;
            }
            if (type == MouseActionType.SCROLL_DOWN) {
                onScroll.accept(scrollStep);
                return true;
            }
        }
        return super.handleInput(key);
    }
}
