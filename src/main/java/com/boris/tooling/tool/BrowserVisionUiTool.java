package com.boris.tooling.tool;

import com.boris.exceptions.BrowserActionTimeoutException;
import com.boris.exceptions.InvalidToolArgumentException;
import com.boris.exceptions.VisualElementNotFoundException;
import com.boris.tooling.annotation.Tool;
import com.boris.tooling.annotation.ToolParam;
import com.boris.ui.automation.BrowserActionLogger;
import com.boris.ui.automation.CoordinateInputActuator;
import com.boris.ui.automation.EmergencyFailsafeGuard;
import com.boris.ui.automation.MacNativeOcrVisionService;
import com.boris.ui.automation.UiBrowserSessionManager;
import com.boris.ui.automation.VisionGroundingService;
import com.boris.ui.automation.model.BoundingBox;
import com.boris.ui.automation.model.VisualElementTarget;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.microsoft.playwright.Page;

import java.security.MessageDigest;
import java.util.List;
import java.util.Map;

/**
 * Herramienta universal para automatización de navegador a nivel visual (Zero-DOM).
 * Implementa un contrato explícito de acción: open, read_screen, click_text,
 * focus_and_type, press_key, scroll, screenshot. Cada acción devuelve evidencia
 * observable (screen_hash antes/después, URL, changed) y falla explícitamente
 * cuando el elemento no se encuentra o la acción no produce cambio.
 */
public class BrowserVisionUiTool {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final int STABLE_SCREEN_TIMEOUT_MS = 5000;
    private static final int STABLE_SCREEN_POLL_MS = 500;
    private static final int MAX_STABLE_SCREEN_ATTEMPTS = 10;

    private static final Map<String, String> PLAYWRIGHT_KEY_MAP = Map.ofEntries(
        Map.entry("enter", "Enter"),
        Map.entry("tab", "Tab"),
        Map.entry("escape", "Escape"),
        Map.entry("esc", "Escape"),
        Map.entry("arrowdown", "ArrowDown"),
        Map.entry("arrowup", "ArrowUp"),
        Map.entry("arrowleft", "ArrowLeft"),
        Map.entry("arrowright", "ArrowRight"),
        Map.entry("backspace", "Backspace"),
        Map.entry("delete", "Delete"),
        Map.entry("home", "Home"),
        Map.entry("end", "End"),
        Map.entry("pageup", "PageUp"),
        Map.entry("pagedown", "PageDown"),
        Map.entry("space", " ")
    );

    private final UiBrowserSessionManager session;
    private final BrowserActionLogger logger;
    private final VisionGroundingService visionOverride;

    public BrowserVisionUiTool() {
        this(SharedSessionHolder.INSTANCE, null);
    }

    BrowserVisionUiTool(UiBrowserSessionManager session) {
        this(session, null);
    }

    /**
     * Seam de inyección para tests: permite sustituir el OCR nativo por un
     * VisionGroundingService simulado. En producción se usa el constructor público.
     */
    BrowserVisionUiTool(UiBrowserSessionManager session, VisionGroundingService visionOverride) {
        if (session == null) {
            throw new IllegalArgumentException("session cannot be null");
        }
        this.session = session;
        this.visionOverride = visionOverride;
        this.logger = new BrowserActionLogger("browser-session-" + System.currentTimeMillis());
    }

