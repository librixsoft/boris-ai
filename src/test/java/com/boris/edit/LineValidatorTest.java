package com.boris.edit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LineValidatorTest {

    private SymbolCache cache;
    private LineValidator validator;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        cache = new SymbolCache();

        String contextContent = """
            ### `BasicWindow`
            - `BasicWindow()`
            - `BasicWindow(String title)`
            - `setComponent(Component)`
            - `close()`

            ### `Panel`
            - `Panel()`
            - `addComponent(Component)`
            - `setLayoutManager(LayoutManager)`

            ### `Label`
            - `Label(String text)`
            - `setText(String)`

            ### `GridLayout`
            - `GridLayout(int columns)`
            """;

        Path contextFile = tempDir.resolve("CONTEXT.md");
        Files.writeString(contextFile, contextContent);
        cache.loadFromContext(contextFile);

        validator = new LineValidator(cache);
    }

    @Test
    void testValidImportStandard() {
        var result = validator.validate("import java.util.List;");
        assertTrue(result.isValid());
    }

    @Test
    void testValidImportKnownClass() {
        var result = validator.validate("import com.googlecode.lanterna.gui2.BasicWindow;");
        assertTrue(result.isValid());
    }

    @Test
    void testInvalidImportUnknownClass() {
        var result = validator.validate("import com.googlecode.lanterna.gui2.TextLabel;");
        assertFalse(result.isValid());
        assertTrue(result.errorMessage().contains("TextLabel"));
        assertTrue(result.hasSuggestions());
        assertTrue(result.suggestions().contains("Label"));
    }

    @Test
    void testValidNewInstance() {
        var result = validator.validate("Panel panel = new Panel();");
        assertTrue(result.isValid());
    }

    @Test
    void testInvalidNewInstance() {
        var result = validator.validate("TextLabel label = new TextLabel(\"hello\");");
        assertFalse(result.isValid());
        assertTrue(result.errorMessage().contains("TextLabel"));
    }

    @Test
    void testValidExtends() {
        var result = validator.validate("public class MyWindow extends BasicWindow {");
        assertTrue(result.isValid());
    }

    @Test
    void testInvalidExtends() {
        var result = validator.validate("public class MyWindow extends UnknownWindow {");
        assertFalse(result.isValid());
        assertTrue(result.errorMessage().contains("UnknownWindow"));
    }

    @Test
    void testSkipEmptyLines() {
        assertTrue(validator.validate("").isValid());
        assertTrue(validator.validate("   ").isValid());
    }

    @Test
    void testSkipComments() {
        assertTrue(validator.validate("// This is a comment").isValid());
        assertTrue(validator.validate("/* Block comment */").isValid());
        assertTrue(validator.validate(" * Middle of block").isValid());
    }

    @Test
    void testSkipBraces() {
        assertTrue(validator.validate("{").isValid());
        assertTrue(validator.validate("}").isValid());
    }

    @Test
    void testSkipPackageDeclaration() {
        assertTrue(validator.validate("package com.boris.app;").isValid());
    }

    @Test
    void testSkipAnnotations() {
        assertTrue(validator.validate("@Override").isValid());
        assertTrue(validator.validate("@SuppressWarnings(\"unchecked\")").isValid());
    }

    @Test
    void testSkipPrimitiveDeclarations() {
        assertTrue(validator.validate("int count = 0;").isValid());
        assertTrue(validator.validate("private String name;").isValid());
        assertTrue(validator.validate("public void doSomething() {").isValid());
    }

    @Test
    void testValidVariableDeclaration() {
        var result = validator.validate("Panel panel;");
        assertTrue(result.isValid());
    }

    @Test
    void testInvalidVariableDeclaration() {
        var result = validator.validate("UnknownType variable;");
        assertFalse(result.isValid());
    }

    @Test
    void testComplexLineWithKnownClasses() {
        var result = validator.validate("panel.setLayoutManager(new GridLayout(2));");
        assertTrue(result.isValid());
    }

    @Test
    void testComplexLineWithUnknownClass() {
        var result = validator.validate("panel.addComponent(new TextLabel(\"hello\"));");
        assertFalse(result.isValid());
        assertTrue(result.suggestions().contains("Label"));
    }

    @Test
    void testFormatError() {
        var result = validator.validate("new UnknownClass();");
        assertFalse(result.isValid());

        String formatted = result.formatError();
        assertTrue(formatted.contains("❌ ERROR"));
        assertTrue(formatted.contains("UnknownClass"));
    }
}
