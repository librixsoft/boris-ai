package com.boris.cli.ui;

import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;

import java.util.ArrayList;
import java.util.List;

public class ModelSelector extends Panel {

    private final List<String> models;
    private final List<Label> itemLabels;
    private int selectedIndex = 0;
    private boolean visible = false;
    private final Runnable onModelSelected;

    public ModelSelector(List<String> models, Runnable onModelSelected) {
        super(new LinearLayout(Direction.VERTICAL));
        this.models = models != null ? new ArrayList<>(models) : new ArrayList<>();
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
        renderSelector();
    }

    public void hideSelector() {
        this.visible = false;
        this.selectedIndex = 0;
        setVisible(false);
        removeAllComponents();
        itemLabels.clear();
    }

    public void selectNext() {
        if (!visible || models.isEmpty()) {
            return;
        }
        selectedIndex = (selectedIndex + 1) % models.size();
        renderSelector();
    }

    public void selectPrevious() {
        if (!visible || models.isEmpty()) {
            return;
        }
        selectedIndex = (selectedIndex - 1 + models.size()) % models.size();
        renderSelector();
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

        for (int i = 0; i < models.size(); i++) {
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

        Label hint = new Label(" ↑↓: navegar  Enter: seleccionar  Esc: cerrar");
        hint.setForegroundColor(UiTheme.MUTED);
        addComponent(hint);
    }
}
