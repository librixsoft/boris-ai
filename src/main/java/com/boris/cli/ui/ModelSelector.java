package com.boris.cli.ui;

import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ModelSelector extends Panel {

    private static final int MAX_VISIBLE_ITEMS = 6;

    private final Supplier<List<String>> modelsSupplier;
    private List<String> models;
    private final List<Label> itemLabels;
    private int selectedIndex = 0;
    private int scrollOffset = 0;
    private boolean visible = false;
    private final Runnable onModelSelected;

    public ModelSelector(Supplier<List<String>> modelsSupplier, Runnable onModelSelected) {
        super(new LinearLayout(Direction.VERTICAL));
        this.modelsSupplier = modelsSupplier;
        this.models = modelsSupplier != null ? modelsSupplier.get() : new ArrayList<>();
        this.itemLabels = new ArrayList<>();
        this.onModelSelected = onModelSelected;
        setVisible(false);
    }

    public boolean isSelectorVisible() {
        return visible;
    }

    public void showSelector() {
        this.visible = true;
        setVisible(true);
        selectedIndex = 0;
        scrollOffset = 0;
        if (modelsSupplier != null) {
            this.models = modelsSupplier.get();
        }
        renderSelector();
    }

    public void hideSelector() {
        this.visible = false;
        this.selectedIndex = 0;
        this.scrollOffset = 0;
        setVisible(false);
        removeAllComponents();
        itemLabels.clear();
    }

    public void selectNext() {
        if (!visible || models.isEmpty()) {
            return;
        }
        selectedIndex = (selectedIndex + 1) % models.size();
        adjustScrollOffset();
        renderSelector();
    }

    public void selectPrevious() {
        if (!visible || models.isEmpty()) {
            return;
        }
        selectedIndex = (selectedIndex - 1 + models.size()) % models.size();
        adjustScrollOffset();
        renderSelector();
    }

    private void adjustScrollOffset() {
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + MAX_VISIBLE_ITEMS) {
            scrollOffset = selectedIndex - MAX_VISIBLE_ITEMS + 1;
        }
    }

    public String getSelectedModel() {
        if (!visible || models.isEmpty() || selectedIndex < 0 || selectedIndex >= models.size()) {
            return null;
        }
        return models.get(selectedIndex);
    }

    public void confirmSelection() {
        if (onModelSelected != null) {
            onModelSelected.run();
        }
    }

    private void renderSelector() {
        removeAllComponents();
        itemLabels.clear();

        if (models.isEmpty()) {
            Label empty = new Label("  No hay modelos disponibles");
            empty.setForegroundColor(UiTheme.MUTED);
            addComponent(empty);
            return;
        }

        boolean hasMoreAbove = scrollOffset > 0;
        boolean hasMoreBelow = scrollOffset + MAX_VISIBLE_ITEMS < models.size();

        if (hasMoreAbove) {
            Label upArrow = new Label("   ▲ más modelos arriba");
            upArrow.setForegroundColor(UiTheme.MUTED);
            addComponent(upArrow);
        }

        int endIndex = Math.min(scrollOffset + MAX_VISIBLE_ITEMS, models.size());
        for (int i = scrollOffset; i < endIndex; i++) {
            String model = models.get(i);
            boolean isSelected = (i == selectedIndex);

            String text;
            if (isSelected) {
                text = " ► " + model;
            } else {
                text = "   " + model;
            }

            Label label = new Label(text);
            if (isSelected) {
                label.setForegroundColor(UiTheme.ACCENT);
            } else {
                label.setForegroundColor(UiTheme.FG);
            }

            itemLabels.add(label);
            addComponent(label);
        }

        if (hasMoreBelow) {
            Label downArrow = new Label("   ▼ más modelos abajo");
            downArrow.setForegroundColor(UiTheme.MUTED);
            addComponent(downArrow);
        }

        Label hint = new Label(" ↑↓: nav · Enter: seleccionar · Esc: cerrar");
        hint.setForegroundColor(UiTheme.MUTED);
        addComponent(hint);
    }
}
