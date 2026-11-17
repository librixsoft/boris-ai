package com.boris.ui.automation;

import com.boris.exceptions.EmergencyFailsafeTriggeredException;
import com.boris.ui.automation.model.ScreenCoordinate;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmergencyFailsafeGuardTest {

    @Test
    void centerCoordinatesAreSafe() {
        EmergencyFailsafeGuard guard = new EmergencyFailsafeGuard(1440, 900);
        // Coordenada central, lejos de las esquinas
        guard.assertSafe(new ScreenCoordinate(720, 450));
    }

    @Test
    void topLeftCornerThrowsException() {
        EmergencyFailsafeGuard guard = new EmergencyFailsafeGuard(1440, 900);
        assertThrows(EmergencyFailsafeTriggeredException.class, () ->
            guard.assertSafe(new ScreenCoordinate(0, 0)));
    }

    @Test
    void topRightCornerThrowsException() {
        EmergencyFailsafeGuard guard = new EmergencyFailsafeGuard(1440, 900);
        assertThrows(EmergencyFailsafeTriggeredException.class, () ->
            guard.assertSafe(new ScreenCoordinate(1439, 0)));
    }

    @Test
    void bottomLeftCornerThrowsException() {
        EmergencyFailsafeGuard guard = new EmergencyFailsafeGuard(1440, 900);
        assertThrows(EmergencyFailsafeTriggeredException.class, () ->
            guard.assertSafe(new ScreenCoordinate(0, 899)));
    }

    @Test
    void bottomRightCornerThrowsException() {
        EmergencyFailsafeGuard guard = new EmergencyFailsafeGuard(1440, 900);
        assertThrows(EmergencyFailsafeTriggeredException.class, () ->
            guard.assertSafe(new ScreenCoordinate(1439, 899)));
    }

    @Test
    void nearCornerButOutsideThresholdIsSafe() {
        EmergencyFailsafeGuard guard = new EmergencyFailsafeGuard(1440, 900);
        // cornerSize = 5, así que (6, 6) está fuera del umbral
        guard.assertSafe(new ScreenCoordinate(6, 6));
    }

    @Test
    void edgeButNotCornerIsSafe() {
        EmergencyFailsafeGuard guard = new EmergencyFailsafeGuard(1440, 900);
        // En el borde superior pero no en la esquina
        guard.assertSafe(new ScreenCoordinate(720, 0));
    }
}
