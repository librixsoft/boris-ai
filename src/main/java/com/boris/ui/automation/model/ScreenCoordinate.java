package com.boris.ui.automation.model;

import com.boris.exceptions.CoordinateOutOfBoundsException;

/**
 * Representa una coordenada bidimensional inmutable en el viewport.
 */
public record ScreenCoordinate(int x, int y) {
    public ScreenCoordinate {
        if (x < 0 || y < 0) {
            throw new CoordinateOutOfBoundsException("Coordenadas no pueden ser negativas: (" + x + ", " + y + ")");
        }
    }
}
