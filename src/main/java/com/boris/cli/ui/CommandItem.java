package com.boris.cli.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CommandItem {

    private final String command;
    private final String description;
    private final List<String> aliases;

    public CommandItem(String command, String description) {
        this(command, description, Collections.emptyList());
    }

    public CommandItem(String command, String description, List<String> aliases) {
        this.command = command;
        this.description = description;
        this.aliases = aliases != null ? new ArrayList<>(aliases) : new ArrayList<>();
    }

    public String getCommand() {
        return command;
    }

    public String getDescription() {
        return description;
    }

    public List<String> getAliases() {
        return Collections.unmodifiableList(aliases);
    }

    public boolean matches(String prefix) {
        if (prefix == null || prefix.isEmpty() || "/".equals(prefix)) {
            return true;
        }
        String p = prefix.toLowerCase().trim();
        if (command.toLowerCase().startsWith(p)) {
            return true;
        }
        for (String alias : aliases) {
            if (alias.toLowerCase().startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    public static List<CommandItem> defaultCommands() {
        List<CommandItem> list = new ArrayList<>();
        list.add(new CommandItem("/exit", "Salir", List.of("/quit")));
        list.add(new CommandItem("/clear", "Limpiar historial", Collections.emptyList()));
        list.add(new CommandItem("/thinking", "Activar/desactivar razonamiento", List.of("/think", "/reasoning")));
        return list;
    }
}
