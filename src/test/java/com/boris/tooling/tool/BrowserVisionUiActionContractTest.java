package com.boris.tooling.tool;

import com.boris.exceptions.InvalidToolArgumentException;
import com.boris.exceptions.VisualElementNotFoundException;
import com.boris.tooling.integration.ToolCallingConfig;
import com.boris.ui.automation.UiBrowserSessionManager;
import com.boris.ui.automation.VisionGroundingService;
import com.boris.ui.automation.model.BoundingBox;
import com.boris.ui.automation.model.ScreenCoordinate;
import com.boris.ui.automation.model.VisualElementTarget;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.microsoft.playwright.Keyboard;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Contrato de la acción browser_ui_action: observar, actuar y verificar.
 * Cada acción debe devolver evidencia observable y el fracaso debe ser visible.
 */
class BrowserVisionUiActionContractTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final byte[] FAKE_PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3, 4};
    private static final String CURRENT_URL = "https://www.linkedin.com/feed/";

    private UiBrowserSessionManager session;
    private Page page;
    private VisionGroundingService vision;
    private BrowserVisionUiTool tool;

    @BeforeEach
    void setUp() {
        session = mock(UiBrowserSessionManager.class);
        when(session.getViewportWidth()).thenReturn(1440);
        when(session.getViewportHeight()).thenReturn(900);
        page = mock(Page.class);
        when(session.getOrCreatePage(any())).thenReturn(page);
        Mouse mouse = mock(Mouse.class);
        when(page.mouse()).thenReturn(mouse);
        Keyboard keyboard = mock(Keyboard.class);
        when(page.keyboard()).thenReturn(keyboard);
        when(page.url()).thenReturn(CURRENT_URL);
        when(page.screenshot()).thenReturn(FAKE_PNG);
        vision = mock(VisionGroundingService.class);
        tool = new BrowserVisionUiTool(session, vision);
    }

    private static VisualElementTarget target(String label, int x, int y, int w, int h, double confidence) {
        return new VisualElementTarget(label, new BoundingBox(x, y, w, h), new ScreenCoordinate(x + w / 2, y + h / 2), confidence);
    }

    @Test
    void readScreenReturnsVisibleTextWithCoordinatesAndUrl() throws Exception {
        when(vision.findAllText(any(byte[].class))).thenReturn(List.of(
                target("Buscar jobs", 100, 200, 120, 30, 0.92),
                target("Conectar", 900, 120, 80, 24, 0.81)));

        String result = tool.executeBrowserAction("read_screen", null, null, null, null, null, null);
        JsonNode json = MAPPER.readTree(result);

        assertEquals("success", json.get("status").asText());
        assertEquals("read_screen", json.get("action").asText());
        assertEquals(CURRENT_URL, json.get("url_after").asText());
        assertFalse(json.get("changed").asBoolean());
        assertTrue(json.has("screen_hash_before"));
        assertTrue(json.has("screen_hash_after"));
        JsonNode elements = json.get("elements");
        assertTrue(elements.isArray(), "read_screen debe devolver elements");
        assertEquals(2, elements.size());
        assertEquals("Buscar jobs", elements.get(0).get("label").asText());
        assertEquals(100, elements.get(0).get("x").asInt());
        assertEquals(200, elements.get(0).get("y").asInt());
        assertEquals(120, elements.get(0).get("width").asInt());
        assertEquals(30, elements.get(0).get("height").asInt());
        assertEquals(0.92, elements.get(0).get("confidence").asDouble(), 1e-9);
    }

    @Test
    void missingElementProducesStructuredErrorWithoutSuccess() {
        when(vision.findVisualElement(any(byte[].class), anyString()))
                .thenThrow(new VisualElementNotFoundException("No se encontró 'Buscar'"));
        when(vision.findAllText(any(byte[].class))).thenReturn(List.of(
                target("Conectar", 900, 120, 80, 24, 0.81)));

        VisualElementNotFoundException ex = assertThrows(VisualElementNotFoundException.class,
                () -> tool.executeBrowserAction("click_text", null, "Buscar", null, null, null, null));

        assertTrue(ex.getMessage().contains("Candidatas cercanas"),
                "El error debe listar candidatas cercanas: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("Conectar"));
    }

    @Test
    void actionRequiringChangeWithoutScreenChangeReturnsFailedAndChangedFalse() throws Exception {
        String result = tool.executeBrowserAction("scroll", null, null, null, null, null, "down");
        JsonNode json = MAPPER.readTree(result);

        assertEquals("failed", json.get("status").asText());
        assertFalse(json.get("changed").asBoolean());
        assertTrue(json.has("cause"));
        assertFalse(result.contains("\"status\":\"success\""),
                "El resultado nunca debe contener success cuando changed=false y la acción requería cambio");
    }

    @Test
    void scrollNoneDoesNotRequireScreenChange() throws Exception {
        String result = tool.executeBrowserAction("scroll", null, null, null, null, null, "none");
        JsonNode json = MAPPER.readTree(result);

        assertEquals("success", json.get("status").asText());
        assertFalse(json.get("changed").asBoolean());
    }

    @Test
    void legacyNumericScrollDirectionIsRejected() {
        InvalidToolArgumentException ex = assertThrows(InvalidToolArgumentException.class,
                () -> tool.executeBrowserAction("scroll", null, null, null, null, null, "0"));
        assertTrue(ex.getMessage().contains("'none'"));
        assertTrue(ex.getMessage().contains("0"));
    }

    @Test
    void unknownPressKeyIsRejectedWithAllowedList() {
        InvalidToolArgumentException ex = assertThrows(InvalidToolArgumentException.class,
                () -> tool.executeBrowserAction("press_key", null, null, null, null, "f9", null));
        assertTrue(ex.getMessage().contains("enter"));
    }

    @Test
    void pressKeyNormalizesLowercaseNameToPlaywrightCanonicalForm() throws Exception {
        String result = tool.executeBrowserAction("press_key", null, null, null, null, "enter", null);
        JsonNode json = MAPPER.readTree(result);

        assertEquals("Enter", json.get("key").asText());
    }

    @Test
    void browserSchemaDeclaresEnumsForActionAndScrollDirection() {
        ToolCallingConfig config = new ToolCallingConfig(
                new com.boris.tooling.tool.DeleteTool(),
                new com.boris.tooling.tool.EditTool(),
                new com.boris.tooling.tool.SystemInfoTool(),
                new com.boris.tooling.tool.PdfGenerationTool(),
                new com.boris.tooling.tool.OfficeDocumentTool(),
                new com.boris.settings.Settings(new com.boris.settings.ModelConfig("http://localhost:11434", "test"), null));
        var registry = com.boris.tooling.engine.ToolRegistry.from(config);

        var schema = registry.getTool("browser_ui_action").openAiToolSchema();
        @SuppressWarnings("unchecked")
        var function = (java.util.Map<String, Object>) schema.get("function");
        @SuppressWarnings("unchecked")
        var parameters = (java.util.Map<String, Object>) function.get("parameters");
        @SuppressWarnings("unchecked")
        var properties = (java.util.Map<String, Object>) parameters.get("properties");

        @SuppressWarnings("unchecked")
        var actionEnum = (java.util.List<String>) ((java.util.Map<String, Object>) properties.get("action")).get("enum");
        assertEquals(java.util.List.of("open", "read_screen", "click_text", "focus_and_type", "press_key", "scroll", "screenshot"), actionEnum);

        @SuppressWarnings("unchecked")
        var scrollEnum = (java.util.List<String>) ((java.util.Map<String, Object>) properties.get("scroll_direction")).get("enum");
        assertEquals(java.util.List.of("none", "up", "down"), scrollEnum);
    }
}
