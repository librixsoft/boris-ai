package com.boris.cli.ui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.gui2.TextGUIGraphics;

import java.util.EnumSet;

public final class CodepointAwareTextRenderer {

    private CodepointAwareTextRenderer() {
    }

    public static void render(TextGUIGraphics graphics, String text, int row, int startCol, int maxWidth) {
        renderWithStyle(graphics, text, row, startCol, maxWidth, TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT, EnumSet.noneOf(SGR.class));
    }

    public static void renderWithStyle(TextGUIGraphics graphics, String text, int row, int startCol, int maxWidth,
                                       TextColor fg, TextColor bg, EnumSet<SGR> modifiers) {
        if (text == null || text.isEmpty()) {
            return;
        }
        int col = startCol;
        int i = 0;
        while (i < text.length() && col < maxWidth) {
            int cp = text.codePointAt(i);
            int width = RenderUtils.getDisplayWidth(cp);
            if (width == 0) {
                i += (cp > 0xFFFF ? 2 : 1);
                continue;
            }
            if (col + width > maxWidth) {
                break;
            }
            String cpString = new String(Character.toChars(cp));
            TextCharacter[] chars = TextCharacter.fromString(cpString, fg, bg, modifiers);
            if (chars.length > 0) {
                graphics.setCharacter(col, row, chars[0]);
            }
            col += width;
            i += (cp > 0xFFFF ? 2 : 1);
        }
    }

    public static int charIndexAtDisplayCol(String line, int displayCol) {
        if (line == null || line.isEmpty() || displayCol <= 0) {
            return 0;
        }
        int currentDisplayCol = 0;
        int i = 0;
        while (i < line.length()) {
            if (currentDisplayCol >= displayCol) {
                return i;
            }
            int cp = line.codePointAt(i);
            int width = RenderUtils.getDisplayWidth(cp);
            if (width == 0) {
                i += (cp > 0xFFFF ? 2 : 1);
                continue;
            }
            currentDisplayCol += width;
            i += (cp > 0xFFFF ? 2 : 1);
        }
        return line.length();
    }
}
