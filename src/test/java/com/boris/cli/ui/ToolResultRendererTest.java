package com.boris.cli.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ToolResultRendererTest {

    @Test
    void isToolResultLineDetectsJsonWithSuccess() {
        assertTrue(ToolResultRenderer.isToolResultLine("{\"tool\": \"delete_file\", \"path\": \"/tmp/x\", \"success\": true}"));
    }

    @Test
    void isToolResultLineDetectsJsonWithPath() {
        assertTrue(ToolResultRenderer.isToolResultLine("{\"path\": \"/tmp/x\", \"success\": true}"));
    }

    @Test
    void isToolResultLineDetectsJsonWithMessage() {
        assertTrue(ToolResultRenderer.isToolResultLine("{\"message\": \"ok\", \"success\": true}"));
    }

    @Test
    void isToolResultLineDetectsJsonWithDeleted() {
        assertTrue(ToolResultRenderer.isToolResultLine("{\"deleted\": true, \"path\": \"/tmp/x\"}"));
    }

    @Test
    void isToolResultLineRejectsNonJson() {
        assertFalse(ToolResultRenderer.isToolResultLine("hello world"));
        assertFalse(ToolResultRenderer.isToolResultLine(":: thinking"));
        assertFalse(ToolResultRenderer.isToolResultLine("```java"));
        assertFalse(ToolResultRenderer.isToolResultLine(""));
        assertFalse(ToolResultRenderer.isToolResultLine(null));
    }

    @Test
    void isToolResultLineRejectsJsonWithoutToolResultKeys() {
        assertFalse(ToolResultRenderer.isToolResultLine("{\"foo\": \"bar\"}"));
        assertFalse(ToolResultRenderer.isToolResultLine("{\"name\": \"test\"}"));
    }

    @Test
    void getToolEmojiWriteFile() {
        assertEquals("📝", ToolResultRenderer.getToolEmoji("write_file"));
    }

    @Test
    void getToolEmojiDeleteFile() {
        assertEquals("🗑️", ToolResultRenderer.getToolEmoji("delete_file"));
    }

    @Test
    void getToolEmojiReadFile() {
        assertEquals("📄", ToolResultRenderer.getToolEmoji("read_file"));
    }

    @Test
    void getToolEmojiApplyEdit() {
        assertEquals("✏️", ToolResultRenderer.getToolEmoji("apply_edit"));
    }

    @Test
    void getToolEmojiBash() {
        assertEquals("🐧", ToolResultRenderer.getToolEmoji("bash"));
    }

    @Test
    void getToolEmojiWebSearch() {
        assertEquals("𔍀", ToolResultRenderer.getToolEmoji("web_search"));
    }

    @Test
    void getToolEmojiWebFetch() {
        assertEquals("🌐", ToolResultRenderer.getToolEmoji("web_fetch"));
    }

    @Test
    void getToolEmojiGit() {
        assertEquals("📦", ToolResultRenderer.getToolEmoji("git"));
    }

    @Test
    void getToolEmojiBatchReadFiles() {
        assertEquals("📂", ToolResultRenderer.getToolEmoji("batch_read_files"));
    }

    @Test
    void getToolEmojiDefault() {
        assertEquals("🛠️", ToolResultRenderer.getToolEmoji("unknown_tool"));
    }

    @Test
    void formatToolResultExtractsPathAndTool() {
        String result = ToolResultRenderer.formatToolResult("{\"tool\": \"delete_file\", \"path\": \"/Users/lastprophet/Downloads/hola.md\", \"success\": true}");
        assertEquals("🗑️ delete_file: /Users/lastprophet/Downloads/hola.md", result);
    }

    @Test
    void formatToolResultWithPathOnly() {
        String result = ToolResultRenderer.formatToolResult("{\"path\": \"/tmp/file.txt\", \"success\": true}");
        assertEquals("🛠️ : /tmp/file.txt", result);
    }

    @Test
    void formatToolResultThrowsOnInvalidJson() {
        assertThrows(IllegalArgumentException.class, () -> ToolResultRenderer.formatToolResult("not json"));
    }

    @Test
    void formatToolResultThrowsOnMissingPath() {
        assertThrows(IllegalArgumentException.class, () -> ToolResultRenderer.formatToolResult("{\"tool\": \"delete_file\", \"success\": true}"));
    }

    @Test
    void formatToolResultHandlesNull() {
        assertThrows(IllegalArgumentException.class, () -> ToolResultRenderer.formatToolResult(null));
    }

    @Test
    void isToolResultLineDetectsJsonWithHtmlInPath() {
        assertTrue(ToolResultRenderer.isToolResultLine("{\"tool\": \"write_file\", \"path\": \"<meta charset=\\\"UTF-8\\\">\", \"success\": true}"));
    }

    @Test
    void isToolResultLineDetectsJsonWithPathOnly() {
        assertTrue(ToolResultRenderer.isToolResultLine("{\"path\": \"/Users/lastprophet/Downloads/hola.md\"}"));
    }

    @Test
    void isToolResultLineDetectsJsonWithEmbeddedQuotes() {
        assertTrue(ToolResultRenderer.isToolResultLine("{\"tool\": \"write_file\", \"path\": \"/tmp/file\", \"message\": \"ok\"}"));
    }

    @Test
    void isToolResultLineRejectsJsonWithExtraPrefix() {
        assertFalse(ToolResultRenderer.isToolResultLine("prefix {\"tool\": \"delete_file\", \"path\": \"/tmp/x\", \"success\": true}"));
    }

    @Test
    void isToolResultLineRejectsJsonWithExtraSuffix() {
        assertFalse(ToolResultRenderer.isToolResultLine("{\"tool\": \"delete_file\", \"path\": \"/tmp/x\", \"success\": true} suffix"));
    }

    @Test
    void formatToolResultRejectsHtmlInlineInPath() {
        assertThrows(InvalidToolResultPathException.class, () ->
                ToolResultRenderer.formatToolResult("{\"tool\": \"write_file\", \"path\": \"<meta charset=\\\"UTF-8\\\">\", \"success\": true}"));
    }

    @Test
    void formatToolResultHandlesPathOnly() {
        String result = ToolResultRenderer.formatToolResult("{\"path\": \"/Users/lastprophet/Downloads/hola.md\", \"success\": true}");
        assertEquals("🛠️ : /Users/lastprophet/Downloads/hola.md", result);
    }

    @Test
    void formatToolResultThrowsOnUnescapedQuotes() {
        assertThrows(IllegalArgumentException.class, () -> ToolResultRenderer.formatToolResult("{\"path\": \"/tmp/file\", \"message\": \"broken"));
    }

    @Test
    void formatToolResultRejectsAngleBracketsInPath() {
        assertThrows(InvalidToolResultPathException.class, () -> ToolResultRenderer.formatToolResult("{\"tool\": \"write_file\", \"path\": \"/tmp/<file>.txt\", \"success\": true}"));
    }

    @Test
    void formatToolResultRejectsHtmlEntityInPath() {
        assertThrows(InvalidToolResultPathException.class, () -> ToolResultRenderer.formatToolResult("{\"tool\": \"write_file\", \"path\": \"/tmp/file&amp;.txt\", \"success\": true}"));
    }
}