    private VisionGroundingService vision() {
        return visionOverride != null
            ? visionOverride
            : new MacNativeOcrVisionService(session.getViewportWidth(), session.getViewportHeight());
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
        description = "Abre automáticamente una ventana visible de Chromium gestionada por Playwright y automatiza tareas visualmente (Google, LinkedIn, etc.) simulando mouse y teclado humano sin tocar el DOM. No requiere Google Chrome abierto, ni depuración remota, ni comandos en terminal. Ejecuta siempre esta herramienta directamente cuando te pidan interactuar o buscar en el navegador.")
    public String executeBrowserAction(
        @ToolParam(name = "action", description = "Acción a ejecutar: open (navegar al dominio), read_screen (leer texto visible), click_text (hacer clic sobre texto visible), focus_and_type (enfocar campo y escribir), press_key (presionar tecla), scroll (desplazar pantalla), screenshot (capturar pantalla).", required = false) String action,
        @ToolParam(name = "target_domain", description = "Dominio o URL de la pestaña objetivo (ej. 'google.com', 'linkedin.com'). Dejar vacío para usar la pestaña activa actual.", required = false) String targetDomain,
        @ToolParam(name = "click_target_text", description = "Texto visual del elemento o botón a pulsar (ej. 'Buscar con Google', 'Conectar', 'Buscar', 'Enviar').", required = false) String clickTargetText,
        @ToolParam(name = "focus_target_text", description = "Texto visual del campo a enfocar antes de escribir (ej. 'Buscar', 'Correo electrónico').", required = false) String focusTargetText,
        @ToolParam(name = "type_text", description = "Texto a escribir en el campo activo o enfocado (opcional).", required = false) String typeText,
        @ToolParam(name = "press_key_after", description = "Tecla física a presionar después de escribir (ej. 'Enter', 'Tab', 'Escape').", required = false) String pressKeyAfter,
        @ToolParam(name = "scroll_direction", description = "Dirección de scroll: 'none', 'down' o 'up'.", required = false) String scrollDirection
    ) {
        String normalizedAction = normalizeAction(action);
        validateScrollDirection(scrollDirection);
        String normalizedKey = normalizePressKey(pressKeyAfter);

        boolean isOpen = "open".equals(normalizedAction);
        boolean hasTargetDomain = targetDomain != null && !targetDomain.isBlank();
        if (!isOpen && hasTargetDomain) {
            session.ensureDomainExists(targetDomain);
        }
        Page page = session.getOrCreatePage(null);
        if (isOpen && hasTargetDomain) {
            session.navigateTo(targetDomain);
        }
        String urlBefore = page.url();

        EmergencyFailsafeGuard failsafe = new EmergencyFailsafeGuard(
            session.getViewportWidth(), session.getViewportHeight());
        CoordinateInputActuator actuator = new CoordinateInputActuator(page, failsafe);
        VisionGroundingService vision = vision();

        // Captura antes de actuar: screen_hash_before
        byte[] screenBefore = page.screenshot();
        String screenHashBefore = sha256(screenBefore);

        String elementLabel = null;
        BoundingBox elementBbox = null;
        double elementConfidence = 0.0;
        String typed = null;
        String key = null;
        boolean changed = false;
        String status = "success";
        String cause = null;
        List<VisualElementTarget> visibleTargets = null;

        try {
            switch (normalizedAction) {
                case "open" -> {
                    // La navegación explícita ya se resolvió antes de la captura inicial
                }
                case "read_screen" -> {
                    visibleTargets = vision.findAllText(screenBefore);
                }
                case "click_text" -> {
                    if (clickTargetText == null || clickTargetText.isBlank()) {
                        throw new InvalidToolArgumentException(
                            "click_target_text es requerido para la acción click_text");
                    }
                    VisualElementTarget target = vision.findVisualElement(screenBefore, clickTargetText);
                    elementLabel = target.label();
                    elementBbox = target.boundingBox();
                    elementConfidence = target.confidence();
                    actuator.moveAndClick(target.targetCoordinate());
                }
                case "focus_and_type" -> {
                    if (focusTargetText == null || focusTargetText.isBlank()) {
                        throw new InvalidToolArgumentException(
                            "focus_target_text es requerido para la acción focus_and_type");
                    }
                    if (typeText == null || typeText.isBlank()) {
                        throw new InvalidToolArgumentException(
                            "type_text es requerido para la acción focus_and_type");
                    }
                    VisualElementTarget target = vision.findVisualElement(screenBefore, focusTargetText);
                    elementLabel = target.label();
                    elementBbox = target.boundingBox();
                    elementConfidence = target.confidence();
                    actuator.moveAndClick(target.targetCoordinate());
                    // Pausa breve para que el foco se establezca
                    sleep(100);
                    actuator.typeHumanText(typeText);
                    typed = typeText;
                }
                case "press_key" -> {
                    if (normalizedKey == null || normalizedKey.isBlank()) {
                        throw new InvalidToolArgumentException(
                            "press_key_after es requerido para la acción press_key");
                    }
                    actuator.pressKey(normalizedKey);
                    key = normalizedKey;
                }
                case "scroll" -> {
                    if ("down".equalsIgnoreCase(scrollDirection)) {
                        actuator.scrollSmooth(400);
                    } else if ("up".equalsIgnoreCase(scrollDirection)) {
                        actuator.scrollSmooth(-400);
                    }
                    // none no hace nada
                }
                case "screenshot" -> {
                    // Solo captura: no hay acción que modifique
                }
                default -> throw new InvalidToolArgumentException(
                    "Acción desconocida: '" + action + "'. Valores permitidos: open, read_screen, click_text, focus_and_type, press_key, scroll, screenshot");
            }

            // Esperar pantalla estable tras la acción
            byte[] screenAfter;
            try {
                screenAfter = waitForStableScreen(page, screenHashBefore);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new BrowserActionTimeoutException(
                    "La espera de pantalla estable fue interrumpida", e);
            }
            String screenHashAfter = sha256(screenAfter);
            changed = !screenHashBefore.equals(screenHashAfter);
            String urlAfter = page.url();

            // Si la acción esperaba cambio y no hubo, marcar como failed.
            // scroll con dirección none es observación pasiva: no requiere cambio.
            boolean expectsChange = switch (normalizedAction) {
                case "click_text", "focus_and_type", "press_key" -> true;
                case "scroll" -> !"none".equalsIgnoreCase(scrollDirection);
                default -> false;
            };
            if (!changed && expectsChange) {
                status = "failed";
                cause = "La acción '" + normalizedAction + "' no produjo cambio visible en la pantalla";
            }

            // Registrar acción en el logger
            logger.logAction(
                normalizedAction,
                targetDomain,
                clickTargetText,
                typeText,
                normalizedKey,
                scrollDirection,
                urlBefore,
                urlAfter,
                elementLabel,
                elementBbox,
                elementConfidence,
                screenAfter,
                status,
                cause);

            ObjectNode result = MAPPER.createObjectNode();
            result.put("status", status);
            result.put("action", normalizedAction);
            result.put("screen_hash_before", screenHashBefore);
            result.put("screen_hash_after", screenHashAfter);
            result.put("url_before", urlBefore);
            result.put("url_after", urlAfter);
            result.put("changed", changed);
            if (elementLabel != null) {
                ObjectNode element = MAPPER.createObjectNode();
                element.put("label", elementLabel);
                element.put("bounding_box", MAPPER.createObjectNode()
                    .put("x", elementBbox.x())
                    .put("y", elementBbox.y())
                    .put("width", elementBbox.width())
                    .put("height", elementBbox.height()));
                element.put("confidence", elementConfidence);
                result.set("element", element);
            }
            if (visibleTargets != null) {
                result.set("elements", buildElementsArray(visibleTargets));
            }
            if (typed != null) {
                result.put("typed", typed);
            }
            if (key != null) {
                result.put("key", key);
            }
            if (cause != null) {
                result.put("cause", cause);
            }

            String jsonResult;
            try {
                jsonResult = MAPPER.writeValueAsString(result);
            } catch (Exception e) {
                throw new RuntimeException("Error serializando resultado de acción browser", e);
            }

            return jsonResult;

        } catch (VisualElementNotFoundException e) {
            // Obtener candidatos cercanos para el mensaje de error
            List<VisualElementTarget> allTargets = vision.findAllText(screenBefore);
            StringBuilder candidates = new StringBuilder("Candidatas cercanas: ");
            for (VisualElementTarget t : allTargets) {
                candidates.append(t.label()).append(" (").append(t.boundingBox().x())
                    .append(",").append(t.boundingBox().y()).append("), ");
            }
            String errorMsg = "No se encontró el elemento visual '" +
                (clickTargetText != null ? clickTargetText : focusTargetText != null ? focusTargetText : "desconocido") +
                "' en la pantalla. " + candidates.toString();
            throw new VisualElementNotFoundException(errorMsg, e);
        }
    }

