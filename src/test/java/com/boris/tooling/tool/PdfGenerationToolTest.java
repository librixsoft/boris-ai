package com.boris.tooling.tool;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class PdfGenerationToolTest {

    @TempDir
    Path tempDir;

    private PdfGenerationTool pdfTool;

    @BeforeEach
    void setUp() {
        pdfTool = new PdfGenerationTool();
    }

    @Test
    void generate_pdf_createsPdfFromPlainText() throws Exception {
        Path outputFile = tempDir.resolve("test_plain.pdf");
        String content = "This is plain text content for PDF generation.";

        String result = pdfTool.execute(Map.of(
            "content", content,
            "outputPath", outputFile.toString(),
            "contentType", "text"
        ));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(Files.exists(outputFile));
        assertTrue(Files.size(outputFile) > 0);
    }

    @Test
    void generate_pdf_createsPdfFromMarkdown() throws Exception {
        Path outputFile = tempDir.resolve("test_markdown.pdf");
        String content = "# Heading\n\nThis is **bold** and *italic* text.";

        String result = pdfTool.execute(Map.of(
            "content", content,
            "outputPath", outputFile.toString(),
            "contentType", "markdown"
        ));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(Files.exists(outputFile));
        assertTrue(Files.size(outputFile) > 0);
    }

    @Test
    void generate_pdf_createsPdfFromHtml() throws Exception {
        Path outputFile = tempDir.resolve("test_html.pdf");
        String content = "<h1>Heading</h1><p>This is <b>bold</b> and <i>italic</i> text.</p>";

        String result = pdfTool.execute(Map.of(
            "content", content,
            "outputPath", outputFile.toString(),
            "contentType", "html"
        ));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(Files.exists(outputFile));
        assertTrue(Files.size(outputFile) > 0);
    }

    @Test
    void generate_pdf_createsParentDirectories() throws Exception {
        Path deepDir = tempDir.resolve("a/b/c");
        Path outputFile = deepDir.resolve("test.pdf");

        String result = pdfTool.execute(Map.of(
            "content", "test content",
            "outputPath", outputFile.toString(),
            "contentType", "text"
        ));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(Files.exists(outputFile));
    }

    @Test
    void generate_pdf_returnsError_whenContentBlank() {
        String result = pdfTool.execute(Map.of(
            "content", "",
            "outputPath", "/tmp/test.pdf",
            "contentType", "text"
        ));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("content is required"));
    }

    @Test
    void generate_pdf_returnsError_whenOutputPathBlank() {
        String result = pdfTool.execute(Map.of(
            "content", "test content",
            "outputPath", "",
            "contentType", "text"
        ));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("outputPath is required"));
    }

    @Test
    void generate_pdf_returnsError_whenContentTypeBlank() {
        String result = pdfTool.execute(Map.of(
            "content", "test content",
            "outputPath", "/tmp/test.pdf",
            "contentType", ""
        ));

        assertTrue(result.contains("\"success\":false"));
        assertTrue(result.contains("contentType is required"));
    }

    @Test
    void generate_pdf_handlesLongTextWithWrapping() throws Exception {
        Path outputFile = tempDir.resolve("long_text.pdf");
        String longContent = "This is a very long line of text that should be wrapped automatically when converted to PDF format to ensure proper formatting and readability in the generated document.";

        String result = pdfTool.execute(Map.of(
            "content", longContent,
            "outputPath", outputFile.toString(),
            "contentType", "text"
        ));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(Files.exists(outputFile));
    }

    @Test
    void generate_pdf_handlesMultilineText() throws Exception {
        Path outputFile = tempDir.resolve("multiline.pdf");
        String multilineContent = "Line 1\nLine 2\nLine 3\nLine 4\nLine 5";

        String result = pdfTool.execute(Map.of(
            "content", multilineContent,
            "outputPath", outputFile.toString(),
            "contentType", "text"
        ));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(Files.exists(outputFile));
    }

    @Test
    void generate_pdf_definition_hasParametersSchema() {
        var def = pdfTool.generate_pdf();
        Map<String, Object> props = (Map<String, Object>) def.parameters().get("properties");
        assertNotNull(props);
        assertTrue(props.containsKey("content"));
        assertTrue(props.containsKey("outputPath"));
        assertTrue(props.containsKey("contentType"));
    }

    @Test
    void generate_pdf_handlesCaseInsensitiveContentType() throws Exception {
        Path outputFile = tempDir.resolve("case_insensitive.pdf");

        String result = pdfTool.execute(Map.of(
            "content", "test content",
            "outputPath", outputFile.toString(),
            "contentType", "TEXT" // uppercase
        ));

        assertTrue(result.contains("\"success\":true"));
        assertTrue(Files.exists(outputFile));
    }
}