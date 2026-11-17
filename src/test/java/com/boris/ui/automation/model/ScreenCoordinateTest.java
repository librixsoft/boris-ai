package com.boris.ui.automation.model;

import com.boris.exceptions.CoordinateOutOfBoundsException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ScreenCoordinateTest {

    @Test
    void validCoordinatesAreAccepted() {
        ScreenCoordinate coord = new ScreenCoordinate(100, 200);
        assertEquals(100, coord.x());
        assertEquals(200, coord.y());
    }

    @Test
    void zeroCoordinatesAreAccepted() {
        ScreenCoordinate coord = new ScreenCoordinate(0, 0);
        assertEquals(0, coord.x());
        assertEquals(0, coord.y());
    }

    @Test
    void negativeXThrowsException() {
        assertThrows(CoordinateOutOfBoundsException.class, () -> new ScreenCoordinate(-1, 100));
    }

    @Test
    void negativeYThrowsException() {
        assertThrows(CoordinateOutOfBoundsException.class, () -> new ScreenCoordinate(100, -1));
    }

    @Test
    void negativeBothThrowsException() {
        assertThrows(CoordinateOutOfBoundsException.class, () -> new ScreenCoordinate(-5, -5));
    }
}
