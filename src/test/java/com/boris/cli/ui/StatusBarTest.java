package com.boris.cli.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.googlecode.lanterna.TextColor;

class StatusBarTest {

    private static final String SPINNER_FRAME = "⣾";
    private static final String SPINNER_TEXT = "pensando...";
    private static final String IDLE_TEXT = "en pausa...";
    private static final String ANSI_ESCAPE_EXAMPLE = "\u001b[38;2;255;0;0m";

    @Test
    void showThinkingAplicaColorDeGradienteSoloAlSpinner() {
        StatusBar statusBar = new StatusBar(Runnable::run);
        TokenCounter tokens = new TokenCounter(128000);
        TextColor spinnerColor = UiTheme.SPINNER_GRADIENT[0];

        statusBar.showThinking(SPINNER_FRAME, spinnerColor, SPINNER_TEXT, 0, 5, tokens);

        assertFalse(statusBar.spinnerText().contains("\u001b"));
        assertFalse(statusBar.spinnerText().contains(";0m"));
        assertEquals(spinnerColor, statusBar.spinnerColor());
        assertEquals(UiTheme.ACCENT, statusBar.infoColor());
        assertFalse(statusBar.infoText().contains("\u001b"));
    }

    @Test
    void showThinkingMuestraFrameTextoTiempoYTokens() {
        StatusBar statusBar = new StatusBar(Runnable::run);
        TokenCounter tokens = new TokenCounter(128000);

        statusBar.showThinking(SPINNER_FRAME, UiTheme.SPINNER_GRADIENT[0], SPINNER_TEXT, 0, 5, tokens);

        assertEquals(" " + SPINNER_FRAME + " " + SPINNER_TEXT, statusBar.spinnerText());
        assertTrue(statusBar.infoText().contains("5s"));
        assertTrue(statusBar.infoText().contains(tokens.plainStatus()));
    }

    @Test
    void spinnerMantieneAnchoConstanteEntreEstadoThinkingYIdle() {
        StatusBar statusBar = new StatusBar(Runnable::run);
        TokenCounter tokens = new TokenCounter(128000);

        statusBar.showThinking(SPINNER_FRAME, UiTheme.SPINNER_GRADIENT[0], SPINNER_TEXT, 0, 5, tokens);
        int thinkingWidth = statusBar.spinnerText().length();

        statusBar.showTokenStatus(tokens);
        int idleWidth = statusBar.spinnerText().length();

        assertEquals(thinkingWidth, idleWidth);
    }

    @Test
    void showTokenStatusMuestraEstadoIdleSinVaciarElSpinner() {
        StatusBar statusBar = new StatusBar(Runnable::run);
        TokenCounter tokens = new TokenCounter(128000);

        statusBar.showTokenStatus(tokens);

        assertTrue(statusBar.spinnerText().contains(StatusBar.IDLE_MARK));
        assertTrue(statusBar.spinnerText().contains(IDLE_TEXT));
        assertEquals(" " + StatusBar.IDLE_MARK + " " + IDLE_TEXT, statusBar.spinnerText());
        assertEquals(UiTheme.IDLE, statusBar.spinnerColor());
        assertEquals(UiTheme.ACCENT, statusBar.infoColor());
        assertFalse(statusBar.infoText().contains("\u001b"));
    }

    @Test
    void showThinkingRechazaFrameConEscapeAnsi() {
        StatusBar statusBar = new StatusBar(Runnable::run);
        TokenCounter tokens = new TokenCounter(128000);

        assertThrows(AnsiEscapeInUiTextException.class,
                () -> statusBar.showThinking(ANSI_ESCAPE_EXAMPLE + SPINNER_FRAME, UiTheme.SPINNER_GRADIENT[0],
                        SPINNER_TEXT, 0, 5, tokens));
    }

    @Test
    void showThinkingRechazaTextoConEscapeAnsi() {
        StatusBar statusBar = new StatusBar(Runnable::run);
        TokenCounter tokens = new TokenCounter(128000);

        assertThrows(AnsiEscapeInUiTextException.class,
                () -> statusBar.showThinking(SPINNER_FRAME, UiTheme.SPINNER_GRADIENT[0],
                        ANSI_ESCAPE_EXAMPLE + SPINNER_TEXT, 0, 5, tokens));
    }

    @Test
    void showThinkingStateActivadoMuestraEmojiAlInicio() {
        StatusBar statusBar = new StatusBar(Runnable::run);

        statusBar.showThinkingState(true);

        assertTrue(statusBar.infoText().startsWith("💭"));
        assertTrue(statusBar.infoText().contains("Thinking: activado"));
        assertEquals(UiTheme.ACCENT, statusBar.infoColor());
    }

    @Test
    void showThinkingStateDesactivadoEliminaEmoji() {
        StatusBar statusBar = new StatusBar(Runnable::run);

        statusBar.showThinkingState(true);
        statusBar.showThinkingState(false);

        assertTrue(statusBar.infoText().isBlank());
    }
}
