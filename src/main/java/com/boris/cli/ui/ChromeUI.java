package com.boris.cli.ui;

/**
 * Chrome/UI elements: banner, prompt, status lines, and answer formatting.
 * Provides a single output channel for all chrome elements.
 */
public class ChromeUI {
    
    private final TerminalManager terminalManager;
    private final ColorPalette colorPalette;
    
    public ChromeUI(TerminalManager terminalManager, ColorPalette colorPalette) {
        this.terminalManager = terminalManager;
        this.colorPalette = colorPalette;
    }
    
    /**
     * Single output channel for all chrome (banner, prompt, status lines).
     * Everything used to go through System.out while streamed responses
     * went through terminal.writer(); Jansi's AnsiConsole and JLine's writer
     * don't always agree on ANSI state until the writer has been used at
     * least once, which is why colors used to look "wrong" until the first
     * message came back. Routing everything through terminal.writer() keeps
     * colors consistent from the very first frame.
     */
    public void out(String s) {
        terminalManager.out(s);
    }
    
    /**
     * Plain-text startup mark: name + slogan, no glyphs, no banner art.
     */
    public void printBanner() {
        out("\n");
        out(colorPalette.accent());
        out("Boris");
        out(colorPalette.reset());
        out(colorPalette.dim());
        out("  —  I am invincible\n");
        out("esc abort  ·  ctrl+c quit\n");
        out(colorPalette.reset());
        out("\n");
    }
    
    /**
     * Borderless input prompt, just "› " followed by whatever the user
     * types. No box, no fixed-width padding, nothing that depends on the
     * terminal's column count.
     *
     * This is a deliberate simplification, not just a style choice: the
     * previous boxed prompt computed a width once (queryTerminalColumns /
     * boxWidth) and baked it into three printed lines (top rule, input
     * line with side borders, bottom rule). If the terminal was resized
     * while that box was on screen, the emulator would reflow those
     * already-printed lines against the *new* column count while our
     * cursor-position escape codes still assumed the *old* one — every
     * resize made the misalignment worse, since each reflow started from
     * an already-corrupted layout. Dropping the box removes every
     * width-dependent character from the prompt entirely, so there is
     * nothing left for a resize to desynchronize. Input now wraps exactly
     * the way a normal shell prompt would.
     */
    public void printPrompt() {
        out(colorPalette.accent());
        out("› ");
        out(colorPalette.fg());
    }
    
    /**
     * Moves to a fresh line after Enter is pressed.
     */
    public void closeInputBox() {
        out("\n");
        out(colorPalette.reset());
    }
    
    /**
     * Starts the answer, labeled, in the neutral fg tone. No leading
     * newline here — stopSpinner() already moves the cursor to a fresh
     * line after freezing the elapsed-time indicator, so this just prints
     * the label directly on that new line.
     */
    public void openAnswer() {
        out(colorPalette.accent());
        out("\nBoris ");
        out(colorPalette.dim());
        out("· ");
        out(colorPalette.fg());
    }
    
    /**
     * Short status line reusing the prompt glyph in a different tone (e.g. aborted).
     */
    public void printStatus(String text) {
        out(colorPalette.warn());
        out(text + "\n");
        out(colorPalette.reset());
    }
    
    /**
     * Print a newline with reset.
     */
    public void printNewline() {
        out(colorPalette.reset());
        out("\n\n");
    }
}