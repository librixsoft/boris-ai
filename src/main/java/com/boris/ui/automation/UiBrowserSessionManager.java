package com.boris.ui.automation;

import java.nio.file.Path;
import java.util.List;

import com.boris.exceptions.BrowserActionTimeoutException;
import com.boris.exceptions.BrowserLaunchFailedException;
import com.boris.exceptions.BrowserTabNotFoundException;
import com.boris.exceptions.UiActionExecutionException;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;
import com.microsoft.playwright.options.LoadState;

/**
 * Gestiona una sesión de navegador abierta por Playwright con una ventana visible
 * y un perfil persistente. Proporciona separación clara entre asegurar la página
 * activa (sin navegar) y navegar a un destino explícito.
 */
public class UiBrowserSessionManager implements AutoCloseable {

    public static final int DEFAULT_VIEWPORT_WIDTH = 1440;
    public static final int DEFAULT_VIEWPORT_HEIGHT = 900;
    public static final Path DEFAULT_PROFILE_DIR =
        Path.of(System.getProperty("user.home"), ".boris", "playwright-profile");
    public static final int DEFAULT_LOAD_TIMEOUT_MS = 30000;

    private final Path profileDir;
    private final int viewportWidth;
    private final int viewportHeight;

    private Playwright playwright;
    private BrowserContext context;
    private Page activePage;

    public UiBrowserSessionManager() {
        this(DEFAULT_PROFILE_DIR, DEFAULT_VIEWPORT_WIDTH, DEFAULT_VIEWPORT_HEIGHT);
    }

    public UiBrowserSessionManager(Path profileDir, int viewportWidth, int viewportHeight) {
        if (profileDir == null) {
            throw new IllegalArgumentException("profileDir cannot be null");
        }
        if (viewportWidth <= 0) {
            throw new IllegalArgumentException("viewportWidth must be greater than zero");
        }
        if (viewportHeight <= 0) {
            throw new IllegalArgumentException("viewportHeight must be greater than zero");
        }
        this.profileDir = profileDir;
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
    }

    /**
     * Devuelve la página activa, lanzando Chromium en la primera invocación o
     * recreándola si la ventana fue cerrada. No navega a ningún destino.
     *
     * @return página activa de Playwright
     */
    public synchronized Page ensureActivePage() {
        if (context == null) {
            launch();
        }

        if (activePage == null || !activePage.isClosed()) {
            // La página existe y no está cerrada
            return activePage;
        }

        // La página fue cerrada: crear una nueva en el mismo contexto
        activePage = context.newPage();
        return activePage;
    }

    /**
     * Navega a un destino explícito, esperando a que la página termine de cargar.
     * Solo debe invocarse desde la acción open o cuando la página está en about:blank
     * en la primera apertura.
     *
     * @param target dominio o URL objetivo
     * @throws BrowserActionTimeoutException si la carga excede el timeout
     */
    public synchronized void navigateTo(String target) {
        Page page = ensureActivePage();
        String destinationUrl = toDestinationUrl(target);
        page.navigate(destinationUrl);
        waitForLoadState(page);
    }

    /**
     * Devuelve la página activa, lanzando Chromium en la primera invocación.
     * No navega: si se indica un dominio objetivo, verifica que exista una
     * pestaña abierta con ese dominio; si no existe lanza
     * BrowserTabNotFoundException. La navegación es responsabilidad de
     * navigateTo, invocada sólo desde la acción open.
     *
     * @param targetUrlOrDomain dominio o URL objetivo; vacío o nulo reutiliza la página actual
     * @return página activa de Playwright
     */
    public synchronized Page getOrCreatePage(String targetUrlOrDomain) {
        Page page = ensureActivePage();

        if (targetUrlOrDomain != null && !targetUrlOrDomain.isBlank()) {
            ensureDomainExists(targetUrlOrDomain);
        }

        page.bringToFront();
        return page;
    }

    /**
     * Espera a que la página termine de cargar hasta DOMContentLoaded con timeout.
     */
    private void waitForLoadState(Page page) {
        try {
            page.waitForLoadState(LoadState.DOMCONTENTLOADED,
                new com.microsoft.playwright.Page.WaitForLoadStateOptions()
                    .setTimeout(DEFAULT_LOAD_TIMEOUT_MS));
        } catch (PlaywrightException e) {
            throw new BrowserActionTimeoutException(
                "La página no terminó de cargar dentro del timeout de " +
                DEFAULT_LOAD_TIMEOUT_MS + " ms", e);
        }
    }

    /**
     * Devuelve todas las páginas abiertas en el contexto.
     */
    public synchronized List<Page> getAllPages() {
        if (context == null) {
            return List.of();
        }
        return context.pages();
    }

    /**
     * Verifica si el dominio dado existe entre las páginas abiertas.
     * Lanza BrowserTabNotFoundException si no existe y no se ha pedido navegar.
     */
    public synchronized void ensureDomainExists(String targetDomain) {
        if (targetDomain == null || targetDomain.isBlank()) {
            return;
        }
        List<Page> pages = getAllPages();
        String targetLower = targetDomain.toLowerCase();
        for (Page page : pages) {
            if (!page.isClosed() && page.url().toLowerCase().contains(targetLower)) {
                return;
            }
        }
        throw new BrowserTabNotFoundException(
            "No hay ninguna pestaña abierta con el dominio '" + targetDomain +
            "'. Use la acción 'open' para navegar a ese dominio primero.");
    }

    private void launch() {
        this.playwright = Playwright.create();

        BrowserType.LaunchPersistentContextOptions options =
            new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(false)
                .setViewportSize(viewportWidth, viewportHeight)
                .setDeviceScaleFactor(1);

        try {
            this.context = playwright.chromium().launchPersistentContext(profileDir, options);
            this.activePage = context.pages().stream().findFirst().orElseGet(context::newPage);
        } catch (PlaywrightException e) {
            playwright.close();
            this.playwright = null;
            throw new BrowserLaunchFailedException(
                "No se pudo abrir la ventana de Chromium de Playwright con el perfil " + profileDir
                + ". Verifique que no haya otra ventana usando ese perfil.", e);
        }
    }

    private static String toDestinationUrl(String target) {
        String trimmed = target.trim();
        return trimmed.startsWith("http://") || trimmed.startsWith("https://")
            ? trimmed
            : "https://" + trimmed;
    }

    @Override
    public synchronized void close() {
        try {
            if (context != null) {
                context.close();
                context = null;
                activePage = null;
            }
            if (playwright != null) {
                playwright.close();
                playwright = null;
            }
        } catch (PlaywrightException e) {
            throw new UiActionExecutionException("Fallo al cerrar la sesión de Playwright", e);
        }
    }

    /**
     * Devuelve el ancho del viewport configurado.
     */
    public int getViewportWidth() {
        return viewportWidth;
    }

    /**
     * Devuelve el alto del viewport configurado.
     */
    public int getViewportHeight() {
        return viewportHeight;
    }
}
