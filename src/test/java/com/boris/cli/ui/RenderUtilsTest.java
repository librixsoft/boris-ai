package com.boris.cli.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests para {@link RenderUtils#getDisplayWidth(int)}.
 * <p>
 * Issue: {@link RenderUtils#getDisplayWidth(int)} retornaba 1 para puntos de código suplementarios
 * (emojis) porque delegaba a {@link com.googlecode.lanterna.TerminalTextUtils#isCharDoubleWidth(char)},
 * que solo detecta caracteres CJK como doble ancho. Los emojis no son CJK, por lo que se consideraba
 * ancho 1. Esto causaba un desajuste con {@link com.googlecode.lanterna.screen.TerminalScreen#refreshFull()},
 * que avanza 2 columnas para caracteres de doble ancho (detectados por
 * {@link TextCharacter#isDoubleWidth()}, que sí identifica emojis mediante {@link TextCharacter#isEmoji(String)}).
 * <p>
 * Corrección: {@link RenderUtils#getDisplayWidth(int)} ahora retorna 2 para todos los puntos de código
 * suplementarios (> 0xFFFF), que son casi siempre doble ancho (emojis, símbolos matemáticos, etc.).
 * Retorna 0 para caracteres de ancho cero suplementarios (tags U+E0000-U+E001F). Para puntos de código
 * BMP (<= 0xFFFF) se mantiene la lógica original basada en {@link com.googlecode.lanterna.TerminalTextUtils#isCharDoubleWidth(char)}.
 */

class RenderUtilsTest {

    @Test
    void getDisplayWidthEmojiReturnsTwo() {
        // 🤖 = U+1F926 (punto de código suplementario, doble ancho)
        assertEquals(2, RenderUtils.getDisplayWidth(0x1F926));
    }

    @Test
    void getDisplayWidthSmileyReturnsTwo() {
        // 😀 = U+1F600 (punto de código suplementario, doble ancho)
        assertEquals(2, RenderUtils.getDisplayWidth(0x1F600));
    }

    @Test
    void getDisplayWidthAsciiReturnsOne() {
        assertEquals(1, RenderUtils.getDisplayWidth('A'));
        assertEquals(1, RenderUtils.getDisplayWidth(' '));
    }

    @Test
    void getDisplayWidthCjkReturnsTwo() {
        // あ = U+3042 (CJK, doble ancho)
        assertEquals(2, RenderUtils.getDisplayWidth('あ'));
    }

    @Test
    void getDisplayWidthZeroWidthTagReturnsZero() {
        // Tag character U+E0000 (punto de código suplementario, ancho cero)
        assertEquals(0, RenderUtils.getDisplayWidth(0xE0000));
    }
}
