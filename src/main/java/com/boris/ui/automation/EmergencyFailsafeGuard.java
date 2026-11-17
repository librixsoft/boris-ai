package com.boris.ui.automation;

import com.boris.exceptions.EmergencyFailsafeTriggeredException;
import com.boris.ui.automation.model.ScreenCoordinate;

public class EmergencyFailsafeGuard {

    public static final int DEFAULT_CORNER_SIZE_PIXELS = 5;

    private final int viewportWidth;
    private final int viewportHeight;
    private final int cornerSize;

    public EmergencyFailsafeGuard(int viewportWidth, int viewportHeight) {
        this(viewportWidth, viewportHeight, DEFAULT_CORNER_SIZE_PIXELS);
    }

    public EmergencyFailsafeGuard(int viewportWidth, int viewportHeight, int cornerSize) {
        this.viewportWidth = viewportWidth;
        this.viewportHeight = viewportHeight;
        this.cornerSize = cornerSize;
    }

    public void assertSafe(ScreenCoordinate coord) {
        boolean inTopLeft = coord.x() <= cornerSize && coord.y() <= cornerSize;
        boolean inTopRight = coord.x() >= (viewportWidth - cornerSize) && coord.y() <= cornerSize;
        boolean inBottomLeft = coord.x() <= cornerSize && coord.y() >= (viewportHeight - cornerSize);
        boolean inBottomRight = coord.x() >= (viewportWidth - cornerSize) && coord.y() >= (viewportHeight - cornerSize);

        if (inTopLeft || inTopRight || inBottomLeft || inBottomRight) {
            throw new EmergencyFailsafeTriggeredException(
                "Paro de emergencia activado por el usuario en la coordenada (" + coord.x() + ", " + coord.y() + ")");
        }
    }
}
