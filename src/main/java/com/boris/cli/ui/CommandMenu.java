package com.boris.cli.ui;

import com.googlecode.lanterna.gui2.Direction;
import com.googlecode.lanterna.gui2.Label;
import com.googlecode.lanterna.gui2.LinearLayout;
import com.googlecode.lanterna.gui2.Panel;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class CommandMenu extends Panel {

    private static final int MAX_VISIBLE_ITEMS = 6;

    private final List<CommandItem> allCommands;
    private List<CommandItem> filteredCommands;
    private final List<Label> itemLabels;
    private int selectedIndex = 0;
    private int scrollOffset = 0;
    private boolean visible = false;

    public CommandMenu() {
        this(CommandItem.defaultCommands());
    }

    public CommandMenu(List<CommandItem> commands) {
        super(new LinearLayout(Direction.VERTICAL));
        this.allCommands = commands != null ? new ArrayList<>(commands) : CommandItem.defaultCommands();
        this.filteredCommands = new ArrayList<>(this.allCommands);
        this.itemLabels = new ArrayList<>();
        setVisible(false);
    }

    public boolean isMenuVisible() {
        return visible;
    }

    public void showMenu(String prefix) {
        this.visible = true;
        setVisible(true);
        updateFilter(prefix);
    }

    public void hideMenu() {
        this.visible = false;
        this.selectedIndex = 0;
        this.scrollOffset = 0;
        setVisible(false);
        removeAllComponents();
        itemLabels.clear();
    }

    public void updateFilter(String prefix) {
        if (!visible) {
            return;
        }

        if (prefix == null || prefix.isEmpty() || "/".equals(prefix)) {
            filteredCommands = new ArrayList<>(allCommands);
        } else {
            filteredCommands = allCommands.stream()
                    .filter(cmd -> cmd.matches(prefix))
                    .collect(Collectors.toList());
            if (filteredCommands.isEmpty()) {
                hideMenu();
                return;
            }
        }

        if (selectedIndex >= filteredCommands.size()) {
            selectedIndex = Math.max(0, filteredCommands.size() - 1);
        }
        scrollOffset = 0;
        adjustScrollOffset();

        renderMenu();
    }

    public void selectNext() {
        if (!visible || filteredCommands.isEmpty()) {
            return;
        }
        selectedIndex = (selectedIndex + 1) % filteredCommands.size();
        adjustScrollOffset();
        renderMenu();
    }

    public void selectPrevious() {
        if (!visible || filteredCommands.isEmpty()) {
            return;
        }
        selectedIndex = (selectedIndex - 1 + filteredCommands.size()) % filteredCommands.size();
        adjustScrollOffset();
        renderMenu();
    }

    private void adjustScrollOffset() {
        if (selectedIndex < scrollOffset) {
            scrollOffset = selectedIndex;
        } else if (selectedIndex >= scrollOffset + MAX_VISIBLE_ITEMS) {
            scrollOffset = selectedIndex - MAX_VISIBLE_ITEMS + 1;
        }
    }

    public CommandItem getSelectedCommand() {
        if (!visible || filteredCommands.isEmpty() || selectedIndex < 0 || selectedIndex >= filteredCommands.size()) {
            return null;
        }
        return filteredCommands.get(selectedIndex);
    }

    private void renderMenu() {
        removeAllComponents();
        itemLabels.clear();

        if (filteredCommands.isEmpty()) {
            Label empty = new Label("  Sin comandos");
            empty.setForegroundColor(UiTheme.MUTED);
            addComponent(empty);
            return;
        }

        boolean hasMoreAbove = scrollOffset > 0;
        boolean hasMoreBelow = scrollOffset + MAX_VISIBLE_ITEMS < filteredCommands.size();

        if (hasMoreAbove) {
            Label upArrow = new Label("   ▲ más comandos arriba");
            upArrow.setForegroundColor(UiTheme.MUTED);
            addComponent(upArrow);
        }

        int endIndex = Math.min(scrollOffset + MAX_VISIBLE_ITEMS, filteredCommands.size());
        for (int i = scrollOffset; i < endIndex; i++) {
            CommandItem cmd = filteredCommands.get(i);
            boolean isSelected = (i == selectedIndex);

            String cmdPart = String.format("%-12s", cmd.getCommand());
            String text;
            if (isSelected) {
                text = " ► " + cmdPart + " " + cmd.getDescription();
            } else {
                text = "   " + cmdPart + " " + cmd.getDescription();
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
            Label downArrow = new Label("   ▼ más comandos abajo");
            downArrow.setForegroundColor(UiTheme.MUTED);
            addComponent(downArrow);
        }

        Label hint = new Label(" ↑↓: nav · Enter: ok · Esc: cerrar");
        hint.setForegroundColor(UiTheme.MUTED);
        addComponent(hint);
    }
}
