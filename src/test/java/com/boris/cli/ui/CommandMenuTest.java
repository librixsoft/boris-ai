package com.boris.cli.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandMenuTest {

    private CommandMenu commandMenu;

    @BeforeEach
    void setUp() {
        commandMenu = new CommandMenu();
    }

    @Test
    void testDefaultCommandsListArrayContainsExpectedCommands() {
        List<CommandItem> commands = CommandItem.defaultCommands();
        assertNotNull(commands);
        assertTrue(commands.size() >= 5);

        List<String> cmdNames = commands.stream().map(CommandItem::getCommand).toList();
        assertTrue(cmdNames.contains("/exit"));
        assertTrue(cmdNames.contains("/clear"));
        assertTrue(cmdNames.contains("/thinking"));
        assertTrue(cmdNames.contains("/effort"));
        assertTrue(cmdNames.contains("/skills"));
    }

    @Test
    void testCommandItemMatching() {
        CommandItem exitCmd = new CommandItem("/exit", "Salir", List.of("/quit"));
        assertTrue(exitCmd.matches("/"));
        assertTrue(exitCmd.matches("/e"));
        assertTrue(exitCmd.matches("/exit"));
        assertTrue(exitCmd.matches("/q"));
        assertTrue(exitCmd.matches("/quit"));
        assertFalse(exitCmd.matches("/clear"));

        CommandItem thinkCmd = new CommandItem("/thinking", "Razonamiento", List.of("/think", "/reasoning"));
        assertTrue(thinkCmd.matches("/th"));
        assertTrue(thinkCmd.matches("/think"));
        assertTrue(thinkCmd.matches("/reason"));
        assertFalse(thinkCmd.matches("/exit"));
    }

    @Test
    void testCommandMenuInitialState() {
        assertFalse(commandMenu.isMenuVisible());
        assertNull(commandMenu.getSelectedCommand());
    }

    @Test
    void testCommandMenuShowAndHideMenu() {
        commandMenu.showMenu("/");
        assertTrue(commandMenu.isMenuVisible());
        assertNotNull(commandMenu.getSelectedCommand());
        assertEquals("/exit", commandMenu.getSelectedCommand().getCommand());

        commandMenu.hideMenu();
        assertFalse(commandMenu.isMenuVisible());
        assertNull(commandMenu.getSelectedCommand());
    }

    @Test
    void testCommandMenuNavigation() {
        commandMenu.showMenu("/");
        assertEquals("/exit", commandMenu.getSelectedCommand().getCommand());

        commandMenu.selectNext();
        assertEquals("/clear", commandMenu.getSelectedCommand().getCommand());

        commandMenu.selectNext();
        assertEquals("/thinking", commandMenu.getSelectedCommand().getCommand());

        commandMenu.selectNext();
        assertEquals("/effort", commandMenu.getSelectedCommand().getCommand());

        commandMenu.selectNext();
        assertEquals("/skills", commandMenu.getSelectedCommand().getCommand());

        commandMenu.selectNext();
        assertEquals("/exit", commandMenu.getSelectedCommand().getCommand());

        commandMenu.selectPrevious();
        assertEquals("/skills", commandMenu.getSelectedCommand().getCommand());
    }

    @Test
    void testCommandMenuFilterUpdatesSelection() {
        commandMenu.showMenu("/th");
        assertTrue(commandMenu.isMenuVisible());
        assertEquals("/thinking", commandMenu.getSelectedCommand().getCommand());

        commandMenu.updateFilter("/c");
        assertEquals("/clear", commandMenu.getSelectedCommand().getCommand());
    }
}
