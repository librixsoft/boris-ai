package com.boris.ui.automation.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BoundingBoxTest {

    @Test
    void validBoundingBoxIsAccepted() {
        BoundingBox bbox = new BoundingBox(10, 20, 100, 50);
        assertEquals(10, bbox.x());
        assertEquals(20, bbox.y());
        assertEquals(100, bbox.width());
        assertEquals(50, bbox.height());
    }

    @Test
    void zeroWidthThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new BoundingBox(10, 20, 0, 50));
    }

    @Test
    void zeroHeightThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new BoundingBox(10, 20, 100, 0));
    }

    @Test
    void negativeWidthThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new BoundingBox(10, 20, -10, 50));
    }

    @Test
    void negativeHeightThrowsException() {
        assertThrows(IllegalArgumentException.class, () -> new BoundingBox(10, 20, 100, -5));
    }

    @Test
    void centerWithJitterReturnsCoordinateWithinBounds() {
        BoundingBox bbox = new BoundingBox(100, 100, 200, 100);
        ScreenCoordinate center = bbox.centerWithJitter(10);
        // Centro es (200, 150), jitter ±10
        assertTrue(center.x() >= 190 && center.x() <= 210);
        assertTrue(center.y() >= 140 && center.y() <= 160);
    }
}
