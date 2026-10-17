package com.boris.edit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import com.boris.tooling.DiffLineNotifier;

import static org.junit.jupiter.api.Assertions.*;

class StreamingFileWriterTest {

    @TempDir
    Path tempDir;

    private StreamingFileWriter writer;
    private List<String> notifications;

    @BeforeEach
    void setUp() {
        writer = new StreamingFileWriter();
        notifications = new ArrayList<>();
        DiffLineNotifier.clearListeners();
        DiffLineNotifier.addListener(notifications::add);
    }

    @Test
    void testDetectsCodeBlockWithPath() {
        String input = "Texto normal\n```java:test/App.java\npackage test;\n```\nMás texto";

        Path targetFile = tempDir.resolve("test/App.java");
        String inputWithAbsPath = input.replace("test/App.java", targetFile.toString());

        String result = writer.processChunk(inputWithAbsPath);
        writer.finish();

        assertTrue(Files.exists(targetFile), "El archivo debería existir");
    }

    @Test
    void testWritesContentToFile() throws IOException {
        Path targetFile = tempDir.resolve("src/MiClase.java");

        String input = "```java:" + targetFile + "\n" +
                      "package com.test;\n" +
                      "\n" +
                      "public class MiClase {\n" +
                      "}\n" +
                      "```\n";

        writer.processChunk(input);
        writer.finish();

        assertTrue(Files.exists(targetFile));
        String content = Files.readString(targetFile);
        assertTrue(content.contains("package com.test;"));
        assertTrue(content.contains("public class MiClase"));
    }

    @Test
    void testReturnsNonCodeText() {
        Path targetFile = tempDir.resolve("test.java");

        String input = "Esto es texto normal\n" +
                      "```java:" + targetFile + "\n" +
                      "codigo\n" +
                      "```\n" +
                      "Más texto normal\n";

        String result = writer.processChunk(input);
        writer.finish();

        assertTrue(result.contains("Esto es texto normal"));
        assertTrue(result.contains("Más texto normal"));
        assertFalse(result.contains("codigo"));
    }

    @Test
    void testHandlesChunkedInput() throws IOException {
        Path targetFile = tempDir.resolve("chunked/Test.java");

        // Simular chunks como llegarían del streaming
        writer.processChunk("Hola\n```java:" + targetFile + "\n");
        writer.processChunk("package test;\n");
        writer.processChunk("public class Test {\n");
        writer.processChunk("}\n");
        writer.processChunk("```\nFin");
        writer.finish();

        assertTrue(Files.exists(targetFile));
        String content = Files.readString(targetFile);
        assertEquals("package test;\npublic class Test {\n}\n", content);
    }

    @Test
    void testNotifiesUIForEachLine() throws IOException {
        Path targetFile = tempDir.resolve("notify/Test.java");

        String input = "```java:" + targetFile + "\n" +
                      "linea1\n" +
                      "linea2\n" +
                      "linea3\n" +
                      "```\n";

        writer.processChunk(input);
        writer.finish();

        // Debería notificar: header + 3 líneas + footer
        assertTrue(notifications.size() >= 4);
        assertTrue(notifications.stream().anyMatch(n -> n.contains("@@@")));
        assertTrue(notifications.stream().anyMatch(n -> n.contains("linea1")));
        assertTrue(notifications.stream().anyMatch(n -> n.contains("linea2")));
        assertTrue(notifications.stream().anyMatch(n -> n.contains("linea3")));
    }

    @Test
    void testHandlesMultipleFiles() throws IOException {
        Path file1 = tempDir.resolve("multi/File1.java");
        Path file2 = tempDir.resolve("multi/File2.java");

        String input = "```java:" + file1 + "\n" +
                      "contenido1\n" +
                      "```\n" +
                      "Texto entre archivos\n" +
                      "```java:" + file2 + "\n" +
                      "contenido2\n" +
                      "```\n";

        writer.processChunk(input);
        writer.finish();

        assertTrue(Files.exists(file1));
        assertTrue(Files.exists(file2));
        assertEquals("contenido1\n", Files.readString(file1));
        assertEquals("contenido2\n", Files.readString(file2));
    }

    @Test
    void testResetClearsState() throws IOException {
        Path file1 = tempDir.resolve("reset/File1.java");

        String input1 = "```java:" + file1 + "\n" +
                       "contenido1\n" +
                       "```\n";

        writer.processChunk(input1);
        writer.finish();

        // Reset
        writer.reset();

        assertFalse(writer.isWritingFile());
        assertNull(writer.getCurrentFilePath());
    }

    @Test
    void testIsWritingFileReturnsTrueInsideBlock() {
        Path targetFile = tempDir.resolve("writing/Test.java");

        assertFalse(writer.isWritingFile());

        writer.processChunk("```java:" + targetFile + "\n");
        assertTrue(writer.isWritingFile());

        writer.processChunk("contenido\n");
        assertTrue(writer.isWritingFile());

        writer.processChunk("```\n");
        writer.finish();
        assertFalse(writer.isWritingFile());
    }

    @Test
    void testHandlesEmptyContent() throws IOException {
        Path targetFile = tempDir.resolve("empty/Empty.java");

        String input = "```java:" + targetFile + "\n```\n";

        writer.processChunk(input);
        writer.finish();

        // Empty content blocks should NOT create files (no point in empty files)
        assertFalse(Files.exists(targetFile));
    }

    @Test
    void testHandlesLanguageWithoutColon() {
        // Bloque normal de código (sin path) no debería crear archivo
        String input = "```java\ncodigo normal\n```\n";

        String result = writer.processChunk(input);
        writer.finish();

        // Todo debería pasar como texto normal
        assertTrue(result.contains("```java") || result.contains("codigo normal"));
    }

    @Test
    void testCreatesParentDirectories() throws IOException {
        Path targetFile = tempDir.resolve("deep/nested/path/Test.java");

        String input = "```java:" + targetFile + "\n" +
                      "contenido\n" +
                      "```\n";

        writer.processChunk(input);
        writer.finish();

        assertTrue(Files.exists(targetFile));
        assertTrue(Files.exists(targetFile.getParent()));
    }
}