    /**
     * Normaliza la acción a minúsculas. Devuelve "read_screen" si la acción es nula o vacía.
     */
    private String normalizeAction(String action) {
        if (action == null || action.isBlank()) {
            return "read_screen";
        }
        return action.trim().toLowerCase();
    }

    /**
     * Valida que scroll_direction sea uno de: none, up, down.
     * Rechaza el valor legado "0" como fallback.
     */
    private void validateScrollDirection(String scrollDirection) {
        if (scrollDirection == null || scrollDirection.isBlank()) {
            return;
        }
        String lower = scrollDirection.trim().toLowerCase();
        if (!"none".equals(lower) && !"up".equals(lower) && !"down".equals(lower)) {
            throw new InvalidToolArgumentException(
                "scroll_direction debe ser 'none', 'up' o 'down'. Valor recibido: '" + scrollDirection + "'");
        }
    }

    /**
     * Normaliza el nombre de la tecla a la forma canónica de Playwright.
     * Lanza InvalidToolArgumentException para claves desconocidas.
     */
    private String normalizePressKey(String pressKeyAfter) {
        if (pressKeyAfter == null || pressKeyAfter.isBlank()) {
            return null;
        }
        String lower = pressKeyAfter.trim().toLowerCase();
        String canonical = PLAYWRIGHT_KEY_MAP.get(lower);
        if (canonical == null) {
            // Verificar si es una tecla de una sola letra o número válida para Playwright
            if (pressKeyAfter.trim().length() == 1) {
                return pressKeyAfter.trim();
            }
            StringBuilder allowed = new StringBuilder("Clave desconocida: '").append(pressKeyAfter)
                .append("'. Valores permitidos: ");
            for (String k : PLAYWRIGHT_KEY_MAP.keySet()) {
                allowed.append(k).append(", ");
            }
            throw new InvalidToolArgumentException(allowed.toString());
        }
        return canonical;
    }

