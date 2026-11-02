package com.boris.cli.ui;

import java.awt.GraphicsEnvironment;
import java.io.IOException;

import com.googlecode.lanterna.terminal.DefaultTerminalFactory;
import com.googlecode.lanterna.terminal.Terminal;

/**
 * Terminal dispatch for Boris.
 * Windows terminals and AWT/Swing terminal emulators keep the stock
 * DefaultTerminalFactory behaviour. Real Unix TTY sessions get an
 * EmojiAwareUnixTerminal so astral codepoints (emoji) print correctly.
 */
public final class BorisTerminalFactory {

    private BorisTerminalFactory() {
    }

    public static Terminal create() throws IOException {
        if (isOperatingSystemWindows() || (!GraphicsEnvironment.isHeadless() && System.console() == null)) {
            return new DefaultTerminalFactory().createTerminal();
        }
        return new EmojiAwareUnixTerminal();
    }

    private static boolean isOperatingSystemWindows() {
        return System.getProperty("os.name", "").toLowerCase().startsWith("windows");
    }
}
