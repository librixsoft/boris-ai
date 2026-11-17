package com.boris.ui.automation;

import com.boris.ui.automation.model.BoundingBox;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.*;

class BrowserActionLoggerTest {

    @TempDir
    static Path tempDir;

    @Test
    void logActionWritesParseableJsonLineAndSavesArtifact() throws Exception {
        String sessionId = "test-session-" + System.nanoTime();
        BrowserActionLogger logger = new BrowserActionLogger(sessionId);

        BoundingBox bbox = new BoundingBox(100, 200, 50, 30);
        byte[] png = new byte[]{1, 2, 3, 4};

        String artifactPath = logger.logAction(
                "click_text", "linkedin.com", "Buscar", null, "Enter", "none",
                "https://linkedin.com", "https://linkedin.com/search",
                "Buscar", bbox, 0.95, png, "success", null);

        // Verificar que el log tiene una línea JSON parseable (la última línea)
        Path logFile = getLogFileForTest(sessionId);
        assertTrue(Files.exists(logFile));
        String content = new String(Files.readAllBytes(logFile));
        assertFalse(content.isBlank());

        String lastLine = content.trim().lines().reduce((first, second) -> second).orElse("");
        JsonNode node = new ObjectMapper().readTree(lastLine);
        assertEquals(sessionId, node.get("session").asText());
        assertEquals("click_text", node.get("action").asText());
        assertEquals("linkedin.com", node.get("target_domain").asText());
        assertEquals("Buscar", node.get("click_target_text").asText());
        assertEquals("Enter", node.get("press_key_after").asText());
        assertEquals("none", node.get("scroll_direction").asText());
        assertEquals("https://linkedin.com", node.get("url_before").asText());
        assertEquals("https://linkedin.com/search", node.get("url_after").asText());
        assertEquals("Buscar", node.get("element_label").asText());
        assertEquals(0.95, node.get("confidence").asDouble(), 0.001);
        assertEquals("success", node.get("result").asText());
        JsonNode causeNode = node.get("cause");
        assertTrue(causeNode == null || causeNode.isNull());

        // Verificar bounding box
        JsonNode bboxNode = node.get("bounding_box");
        assertNotNull(bboxNode);
        assertEquals(100, bboxNode.get("x").asInt());
        assertEquals(200, bboxNode.get("y").asInt());
        assertEquals(50, bboxNode.get("width").asInt());
        assertEquals(30, bboxNode.get("height").asInt());

        // Verificar artifact_path (puede ser null si no se pudo escribir el archivo)
        if (node.get("artifact_path") != null) {
            assertTrue(node.get("artifact_path").asText().endsWith(".png"));
            Path artifactFile = Path.of(node.get("artifact_path").asText());
            assertTrue(Files.exists(artifactFile));
        }
    }

    @Test
    void logActionWithoutScreenshotDoesNotThrow() throws Exception {
        String sessionId = "test-session-" + System.nanoTime();
        BrowserActionLogger logger = new BrowserActionLogger(sessionId);

        // No debe lanzar excepción aunque no haya screenshot
        assertDoesNotThrow(() -> logger.logAction(
                "read_screen", "google.com", null, null, null, "none",
                "https://google.com", "https://google.com",
                null, null, 0.0, null, "success", null));
    }

    @Test
    void logActionWithFailureRecordsCause() throws Exception {
        String sessionId = "test-session-" + System.nanoTime();
        BrowserActionLogger logger = new BrowserActionLogger(sessionId);

        String artifactPath = logger.logAction(
                "click_text", "linkedin.com", "Buscar", null, null, "none",
                "https://linkedin.com", "https://linkedin.com",
                "Buscar", null, 0.0, null, "failure", "VisualElementNotFoundException: no se encontró 'Buscar'");

        // Verificar que el log tiene una línea JSON parseable (la última línea)
        Path logFile = getLogFileForTest(sessionId);
        String content = new String(Files.readAllBytes(logFile));
        String lastLine = content.trim().lines().reduce((first, second) -> second).orElse("");
        JsonNode node = new ObjectMapper().readTree(lastLine);
        assertEquals("failure", node.get("result").asText());
        assertEquals("VisualElementNotFoundException: no se encontró 'Buscar'", node.get("cause").asText());
    }

    @Test
    void sequenceNumbersIncrement() throws Exception {
        String sessionId = "test-session-" + System.nanoTime();
        BrowserActionLogger logger = new BrowserActionLogger(sessionId);

        logger.logAction("click_text", "a.com", "x", null, null, "none", "u1", "u2", "x", null, 0.0, null, "success", null);
        logger.logAction("click_text", "a.com", "y", null, null, "none", "u1", "u2", "y", null, 0.0, null, "success", null);

        Path logFile = getLogFileForTest(sessionId);
        String content = new String(Files.readAllBytes(logFile));
        String[] lines = content.trim().split("\n");
        assertEquals(2, lines.length);

        ObjectMapper mapper = new ObjectMapper();
        assertEquals(1, mapper.readTree(lines[0]).get("sequence").asInt());
        assertEquals(2, mapper.readTree(lines[1]).get("sequence").asInt());
    }

    // Helper de prueba para acceder al log file
    static Path getLogFileForTest(String sessionId) {
        return Paths.get(System.getProperty("user.home"))
                .resolve(".boris")
                .resolve("browser-artifacts")
                .resolve(sessionId)
                .resolve("actions.log");
    }
}
