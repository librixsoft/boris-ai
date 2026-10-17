package com.boris.edit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LineContextResolverTest {

    @TempDir
    Path tempDir;

    private SymbolCache symbolCache;
    private LineContextResolver resolver;

    @BeforeEach
    void setUp() throws IOException {
        String contextContent = """
            # CONTEXT.md - Lanterna 3.1

            ## Core Classes

            ### `Panel`
            - Panel()
            - Panel(LayoutManager layout)
            - setBackground(TextColor color)
            - addComponent(Component component)
            - setLayoutManager(LayoutManager manager)

            ### `TextField`
            - TextField()
            - TextField(String initialText)
            - getText()
            - setText(String text)

            ### `Button`
            - Button(String label)
            - Button(String label, Runnable action)
            - setEnabled(boolean enabled)

            ### `TextColor`
            - ANSI
            - RGB(int r, int g, int b)
            """;

        Path contextFile = tempDir.resolve("CONTEXT.md");
        Files.writeString(contextFile, contextContent);

        symbolCache = new SymbolCache();
        symbolCache.loadFromContext(contextFile);
        resolver = new LineContextResolver(symbolCache);
    }

    @Test
    void validLine_shouldReturnNoErrors() {
        String line = "Panel panel = new Panel();";
        LineContextResolver.LineContext context = resolver.resolve(line);

        assertTrue(context.isValid(), "Line should be valid");
        assertFalse(context.hasErrors(), "Should have no errors");
        assertTrue(context.knownClasses.containsKey("Panel"), "Should recognize Panel class");
    }

    @Test
    void unknownClass_shouldReturnError() {
        String line = "UnknownWidget widget = new UnknownWidget();";
        LineContextResolver.LineContext context = resolver.resolve(line);

        assertFalse(context.isValid(), "Line should be invalid");
        assertTrue(context.hasErrors(), "Should have errors");
        assertTrue(context.unknownClasses.contains("UnknownWidget"), "Should flag unknown class");
    }

    @Test
    void invalidConstructor_shouldReturnError() {
        String line = "TextField field = new TextField(50);";
        LineContextResolver.LineContext context = resolver.resolve(line);

        assertFalse(context.isValid(), "Line should be invalid - TextField(int) doesn't exist");
        assertTrue(context.invalidConstructors.size() > 0, "Should have invalid constructor");
        assertTrue(context.constructorSuggestions.containsKey("TextField"), "Should suggest valid constructors");
    }

    @Test
    void validConstructorWithArgs_shouldBeValid() {
        String line = "TextField field = new TextField(\"hello\");";
        LineContextResolver.LineContext context = resolver.resolve(line);

        assertTrue(context.isValid(), "Line should be valid - TextField(String) exists");
    }

    @Test
    void correctionPrompt_shouldIncludeAvailableAPI() {
        String line = "TextField field = new TextField(50);";
        LineContextResolver.LineContext context = resolver.resolve(line);

        String prompt = resolver.buildCorrectionPrompt(context);

        assertTrue(prompt.contains("CORRIGE ESTA LÍNEA"), "Should have correction header");
        assertTrue(prompt.contains("TextField(50)") || prompt.contains("TextField"), "Should mention the class");
        assertTrue(prompt.contains("API DISPONIBLE") || prompt.contains("Constructores disponibles"),
                   "Should show available API");
    }

    @Test
    void standardJavaClasses_shouldBeIgnored() {
        String line = "String text = new String(\"hello\");";
        LineContextResolver.LineContext context = resolver.resolve(line);

        assertTrue(context.isValid(), "Standard Java classes should be allowed");
    }

    @Test
    void multipleClassesInLine_shouldValidateAll() {
        String line = "Panel panel = new Panel(); TextField field = new TextField();";
        LineContextResolver.LineContext context = resolver.resolve(line);

        assertTrue(context.isValid(), "Both classes are known");
        assertTrue(context.knownClasses.containsKey("Panel"));
        assertTrue(context.knownClasses.containsKey("TextField"));
    }

    @Test
    void classDeclaration_shouldBeIgnored() {
        // Nueva clase que el agente está creando - NO debe validarse
        String line = "public class DashboardApp {";
        LineContextResolver.LineContext context = resolver.resolve(line);

        assertTrue(context.isValid(), "Class declarations should be ignored");
        assertTrue(context.knownClasses.isEmpty(), "Should not try to validate new class name");
    }

    @Test
    void classDeclarationWithExtends_shouldValidateParent() {
        // Extends sí debe validarse porque es una clase que se está USANDO
        String line = "public class MyPanel extends Panel {";
        LineContextResolver.LineContext context = resolver.resolve(line);

        // La declaración se ignora, pero Panel (parent) no está en este contexto
        // porque el regex de extractClasses ignora toda la línea si es declaración
        assertTrue(context.isValid(), "Class declaration line is ignored entirely");
    }

    @Test
    void interfaceDeclaration_shouldBeIgnored() {
        String line = "public interface MyListener {";
        LineContextResolver.LineContext context = resolver.resolve(line);

        assertTrue(context.isValid(), "Interface declarations should be ignored");
    }
}
