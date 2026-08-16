package com.boris.tooling.tool;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.commonmark.node.Document;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.boris.exceptions.BorisException;
import com.boris.tooling.ToolDefinition;

public class PdfGenerationTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Parser MARKDOWN_PARSER = Parser.builder().build();
    private static final HtmlRenderer HTML_RENDERER = HtmlRenderer.builder().build();

    public static ToolDefinition generate_pdf() {
        var contentProp = Map.of("type", "string", "description", "Content to convert to PDF (HTML, Markdown, or plain text)");
        var outputPathProp = Map.of("type", "string", "description", "Output file path for the PDF");
        var contentTypeProp = Map.of(
            "type", "string", 
            "description", "Type of content: 'html', 'markdown', or 'text'",
            "enum", new String[]{"html", "markdown", "text"}
        );
        
        var properties = new LinkedHashMap<String, Object>();
        properties.put("content", contentProp);
        properties.put("outputPath", outputPathProp);
        properties.put("contentType", contentTypeProp);
        
        var schema = new LinkedHashMap<String, Object>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", new String[]{"content", "outputPath", "contentType"});
        
        return ToolDefinition.of(
                "generate_pdf",
                "Generate a PDF file from HTML, Markdown, or plain text content.",
                schema);
    }

    public static String execute(Map<String, Object> args) {
        PdfGenerationTool tool = new PdfGenerationTool();
        return tool.generate(args);
    }

    public String generate(Map<String, Object> args) {
        @SuppressWarnings("unchecked")
        String content = (String) args.getOrDefault("content", "");
        @SuppressWarnings("unchecked")
        String outputPath = (String) args.getOrDefault("outputPath", "");
        @SuppressWarnings("unchecked")
        String contentType = (String) args.getOrDefault("contentType", "text");

        if (content == null || content.isBlank()) {
            return formatOutput(false, "Error: content is required");
        }
        if (outputPath == null || outputPath.isBlank()) {
            return formatOutput(false, "Error: outputPath is required");
        }
        if (contentType == null || contentType.isBlank()) {
            return formatOutput(false, "Error: contentType is required (html, markdown, or text)");
        }

        try {
            String textContent = convertToPlainText(content, contentType);
            createPdfFromText(textContent, outputPath);
            return formatOutput(true, "PDF generated successfully at: " + outputPath);
        } catch (Exception e) {
            throw new BorisException("Error generating PDF: " + e.getMessage(), e);
        }
    }

    private String convertToPlainText(String content, String contentType) {
        switch (contentType.toLowerCase()) {
            case "markdown":
                // Convert Markdown to HTML first, then strip HTML tags
                org.commonmark.node.Node document = MARKDOWN_PARSER.parse(content);
                String html = HTML_RENDERER.render(document);
                return stripHtmlTags(html);
            case "html":
                return stripHtmlTags(content);
            case "text":
            default:
                return content;
        }
    }

    private String stripHtmlTags(String html) {
        // Simple HTML tag removal
        return html.replaceAll("<[^>]*>", "").trim();
    }

    private void createPdfFromText(String text, String outputPath) throws IOException {
        Path output = Paths.get(outputPath);
        
        // Create parent directories if they don't exist
        if (output.getParent() != null) {
            Files.createDirectories(output.getParent());
        }

        try (PDDocument document = new PDDocument()) {
            PDPage page = new PDPage();
            document.addPage(page);

            PDPageContentStream contentStream = new PDPageContentStream(document, page);
            contentStream.beginText();
            contentStream.setFont(PDType1Font.HELVETICA, 12);
            contentStream.newLineAtOffset(50, 700);

            // Simple text wrapping and line handling
            String[] lines = text.split("\n");
            float margin = 50;
            float width = page.getMediaBox().getWidth() - 2 * margin;
            float fontSize = 12;
            float lineHeight = fontSize * 1.5f;
            float yPosition = 700;

            for (String line : lines) {
                // Simple word wrapping
                String[] words = line.split(" ");
                StringBuilder currentLine = new StringBuilder();

                for (String word : words) {
                    String testLine = currentLine.length() > 0 
                        ? currentLine + " " + word 
                        : word;
                    
                    float textWidth = PDType1Font.HELVETICA.getStringWidth(testLine) / 1000 * fontSize;
                    
                    if (textWidth > width && currentLine.length() > 0) {
                        // Write current line and start new one
                        contentStream.showText(currentLine.toString());
                        contentStream.newLineAtOffset(0, -lineHeight);
                        yPosition -= lineHeight;
                        currentLine = new StringBuilder(word);
                        
                        // Check if we need a new page
                        if (yPosition < 50) {
                            contentStream.endText();
                            contentStream.close();
                            
                            PDPage newPage = new PDPage();
                            document.addPage(newPage);
                            contentStream = new PDPageContentStream(document, newPage);
                            contentStream.beginText();
                            contentStream.setFont(PDType1Font.HELVETICA, fontSize);
                            contentStream.newLineAtOffset(margin, 700);
                            yPosition = 700;
                        }
                    } else {
                        if (currentLine.length() > 0) {
                            currentLine.append(" ");
                        }
                        currentLine.append(word);
                    }
                }
                
                if (currentLine.length() > 0) {
                    contentStream.showText(currentLine.toString());
                    contentStream.newLineAtOffset(0, -lineHeight);
                    yPosition -= lineHeight;
                    
                    // Check if we need a new page
                    if (yPosition < 50) {
                        contentStream.endText();
                        contentStream.close();
                        
                        PDPage newPage = new PDPage();
                        document.addPage(newPage);
                        contentStream = new PDPageContentStream(document, newPage);
                        contentStream.beginText();
                        contentStream.setFont(PDType1Font.HELVETICA, fontSize);
                        contentStream.newLineAtOffset(margin, 700);
                        yPosition = 700;
                    }
                }
            }
            
            contentStream.endText();
            contentStream.close();

            document.save(outputPath);
        }
    }

    private static String formatOutput(boolean success, String message) {
        try {
            ObjectNode node = MAPPER.createObjectNode();
            node.put("success", success);
            node.put("message", message);
            return MAPPER.writeValueAsString(node);
        } catch (IOException e) {
            throw new BorisException("Failed to format output", e);
        }
    }
}