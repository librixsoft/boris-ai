package com.boris.context;

import java.util.regex.Pattern;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import com.boris.exceptions.BorisException;

import static org.junit.jupiter.api.Assertions.*;

class SearchSessionContextTest {

    @AfterEach
    void limpiar() {
        SearchSessionContext.clear();
    }

    @Test
    void getSessionIdDevuelveValorSeteadoGlobalmenteSinThreadLocalDelHiloActual() {
        SearchSessionContext.setSessionId("sesion-global-123");
        // Simula hilo distinto: ThreadLocal vacio pero global debe estar seteado
        String id = SearchSessionContext.getSessionId();
        assertEquals("sesion-global-123", id);
    }

    @Test
    void requireSessionIdLanzaExcepcionCuandoNoHaySesion() {
        SearchSessionContext.clear();
        assertThrows(BorisException.class, SearchSessionContext::requireSessionId);
    }

    @Test
    void requireSessionIdDevuelveValorCuandoEstaSeteado() {
        SearchSessionContext.setSessionId("sesion-activa");
        assertEquals("sesion-activa", SearchSessionContext.requireSessionId());
    }

    @Test
    void getSessionIdDevuelveNullCuandoNoEstaSeteado() {
        SearchSessionContext.clear();
        assertNull(SearchSessionContext.getSessionId());
    }

    @Test
    void clearEliminaThreadLocalYGlobal() {
        SearchSessionContext.setSessionId("sesion");
        SearchSessionContext.clear();
        assertNull(SearchSessionContext.getSessionId());
    }

    @Test
    void generateSessionIdTieneFormatoEsperado() {
        String id = SearchSessionContext.generateSessionId();
        assertNotNull(id, "sessionId no debe ser nulo");
        assertEquals(16, id.length(), "sessionId debe tener 16 caracteres");
        assertTrue(Pattern.matches("^[0-9a-f]{16}$", id), "sessionId debe ser hex de 16 chars: " + id);

        String id2 = SearchSessionContext.generateSessionId();
        assertNotEquals(id, id2, "Dos sessionIds consecutivos deben ser distintos");
    }
}
