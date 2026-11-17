package com.boris.ui.automation;

import java.nio.file.Path;

import com.boris.exceptions.BrowserLaunchFailedException;
import com.boris.exceptions.UiActionExecutionException;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import com.microsoft.playwright.PlaywrightException;

/**
 * Gestiona una sesión de navegador abierta por Playwright con una ventana visible
 * y un perfil persistente, reutilizando la misma página entre invocaciones.
 */
public class UiBrowserSessionManager implements AutoCloseable {

    public static final int DEFAULT_VIEWPORT_WIDTH = 1440;
    public static final int DEFAULT_VIEWPORT_HEIGHT = 900;
    public static final Path DEFAULT_PROFILE_DIR =
        Path.of(System.getProperty("user.home"), ".boris", "playwright-profile");

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
     * Devuelve la página activa, lanzando Chromium en la primera invocación.
     * Si se especifica un dominio o URL objetivo distinto al de la página actual,
     * navega hacia ese destino.
     *
     * @param targetUrlOrDomain dominio o URL objetivo; vacío o nulo reutiliza la página actual
     * @return página activa de Playwright
     */
    public synchronized Page getOrCreatePage(String targetUrlOrDomain) {
        if (context == null) {
            launch();
        }

        if (targetUrlOrDomain != null && !targetUrlOrDomain.isBlank()
                && !activePage.url().toLowerCase().contains(targetUrlOrDomain.toLowerCase())) {
            activePage.navigate(toDestinationUrl(targetUrlOrDomain));
        }

        activePage.bringToFront();
        return activePage;
    }

    private void launch() {
        this.playwright = Playwright.create();

        BrowserType.LaunchPersistentContextOptions options =
            new BrowserType.LaunchPersistentContextOptions()
                .setHeadless(false)
                .setViewportSize(viewportWidth, viewportHeight);

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
}
