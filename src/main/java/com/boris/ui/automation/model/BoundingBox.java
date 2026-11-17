package com.boris.ui.automation.model;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Representa la caja delimitadora de un elemento visual detectado.
 */
public record BoundingBox(int x, int y, int width, int height) {
    public BoundingBox {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Dimensiones del BoundingBox deben ser mayores a cero");
        }
    }

    /**
     * Calcula el centro del elemento aplicando un desplazamiento aleatorio (jitter)
     * para emular la imprecisión del clic humano.
     */
    public ScreenCoordinate centerWithJitter(int jitterRadius) {
        int jitterX = ThreadLocalRandom.current().nextInt(-jitterRadius, jitterRadius + 1);
        int jitterY = ThreadLocalRandom.current().nextInt(-jitterRadius, jitterRadius + 1);
        return new ScreenCoordinate(x + (width / 2) + jitterX, y + (height / 2) + jitterY);
    }
}
