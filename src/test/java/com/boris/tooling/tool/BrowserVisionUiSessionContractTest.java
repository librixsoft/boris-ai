package com.boris.tooling.tool;

import com.boris.exceptions.BrowserTabNotFoundException;
import com.boris.ui.automation.UiBrowserSessionManager;
import com.boris.ui.automation.VisionGroundingService;
import com.microsoft.playwright.Keyboard;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Sesión y navegación deterministas: sólo la acción open navega; el resto de
 * acciones actúan sobre la pestaña activa y fallan si el dominio pedido no
 * está abierto.
 */
class BrowserVisionUiSessionContractTest {

    private UiBrowserSessionManager session;
    private VisionGroundingService vision;
    private BrowserVisionUiTool tool;

    @BeforeEach
    void setUp() {
        session = mock(UiBrowserSessionManager.class);
        when(session.getViewportWidth()).thenReturn(1440);
        when(session.getViewportHeight()).thenReturn(900);
        Page page = mock(Page.class);
        when(session.getOrCreatePage(isNull())).thenReturn(page);
        Mouse mouse = mock(Mouse.class);
        when(page.mouse()).thenReturn(mouse);
        Keyboard keyboard = mock(Keyboard.class);
        when(page.keyboard()).thenReturn(keyboard);
        when(page.url()).thenReturn("https://www.linkedin.com/feed/");
        when(page.screenshot()).thenReturn(new byte[]{1, 2, 3});
        vision = mock(VisionGroundingService.class);
        tool = new BrowserVisionUiTool(session, vision);
    }

    @Test
    void nonOpenActionWithTargetDomainDoesNotNavigate() {
        tool.executeBrowserAction("read_screen", "linkedin.com", null, null, null, null, null);

        verify(session).ensureDomainExists("linkedin.com");
        verify(session, never()).navigateTo(anyString());
    }

    @Test
    void secondActionOnSameDomainDoesNotNavigateAgain() {
        tool.executeBrowserAction("read_screen", "linkedin.com", null, null, null, null, null);
        tool.executeBrowserAction("read_screen", "linkedin.com", null, null, null, null, null);

        verify(session, times(2)).ensureDomainExists("linkedin.com");
        verify(session, never()).navigateTo(anyString());
    }

    @Test
    void openNavigatesExactlyOnceAndWaitsForLoad() {
        tool.executeBrowserAction("open", "linkedin.com", null, null, null, null, null);

        verify(session, times(1)).navigateTo("linkedin.com");
        verify(session, never()).ensureDomainExists(anyString());
    }

    @Test
    void nonOpenActionWithDomainNotOpenThrowsBrowserTabNotFound() {
        org.mockito.Mockito.doThrow(new BrowserTabNotFoundException(
                "No hay ninguna pestaña abierta con el dominio 'google.com'. Use la acción 'open' para navegar a ese dominio primero."))
                .when(session).ensureDomainExists("google.com");

        assertThrows(BrowserTabNotFoundException.class, () ->
                tool.executeBrowserAction("click_text", "google.com", "Buscar", null, null, null, null));
    }
}
