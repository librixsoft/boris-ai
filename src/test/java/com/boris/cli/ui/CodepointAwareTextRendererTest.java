package com.boris.cli.ui;

import com.googlecode.lanterna.SGR;
import com.googlecode.lanterna.TextColor;
import com.googlecode.lanterna.TextCharacter;
import com.googlecode.lanterna.gui2.TextGUIGraphics;
import com.googlecode.lanterna.TerminalSize;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.EnumSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests para {@link CodepointAwareTextRenderer}.
 * <p>
 * Issue: Los emojis en el chat panel se pintaban como "??" porque Lanterna 3.1.5
 * {@link com.googlecode.lanterna.graphics.AbstractTextGraphics#putString(int, int, java.lang.String)}
 * itera por {@code string.charAt(i)}, que devuelve unidades UTF-16 ({@code char}). Para un carácter
 * suplementario (emoji como 🤖 = U+1F926 = \uD835\uDD26), {@code charAt(0)} devuelve el surrogate alto
 * \uD835 y {@code charAt(1)} el surrogate bajo \uDD26. Cada surrogate se almacena como un
 * {@link TextCharacter} independiente en el buffer de pantalla. Al renderizar,
 * {@link com.googlecode.lanterna.screen.TerminalScreen#refreshFull()} llama a
 * {@link com.googlecode.lanterna.terminal.Terminal#putString(java.lang.String)} con el string de un
 * solo surrogate, {@link com.googlecode.lanterna.terminal.TerminalTextEncoder#encode(java.lang.String, java.nio.charset.Charset)}
 * hace {@code text.codePointAt(0)} sobre un string de un solo surrogate, obteniendo 0xD835 (no un valor
 * escalar válido), lo que produce bytes UTF-8 inválidos que el terminal muestra como "?". Dos surrogates
 * = dos "??".
 * <p>
 * Corrección: {@link CodepointAwareTextRenderer} itera por puntos de código ({@code codePointAt}) en
 * lugar de unidades UTF-16 ({@code charAt}). Para cada punto de código crea un {@link TextCharacter}
 * mediante {@link TextCharacter#fromString(java.lang.String, com.googlecode.lanterna.TextColor, com.googlecode.lanterna.TextColor, java.util.EnumSet)}
 * (que acepta {@link String} y soporta caracteres suplementarios) y lo coloca con
 * {@link TextGUIGraphics#setCharacter(int, int, com.googlecode.lanterna.TextCharacter)}. Avanza la columna
 * según el ancho de visualización ({@link RenderUtils#getDisplayWidth(int)}: 2 para doble ancho / emoji,
 * 1 para el resto).
 */

class CodepointAwareTextRendererTest {

    @Test
    void renderSimpleAscii() {
        TextGUIGraphics graphics = mock(TextGUIGraphics.class);
        when(graphics.getSize()).thenReturn(new TerminalSize(80, 24));

        CodepointAwareTextRenderer.render(graphics, "Hello", 0, 0, 80);

        verify(graphics).setCharacter(0, 0, new TextCharacter('H', TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT));
        verify(graphics).setCharacter(1, 0, new TextCharacter('e', TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT));
        verify(graphics).setCharacter(2, 0, new TextCharacter('l', TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT));
        verify(graphics).setCharacter(3, 0, new TextCharacter('l', TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT));
        verify(graphics).setCharacter(4, 0, new TextCharacter('o', TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT));
    }

    @Test
    void renderSingleEmoji() {
        TextGUIGraphics graphics = mock(TextGUIGraphics.class);
        when(graphics.getSize()).thenReturn(new TerminalSize(80, 24));

        CodepointAwareTextRenderer.render(graphics, "🤖", 0, 0, 80);

        TextCharacter[] expected = TextCharacter.fromString("🤖", TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT, EnumSet.noneOf(SGR.class));
        verify(graphics).setCharacter(0, 0, expected[0]);
    }

    @Test
    void renderTwoAdjacentEmojis() {
        TextGUIGraphics graphics = mock(TextGUIGraphics.class);
        when(graphics.getSize()).thenReturn(new TerminalSize(80, 24));

        CodepointAwareTextRenderer.render(graphics, "🤖😀", 0, 0, 80);

        TextCharacter[] expected1 = TextCharacter.fromString("🤖", TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT, EnumSet.noneOf(SGR.class));
        TextCharacter[] expected2 = TextCharacter.fromString("😀", TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT, EnumSet.noneOf(SGR.class));
        verify(graphics).setCharacter(0, 0, expected1[0]);
        verify(graphics).setCharacter(2, 0, expected2[0]);
    }

    @Test
    void renderMixedAsciiAndEmoji() {
        TextGUIGraphics graphics = mock(TextGUIGraphics.class);
        when(graphics.getSize()).thenReturn(new TerminalSize(80, 24));

        CodepointAwareTextRenderer.render(graphics, "A🤖B", 0, 0, 80);

        TextCharacter[] expectedEmoji = TextCharacter.fromString("🤖", TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT, EnumSet.noneOf(SGR.class));
        verify(graphics).setCharacter(0, 0, new TextCharacter('A', TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT));
        verify(graphics).setCharacter(1, 0, expectedEmoji[0]);
        verify(graphics).setCharacter(3, 0, new TextCharacter('B', TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT));
    }

    @Test
    void renderWithStyle() {
        TextGUIGraphics graphics = mock(TextGUIGraphics.class);
        when(graphics.getSize()).thenReturn(new TerminalSize(80, 24));

        CodepointAwareTextRenderer.renderWithStyle(graphics, "🤖", 0, 0, 80,
                TextColor.ANSI.GREEN, TextColor.ANSI.BLACK, EnumSet.of(SGR.BOLD));

        TextCharacter[] expected = TextCharacter.fromString("🤖", TextColor.ANSI.GREEN, TextColor.ANSI.BLACK, EnumSet.of(SGR.BOLD));
        verify(graphics).setCharacter(0, 0, expected[0]);
    }

    @Test
    void renderRespectsMaxWidth() {
        TextGUIGraphics graphics = mock(TextGUIGraphics.class);
        when(graphics.getSize()).thenReturn(new TerminalSize(80, 24));

        CodepointAwareTextRenderer.render(graphics, "AB🤖C", 0, 0, 3);

        verify(graphics).setCharacter(0, 0, new TextCharacter('A', TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT));
        verify(graphics).setCharacter(1, 0, new TextCharacter('B', TextColor.ANSI.DEFAULT, TextColor.ANSI.DEFAULT));
    }

    @Test
    void renderEmptyString() {
        TextGUIGraphics graphics = mock(TextGUIGraphics.class);
        when(graphics.getSize()).thenReturn(new TerminalSize(80, 24));

        CodepointAwareTextRenderer.render(graphics, "", 0, 0, 80);

        verify(graphics, Mockito.never()).setCharacter(Mockito.anyInt(), Mockito.anyInt(), Mockito.any());
    }

    @Test
    void renderNullString() {
        TextGUIGraphics graphics = mock(TextGUIGraphics.class);
        when(graphics.getSize()).thenReturn(new TerminalSize(80, 24));

        CodepointAwareTextRenderer.render(graphics, null, 0, 0, 80);

        verify(graphics, Mockito.never()).setCharacter(Mockito.anyInt(), Mockito.anyInt(), Mockito.any());
    }
}
