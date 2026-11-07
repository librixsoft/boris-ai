package com.boris.cli.ui;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.googlecode.lanterna.TerminalSize;

class TranscriptTest {

    private ChatPanel createPanel() {
        ChatPanel panel = mock(ChatPanel.class);
        when(panel.getSize()).thenReturn(new TerminalSize(200, 40));
        when(panel.isAtBottom()).thenReturn(true);
        return panel;
    }

    private String lastRenderedText(ChatPanel panel) {
        ArgumentCaptor<String> captor = ArgumentCaptor.forClass(String.class);
        verify(panel, atLeastOnce()).setText(captor.capture());
        return captor.getValue();
    }

    @Test
    void appendBlankLineCreatesGapAfterWelcomeMessage() {
        ChatPanel panel = createPanel();
        Transcript transcript = new Transcript(panel, Runnable::run);

        transcript.appendLine("boris listo 🤖. Escribe tu mensaje.");
        transcript.appendBlankLine();
        transcript.appendLine(">> hey");

        String rendered = lastRenderedText(panel);
        assertTrue(rendered.contains("boris listo 🤖. Escribe tu mensaje.\n\n>> hey"),
                "Debe existir una linea en blanco entre el mensaje de bienvenida y el mensaje del usuario");
    }

    @Test
    void appendResponseStatsAddsBlankLineBeforeStats() {
        ChatPanel panel = createPanel();
        Transcript transcript = new Transcript(panel, Runnable::run);

        transcript.appendLine("Hola, soy Boris.");
        transcript.appendResponseStats(1000, 12, 34);

        String rendered = lastRenderedText(panel);
        assertTrue(rendered.contains("Hola, soy Boris.\n\n[stats]"),
                "Debe existir una linea en blanco antes de la linea de estadisticas");
    }

    @Test
    void appendResponseStatsKeepsContent() {
        ChatPanel panel = createPanel();
        Transcript transcript = new Transcript(panel, Runnable::run);

        transcript.appendLine("Hola, soy Boris.");
        transcript.appendResponseStats(1000, 12, 34);

        String rendered = lastRenderedText(panel);
        assertTrue(rendered.contains("in: 12 tokens"));
        assertTrue(rendered.contains("out: 34 tokens"));
    }
}
