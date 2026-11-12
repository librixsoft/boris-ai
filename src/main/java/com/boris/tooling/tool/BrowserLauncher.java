package com.boris.tooling.tool;

import com.microsoft.playwright.*;
import com.boris.exceptions.ChromeNotFoundException;

/**
 * Lanza Google Chrome del sistema en modo headless.
 * Se usa tanto en WebSearchTool como en BatchWebFetchTool para evitar
 * la detección anti-bot de Bing.
 */
public final class BrowserLauncher {

    private BrowserLauncher() {
    }

    /**
     * Lanza Google Chrome del sistema en modo headless.
     *
     * @param pw instancia de Playwright
     * @param headless true para headless, false para visible
     * @return Browser configurado con channel chrome y --no-sandbox
     * @throws ChromeNotFoundException si Google Chrome no está instalado
     */
    public static Browser launchChrome(Playwright pw, boolean headless) {
        try {
            return pw.chromium().launch(new BrowserType.LaunchOptions()
                .setChannel("chrome")
                .setHeadless(headless)
                .setArgs(java.util.List.of("--no-sandbox")));
        } catch (Exception e) {
            throw new ChromeNotFoundException(
                "Google Chrome no encontrado en el sistema. Instale Google Chrome para usar web_search y web_fetch.", e);
        }
    }
}
