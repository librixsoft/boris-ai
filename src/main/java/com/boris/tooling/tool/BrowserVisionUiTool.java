package com.boris.tooling.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Page;
import com.boris.exceptions.BorisException;
import com.boris.exceptions.VisualElementNotFoundException;
import com.boris.tooling.annotation.Tool;
import com.boris.tooling.annotation.ToolParam;
import com.boris.ui.automation.*;
import com.boris.ui.automation.model.*;

/**
 * Herramienta universal para automatización de navegador a nivel visual (Zero-DOM).
 * Puede buscar en Google, interactuar en LinkedIn, o manipular cualquier aplicación web en Chrome.
 */
public class BrowserVisionUiTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int VIEWPORT_WIDTH = 1440;
    private static final int VIEWPORT_HEIGHT = 900;
    private static final long DEFAULT_STEP_WAIT_MS = 1500L;
    private static final int DEFAULT_SCROLL_DELTA_Y = 400;

    private final UiBrowserSessionManager session;

    public BrowserVisionUiTool() {
        this(SharedSessionHolder.INSTANCE);
    }

    BrowserVisionUiTool(UiBrowserSessionManager session) {
        if (session == null) {
            throw new IllegalArgumentException("session cannot be null");
        }
        this.session = session;
    }

    private static final class SharedSessionHolder {
        private static final UiBrowserSessionManager INSTANCE = createSharedSession();

        private static UiBrowserSessionManager createSharedSession() {
            UiBrowserSessionManager manager = new UiBrowserSessionManager();
            Runtime.getRuntime().addShutdownHook(
                new Thread(manager::close, "boris-playwright-shutdown"));
            return manager;
        }
    }

    @Tool(
        name = "browser_ui_action",
        description = "Abre automáticamente una ventana visible de Chromium gestionada por Playwright y automatiza tareas visualmente (Google, LinkedIn, etc.) simulando mouse y teclado humano sin tocar el DOM. No requiere Google Chrome abierto, ni depuración remota, ni comandos en terminal. Ejecuta siempre esta herramienta directamente cuando te pidan interactuar o buscar en el navegador."
    )
    public String executeBrowserAction(
        @ToolParam(name = "target_domain", description = "Dominio o URL de la pestaña objetivo (ej. 'google.com', 'linkedin.com'). Dejar vacío para usar la pestaña activa actual.") String targetDomain,
        @ToolParam(name = "click_target_text", description = "Texto visual del elemento o botón a pulsar (ej. 'Buscar con Google', 'Conectar', 'Buscar', 'Enviar').") String clickTargetText,
        @ToolParam(name = "type_text", description = "Texto a escribir en el campo activo o enfocado (opcional).") String typeText,
        @ToolParam(name = "press_key_after", description = "Tecla física a presionar después de escribir (ej. 'Enter', 'Tab', o vacío).") String pressKeyAfter,
        @ToolParam(name = "scroll_direction", description = "Dirección de scroll si se requiere: 'down', 'up' o 'none'.") String scrollDirection
    ) {
        try {
            Page page = session.getOrCreatePage(targetDomain);

            EmergencyFailsafeGuard failsafe = new EmergencyFailsafeGuard(VIEWPORT_WIDTH, VIEWPORT_HEIGHT);
            CoordinateInputActuator actuator = new CoordinateInputActuator(page, failsafe);
            VisionGroundingService vision = new MacNativeOcrVisionService();

            // 1. Ejecutar scroll si se solicitó
            if ("down".equalsIgnoreCase(scrollDirection)) {
                actuator.scrollSmooth(DEFAULT_SCROLL_DELTA_Y);
                sleep(DEFAULT_STEP_WAIT_MS);
            } else if ("up".equalsIgnoreCase(scrollDirection)) {
                actuator.scrollSmooth(-DEFAULT_SCROLL_DELTA_Y);
                sleep(DEFAULT_STEP_WAIT_MS);
            }

            // 2. Si se especificó un elemento para hacer clic, localizarlo visualmente y pulsar
            if (clickTargetText != null && !clickTargetText.isBlank()) {
                byte[] screen = page.screenshot();
                VisualElementTarget target = vision.findVisualElement(screen, clickTargetText);
                actuator.moveAndClick(target.targetCoordinate());
                sleep(DEFAULT_STEP_WAIT_MS);
            }

            // 3. Si se especificó texto para escribir, teclearlo con cinemática humana y posibles typos
            if (typeText != null && !typeText.isEmpty()) {
                actuator.typeHumanText(typeText);
                sleep(400L);
            }

            // 4. Presionar tecla de confirmación si fue indicada (ej. Enter)
            if (pressKeyAfter != null && !pressKeyAfter.isBlank()) {
                actuator.pressKey(pressKeyAfter);
                sleep(DEFAULT_STEP_WAIT_MS);
            }

            ObjectNode result = MAPPER.createObjectNode();
            result.put("status", "success");
            result.put("target_domain", targetDomain != null ? targetDomain : "active_tab");
            result.put("clicked", clickTargetText);
            result.put("typed", typeText);
            return MAPPER.writeValueAsString(result);

        } catch (Exception e) {
            throw new BorisException("Fallo en la acción visual del navegador: " + e.getMessage(), e);
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
