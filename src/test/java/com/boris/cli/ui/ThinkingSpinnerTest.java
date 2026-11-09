package com.boris.cli.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;

import com.googlecode.lanterna.TextColor;

class ThinkingSpinnerTest {

    private static final String SPINNER_FRAME = "⣾";
    private static final String THINKING_TEXT = "pensando...";
    private static final long COLOR_STEP_MILLIS = ThinkingSpinner.colorStepMillis();
    private static final int MIN_CHANNEL = 144;
    private static final int MAX_CHANNEL = 240;

    @Test
    void startMuestraSpinnerFranjaConColorGradiente() throws InterruptedException {
        StatusBar statusBar = new StatusBar(Runnable::run);
        TokenCounter tokens = new TokenCounter(128000);
        AtomicBoolean waiting = new AtomicBoolean(true);
        AtomicBoolean wasAborted = new AtomicBoolean(false);
        ThinkingSpinner spinner = new ThinkingSpinner(statusBar, tokens, waiting, wasAborted);

        spinner.start();

        long deadline = System.currentTimeMillis() + 2000;
        while (statusBar.spinnerText().isBlank() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10);
        }
        waiting.set(false);

        String text = statusBar.spinnerText();
        assertFalse(text.isBlank());
        assertFalse(text.contains("\u001b"));
        boolean muestraThinking = text.contains(SPINNER_FRAME) && text.contains(THINKING_TEXT);
        boolean muestraIdle = text.contains("en pausa...");
        assertTrue(muestraThinking || muestraIdle);
    }

    @Test
    void gradienteUsaGamaDeColorMedia() {
        for (TextColor color : UiTheme.SPINNER_GRADIENT) {
            TextColor.RGB rgb = (TextColor.RGB) color;
            assertTrue(rgb.getRed() >= MIN_CHANNEL && rgb.getRed() <= MAX_CHANNEL);
            assertTrue(rgb.getGreen() >= MIN_CHANNEL && rgb.getGreen() <= MAX_CHANNEL);
            assertTrue(rgb.getBlue() >= MIN_CHANNEL && rgb.getBlue() <= MAX_CHANNEL);
        }
    }

    @Test
    void colorParaElPrimerIntervaloEsRojo() {
        long firstStepStart = 0L;
        long firstStepEnd = COLOR_STEP_MILLIS - 1;

        assertEquals(UiTheme.SPINNER_GRADIENT[0], colorAt(firstStepStart));
        assertEquals(UiTheme.SPINNER_GRADIENT[0], colorAt(firstStepEnd));
    }

    @Test
    void colorCambiaUnaVezPorIntervaloLento() {
        assertEquals(UiTheme.SPINNER_GRADIENT[1], colorAt(COLOR_STEP_MILLIS));
        assertEquals(UiTheme.SPINNER_GRADIENT[2], colorAt(COLOR_STEP_MILLIS * 2));
    }

    @Test
    void colorRotaCerrandoElCicloDelGradiente() {
        int lastColorIndex = UiTheme.SPINNER_GRADIENT.length - 1;

        assertEquals(UiTheme.SPINNER_GRADIENT[lastColorIndex],
                colorAt(COLOR_STEP_MILLIS * lastColorIndex));
        assertEquals(UiTheme.SPINNER_GRADIENT[0],
                colorAt(COLOR_STEP_MILLIS * UiTheme.SPINNER_GRADIENT.length));
    }

    private static TextColor colorAt(long elapsedMillis) {
        return ThinkingSpinner.colorForElapsed(elapsedMillis);
    }
}
