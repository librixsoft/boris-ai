package com.boris.cli.ui;

import com.googlecode.lanterna.gui2.Label;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class HintBar extends Label {

    private static final String DEFAULT_HINTS = " ESC: abortar   Tab: foco   ↑↓: historial/scroll   PgUp/PgDn: scroll   Escribe / para comandos";

    private final List<CommandItem> allCommands;
    private List<CommandItem> filteredCommands;
    private int selectedIndex = 0;
    private boolean menuVisible = false;

    public HintBar() {
        this(CommandItem.defaultCommands());
    }

    public HintBar(List<CommandItem> commands) {
        super(DEFAULT_HINTS);
        this.allCommands = commands != null ? new ArrayList<>(commands) : CommandItem.defaultCommands();
        this.filteredCommands = new ArrayList<>(this.allCommands);
        setForegroundColor(UiTheme.ACCENT);
    }

    public boolean isMenuVisible() {
        return menuVisible;
    }

    public void showMenu(String prefix) {
        this.menuVisible = true;
        updateFilter(prefix);
    }

    public void hideMenu() {
        this.menuVisible = false;
        this.selectedIndex = 0;
        setText(DEFAULT_HINTS);
    }

    public void updateFilter(String prefix) {
        if (!menuVisible) {
            return;
        }
        if (prefix == null || prefix.isEmpty() || "/".equals(prefix)) {
            filteredCommands = new ArrayList<>(allCommands);
        } else {
            filteredCommands = allCommands.stream()
                    .filter(cmd -> cmd.matches(prefix))
                    .collect(Collectors.toList());
            if (filteredCommands.isEmpty()) {
                filteredCommands = new ArrayList<>(allCommands);
            }
        }

        if (selectedIndex >= filteredCommands.size()) {
            selectedIndex = Math.max(0, filteredCommands.size() - 1);
        } else if (selectedIndex < 0) {
            selectedIndex = 0;
        }

        renderMenu();
    }

    public void selectNext() {
        if (!menuVisible || filteredCommands.isEmpty()) {
            return;
        }
        selectedIndex = (selectedIndex + 1) % filteredCommands.size();
        renderMenu();
    }

    public void selectPrevious() {
        if (!menuVisible || filteredCommands.isEmpty()) {
            return;
        }
        selectedIndex = (selectedIndex - 1 + filteredCommands.size()) % filteredCommands.size();
        renderMenu();
    }

    public CommandItem getSelectedCommand() {
        if (!menuVisible || filteredCommands.isEmpty() || selectedIndex < 0 || selectedIndex >= filteredCommands.size()) {
            return null;
        }
        return filteredCommands.get(selectedIndex);
    }

    public List<CommandItem> getFilteredCommands() {
        return new ArrayList<>(filteredCommands);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    private void renderMenu() {
        if (filteredCommands.isEmpty()) {
            setText(" [No hay comandos disponibles]");
            return;
        }

        StringBuilder sb = new StringBuilder();
        sb.append(" Comandos: ");
        for (int i = 0; i < filteredCommands.size(); i++) {
            CommandItem cmd = filteredCommands.get(i);
            boolean isSelected = (i == selectedIndex);
            if (isSelected) {
                sb.append("[► ").append(cmd.getCommand()).append(" ").append(cmd.getDescription()).append("] ");
            } else {
                sb.append("[ ").append(cmd.getCommand()).append("] ");
            }
        }
        sb.append(" (↑↓/Tab: navegar, Enter: elegir, Esc: cerrar)");
        setText(sb.toString());
    }
}
