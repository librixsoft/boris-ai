package com.boris.cli.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.boris.context.SearchSessionContext;
import com.boris.exceptions.BorisException;

import static org.junit.jupiter.api.Assertions.*;

class SessionIdBarTest {

    @AfterEach
    void limpiar() {
        SearchSessionContext.clear();
    }

    @Test
    void muestraSessionIdSeteado() {
        SearchSessionContext.setSessionId("a1b2c3d4e5f6a7b8");
        SessionIdBar bar = new SessionIdBar();
        assertEquals("sesión: a1b2c3d4e5f6a7b8", bar.getText());
    }

    @Test
    void lanzaExcepcionCuandoNoHaySesion() {
        SearchSessionContext.clear();
        assertThrows(BorisException.class, SessionIdBar::new);
    }
}
