package com.boris.ui.automation;

import com.boris.exceptions.EmergencyFailsafeTriggeredException;
import com.boris.ui.automation.model.ScreenCoordinate;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Mouse;
import com.microsoft.playwright.Keyboard;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CoordinateInputActuatorTest {

    private Page mockPage;
    private Mouse mockMouse;
    private Keyboard mockKeyboard;
    private EmergencyFailsafeGuard failsafe;

    @BeforeEach
    void setUp() {
        mockPage = mock(Page.class);
        mockMouse = mock(Mouse.class);
        mockKeyboard = mock(Keyboard.class);
        when(mockPage.mouse()).thenReturn(mockMouse);
        when(mockPage.keyboard()).thenReturn(mockKeyboard);
        failsafe = new EmergencyFailsafeGuard(1440, 900);
    }

    @Test
    void moveAndClickDispatchesMouseMoveAndClick() {
        CoordinateInputActuator actuator = new CoordinateInputActuator(mockPage, failsafe);
        ScreenCoordinate target = new ScreenCoordinate(500, 300);

        actuator.moveAndClick(target);

        verify(mockMouse, atLeastOnce()).move(500, 300);
        verify(mockMouse, times(1)).down();
        verify(mockMouse, times(1)).up();
    }

    @Test
    void moveAndClickOutsideFailsafeThrowsException() {
        CoordinateInputActuator actuator = new CoordinateInputActuator(mockPage, failsafe);
        ScreenCoordinate corner = new ScreenCoordinate(0, 0);

        assertThrows(EmergencyFailsafeTriggeredException.class, () ->
            actuator.moveAndClick(corner));
    }

    @Test
    void typeHumanTextWritesCharacters() {
        CoordinateInputActuator actuator = new CoordinateInputActuator(mockPage, failsafe);

        actuator.typeHumanText("hello");

        verify(mockKeyboard, atLeast(5)).type(anyString());
    }

    @Test
    void typeHumanTextNullThrowsException() {
        CoordinateInputActuator actuator = new CoordinateInputActuator(mockPage, failsafe);

        assertThrows(IllegalArgumentException.class, () ->
            actuator.typeHumanText(null));
    }

    @Test
    void typeHumanTextEmptyThrowsException() {
        CoordinateInputActuator actuator = new CoordinateInputActuator(mockPage, failsafe);

        assertThrows(IllegalArgumentException.class, () ->
            actuator.typeHumanText(""));
    }

    @Test
    void pressKeyCallsKeyboardPress() {
        CoordinateInputActuator actuator = new CoordinateInputActuator(mockPage, failsafe);

        actuator.pressKey("Enter");

        verify(mockKeyboard, times(1)).press("Enter");
    }

    @Test
    void scrollSmoothCallsMouseWheel() {
        CoordinateInputActuator actuator = new CoordinateInputActuator(mockPage, failsafe);

        actuator.scrollSmooth(400);

        verify(mockMouse, atLeastOnce()).wheel(eq(0.0), anyDouble());
    }
}
