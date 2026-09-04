package com.boris.cli.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CommandMenuTest {

    private HintBar hintBar;

    @BeforeEach
    void setUp() {
        hintBar = new HintBar();
    }

    @Test
    void testDefaultCommandsListArrayContainsExpectedCommands() {
        List<CommandItem> commands = CommandItem.defaultCommands();
        assertNotNull(commands);
        assertTrue(commands.size() >= 3);

        List<String> cmdNames = commands.stream().map(CommandItem::getCommand).toList();
        assertTrue(cmdNames.contains("/exit"));
        assertTrue(cmdNames.contains("/clear"));
        assertTrue(cmdNames.contains("/thinking"));
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
    void testHintBarInitialState() {
        assertFalse(hintBar.isMenuVisible());
        assertNull(hintBar.getSelectedCommand());
    }

    @Test
    void testHintBarShowAndHideMenu() {
        hintBar.showMenu("/");
        assertTrue(hintBar.isMenuVisible());
        assertNotNull(hintBar.getSelectedCommand());
        assertEquals("/exit", hintBar.getSelectedCommand().getCommand());

        hintBar.hideMenu();
        assertFalse(hintBar.isMenuVisible());
        assertNull(hintBar.getSelectedCommand());
    }

    @Test
    void testHintBarNavigation() {
        hintBar.showMenu("/");
        assertEquals(0, hintBar.getSelectedIndex());
        assertEquals("/exit", hintBar.getSelectedCommand().getCommand());

        hintBar.selectNext();
        assertEquals(1, hintBar.getSelectedIndex());
        assertEquals("/clear", hintBar.getSelectedCommand().getCommand());

        hintBar.selectNext();
        assertEquals(2, hintBar.getSelectedIndex());
        assertEquals("/thinking", hintBar.getSelectedCommand().getCommand());

        // Wrap-around forward
        hintBar.selectNext();
        assertEquals(0, hintBar.getSelectedIndex());
        assertEquals("/exit", hintBar.getSelectedCommand().getCommand());

        // Wrap-around backward
        hintBar.selectPrevious();
        assertEquals(2, hintBar.getSelectedIndex());
        assertEquals("/thinking", hintBar.getSelectedCommand().getCommand());
    }

    @Test
    void testHintBarFilterUpdatesSelection() {
        hintBar.showMenu("/th");
        assertTrue(hintBar.isMenuVisible());
        assertEquals(1, hintBar.getFilteredCommands().size());
        assertEquals("/thinking", hintBar.getSelectedCommand().getCommand());

        hintBar.updateFilter("/c");
        assertEquals(1, hintBar.getFilteredCommands().size());
        assertEquals("/clear", hintBar.getSelectedCommand().getCommand());
    }
}
