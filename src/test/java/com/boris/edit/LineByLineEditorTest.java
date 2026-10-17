package com.boris.edit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class LineByLineEditorTest {

    private LineByLineEditor editor;
    private List<String> outputLog;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        SymbolCache cache = new SymbolCache();

        String contextContent = """
            ### `BasicWindow`
            - `BasicWindow()`
            - `BasicWindow(String title)`
            - `setComponent(Component)`

            ### `Panel`
            - `Panel()`
            - `addComponent(Component)`

            ### `Label`
            - `Label(String text)`
            """;

        Path contextFile = tempDir.resolve(".boris").resolve("CONTEXT.md");
        Files.createDirectories(contextFile.getParent());
        Files.writeString(contextFile, contextContent);

        cache.loadFromContext(contextFile);
        editor = new LineByLineEditor(cache);

        outputLog = new ArrayList<>();
        editor.setOutputHandler(outputLog::add);
    }

    @Test
    void testStartCreateSession() {
        Path newFile = tempDir.resolve("NewClass.java");
        EditSession session = editor.startCreate(newFile);

        assertNotNull(session);
        assertTrue(session.isActive());
        assertEquals(EditSession.Mode.CREATE, session.getMode());
        assertTrue(editor.hasActiveSession());
    }

    @Test
    void testProcessValidLine() {
        Path newFile = tempDir.resolve("Test.java");
        editor.startCreate(newFile);

        var result = editor.processLine("package com.test;");
        assertTrue(result.isOk());

        result = editor.processLine("import java.util.List;");
        assertTrue(result.isOk());

        result = editor.processLine("Panel panel = new Panel();");
        assertTrue(result.isOk());
    }

    @Test
    void testProcessInvalidLine() {
        Path newFile = tempDir.resolve("Test.java");
        editor.startCreate(newFile);

        var result = editor.processLine("TextLabel label = new TextLabel(\"x\");");
        assertTrue(result.needsCorrection());
        assertNotNull(result.message());
        assertTrue(result.suggestions().contains("Label"));
    }

    @Test
    void testCorrectLine() {
        Path newFile = tempDir.resolve("Test.java");
        editor.startCreate(newFile);

        // Primera línea inválida
        var result = editor.processLine("new TextLabel(\"hello\");");
        assertTrue(result.needsCorrection());

        // Corregir
        var corrected = editor.correctLine("new Label(\"hello\");");
        assertTrue(corrected.isOk());
    }

    @Test
    void testFinishSession() throws IOException {
        Path newFile = tempDir.resolve("MyClass.java");
        editor.startCreate(newFile);

        editor.processLine("package com.test;");
        // El archivo ya debe existir después de la primera línea
        assertTrue(Files.exists(newFile));

        editor.processLine("");
        editor.processLine("public class MyClass {");
        editor.processLine("}");

        var result = editor.finish();

        assertTrue(result.success());

        List<String> content = Files.readAllLines(newFile);
        assertEquals(4, content.size());
        assertEquals("package com.test;", content.get(0));
    }

    @Test
    void testLineWrittenImmediately() throws IOException {
        Path newFile = tempDir.resolve("Immediate.java");
        editor.startCreate(newFile);

        // Antes de cualquier línea, el archivo no existe
        assertFalse(Files.exists(newFile));

        // Después de la primera línea, el archivo existe
        editor.processLine("line 1");
        assertTrue(Files.exists(newFile));
        assertEquals(1, Files.readAllLines(newFile).size());

        // Después de la segunda línea, el archivo tiene 2 líneas
        editor.processLine("line 2");
        assertEquals(2, Files.readAllLines(newFile).size());

        // Cada línea se escribe inmediatamente
        editor.processLine("line 3");
        List<String> content = Files.readAllLines(newFile);
        assertEquals(3, content.size());
        assertEquals("line 1", content.get(0));
        assertEquals("line 2", content.get(1));
        assertEquals("line 3", content.get(2));
    }

    @Test
    void testAbortSession() throws IOException {
        Path newFile = tempDir.resolve("Aborted.java");
        editor.startCreate(newFile);

        editor.processLine("package com.test;");
        // El archivo ya existe porque se escribe en tiempo real
        assertTrue(Files.exists(newFile));

        editor.abort();

        // La sesión se cierra pero el archivo permanece (no se elimina lo escrito)
        assertFalse(editor.hasActiveSession());
        assertTrue(Files.exists(newFile)); // El archivo queda con lo que se escribió
        assertEquals(1, Files.readAllLines(newFile).size());
    }

    @Test
    void testAbortBeforeWriting() {
        Path newFile = tempDir.resolve("NeverWritten.java");
        editor.startCreate(newFile);
        // Abortamos antes de escribir cualquier línea
        editor.abort();

        assertFalse(editor.hasActiveSession());
        assertFalse(Files.exists(newFile)); // El archivo nunca se creó
    }

    @Test
    void testNoSessionError() {
        var result = editor.processLine("some code");
        assertEquals(LineByLineEditor.LineResult.Status.ERROR, result.status());
    }

    @Test
    void testOutputHandler() {
        Path newFile = tempDir.resolve("Output.java");
        editor.startCreate(newFile);

        editor.processLine("package test;");

        assertFalse(outputLog.isEmpty());
        assertTrue(outputLog.stream().anyMatch(s -> s.contains("EDITANDO")));
    }

    @Test
    void testSessionProgress() {
        Path newFile = tempDir.resolve("Progress.java");
        EditSession session = editor.startCreate(newFile);

        assertEquals(0, session.getValidatedCount());

        editor.processLine("line 1");
        assertEquals(1, session.getValidatedCount());

        editor.processLine("line 2");
        assertEquals(2, session.getValidatedCount());
    }

    @Test
    void testMultipleCorrections() {
        Path newFile = tempDir.resolve("Multi.java");
        editor.startCreate(newFile);

        // Error 1
        var r1 = editor.processLine("new UnknownA();");
        assertTrue(r1.needsCorrection());

        // Corregir con otro error
        var r2 = editor.correctLine("new UnknownB();");
        assertTrue(r2.needsCorrection());

        // Corregir correctamente
        var r3 = editor.correctLine("new Panel();");
        assertTrue(r3.isOk());
    }
}
