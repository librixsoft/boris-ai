package com.boris.edit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import com.boris.tooling.DiffLineNotifier;

import static org.junit.jupiter.api.Assertions.*;

class StreamingFileWriterTest {

    private StreamingFileWriter writer;
    private List<String> notifications;

    @BeforeEach
    private void setUp() {
        writer = new StreamingFileWriter();
        notifications = new ArrayList<>();
        DiffLineNotifier.clearListeners();
        DiffLineNotifier.addListener(notifications::add);
    }

    @Test
    void testFiltersSingleLineThinkingTag() {
        String input = "<thinking>pensamiento</thinking>\nTexto normal";

        String result = writer.processChunk(input);
        writer.finish();

        assertFalse(result.contains("<thinking>"), "No debería contener tag <thinking>");
        assertFalse(result.contains("</thinking>"), "No debería contener tag </thinking>");
        assertFalse(result.contains("pensamiento"), "No debería contener contenido de thinking");
        assertTrue(result.contains("Texto normal"), "Debería contener texto normal");
    }

    @Test
    void testFiltersMultiLineThinkingBlock() {
        String input = "<thinking>\n" +
                      "linea 1 de pensamiento\n" +
                      "linea 2 de pensamiento\n" +
                      "</thinking>\n" +
                      "Texto después";

        String result = writer.processChunk(input);
        writer.finish();

        assertFalse(result.contains("<thinking>"), "No debería contener tag de apertura");
        assertFalse(result.contains("</thinking>"), "No debería contener tag de cierre");
        assertFalse(result.contains("linea 1 de pensamiento"), "No debería contener contenido de thinking");
        assertFalse(result.contains("linea 2 de pensamiento"), "No debería contener contenido de thinking");
        assertTrue(result.contains("Texto después"), "Debería contener texto normal");
    }

    @Test
    void testFiltersNestedThinkingTags() {
        String input = "<thinking>outer <thought>inner</thought> outer</thinking>\nTexto";

        String result = writer.processChunk(input);
        writer.finish();

        assertFalse(result.contains("<thinking>"), "No debería contener <thinking>");
        assertFalse(result.contains("</thinking>"), "No debería contener </thinking>");
        assertFalse(result.contains("<thought>"), "No debería contener <thought>");
        assertFalse(result.contains("</thought>"), "No debería contener </thought>");
        assertFalse(result.contains("outer"), "No debería contener contenido");
        assertFalse(result.contains("inner"), "No debería contener contenido");
        assertTrue(result.contains("Texto"), "Debería contener texto normal");
    }

    @Test
    void testFiltersReasoningTag() {
        String input = "<reasoning>lógica</reasoning>\nTexto normal";

        String result = writer.processChunk(input);
        writer.finish();

        assertFalse(result.contains("<reasoning>"), "No debería contener tag <reasoning>");
        assertFalse(result.contains("</reasoning>"), "No debería contener tag </reasoning>");
        assertFalse(result.contains("lógica"), "No debería contener contenido de thinking");
        assertTrue(result.contains("Texto normal"), "Debería contener texto normal");
    }

    @Test
    void testFiltersThoughtTag() {
        String input = "<thought>idea</thought>\nTexto normal";

        String result = writer.processChunk(input);
        writer.finish();

        assertFalse(result.contains("<thought>"), "No debería contener tag <thought>");
        assertFalse(result.contains("</thought>"), "No debería contener tag </thought>");
        assertFalse(result.contains("idea"), "No debería contener contenido de thinking");
        assertTrue(result.contains("Texto normal"), "Debería contener texto normal");
    }

    @Test
    void testFiltersThinkingWithBackticks() {
        String input = "`<thinking>pensamiento</thinking>`\nTexto normal";

        String result = writer.processChunk(input);
        writer.finish();

        assertFalse(result.contains("<thinking>"), "No debería contener tag <thinking>");
        assertFalse(result.contains("</thinking>"), "No debería contener tag </thinking>");
        assertFalse(result.contains("pensamiento"), "No debería contener contenido de thinking");
        assertTrue(result.contains("Texto normal"), "Debería contener texto normal");
    }

    @Test
    void testHandlesChunkedThinkingInput() {
        // Simular chunks como llegarían del streaming
        StringBuilder output = new StringBuilder();
        output.append(writer.processChunk("<thinking>\n"));
        output.append(writer.processChunk("pensamiento\n"));
        output.append(writer.processChunk("</thinking>\n"));
        output.append(writer.processChunk("Texto después\n"));
        writer.finish();

        String result = output.toString();
        assertFalse(result.contains("<thinking>"));
        assertFalse(result.contains("</thinking>"));
        assertFalse(result.contains("pensamiento"));
        assertTrue(result.contains("Texto después"));
    }

    @Test
    void testPartialThinkTagRetained() {
        String result = writer.processChunk("<thin");
        assertTrue(result.isEmpty(), "El fragmento parcial de thinking no debería emitirse");
        String result2 = writer.processChunk("king>contenido</thinking>\nTexto");
        writer.finish();

        assertTrue(result2.contains("Texto"), "Debería contener texto después del thinking");
    }
}