    /**
     * Espera hasta que la pantalla se estabilice (dos capturas consecutivas idénticas)
     * o se agote el timeout.
     */
    private byte[] waitForStableScreen(Page page, String initialHash) throws InterruptedException {
        int attempts = 0;
        while (attempts < MAX_STABLE_SCREEN_ATTEMPTS) {
            byte[] screen1 = page.screenshot();
            String hash1 = sha256(screen1);
            sleep(STABLE_SCREEN_POLL_MS);
            byte[] screen2 = page.screenshot();
            String hash2 = sha256(screen2);
            if (hash1.equals(hash2)) {
                return screen2;
            }
            attempts++;
        }
        throw new BrowserActionTimeoutException(
            "La pantalla no se estabilizó tras " + MAX_STABLE_SCREEN_ATTEMPTS +
            " intentos de espera (" + STABLE_SCREEN_TIMEOUT_MS + " ms)");
    }

    private String sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException("Error calculando SHA-256", e);
        }
    }

    private ArrayNode buildElementsArray(List<VisualElementTarget> targets) {
        ArrayNode elementsNode = MAPPER.createArrayNode();
        for (VisualElementTarget target : targets) {
            ObjectNode elementNode = elementsNode.addObject();
            elementNode.put("label", target.label());
            elementNode.put("x", target.boundingBox().x());
            elementNode.put("y", target.boundingBox().y());
            elementNode.put("width", target.boundingBox().width());
            elementNode.put("height", target.boundingBox().height());
            elementNode.put("confidence", target.confidence());
        }
        return elementsNode;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
