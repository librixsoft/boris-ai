package com.boris.ui.automation;

import com.boris.exceptions.VisualElementNotFoundException;
import com.boris.ui.automation.model.BoundingBox;
import com.boris.ui.automation.model.VisualElementTarget;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class MacNativeOcrVisionServiceTest {

    @Test
    void findVisualElementReturnsTargetWithSimulatedBoundingBox() {
        VisionGroundingService vision = new MacNativeOcrVisionService();
        byte[] screenshot = new byte[]{1, 2, 3, 4};

        VisualElementTarget target = vision.findVisualElement(screenshot, "Buscar");

        assertEquals("Buscar", target.label());
        assertEquals(0.98, target.confidence(), 0.001);
        assertEquals(240, target.boundingBox().x());
        assertEquals(80, target.boundingBox().y());
        assertEquals(140, target.boundingBox().width());
        assertEquals(36, target.boundingBox().height());
    }

    @Test
    void findVisualElementNullScreenshotThrowsException() {
        VisionGroundingService vision = new MacNativeOcrVisionService();

        assertThrows(IllegalArgumentException.class, () ->
            vision.findVisualElement(null, "Buscar"));
    }

    @Test
    void findVisualElementEmptyScreenshotThrowsException() {
        VisionGroundingService vision = new MacNativeOcrVisionService();

        assertThrows(IllegalArgumentException.class, () ->
            vision.findVisualElement(new byte[0], "Buscar"));
    }

    @Test
    void findVisualElementNullTargetTextThrowsException() {
        VisionGroundingService vision = new MacNativeOcrVisionService();
        byte[] screenshot = new byte[]{1, 2, 3};

        assertThrows(IllegalArgumentException.class, () ->
            vision.findVisualElement(screenshot, null));
    }

    @Test
    void findVisualElementBlankTargetTextThrowsException() {
        VisionGroundingService vision = new MacNativeOcrVisionService();
        byte[] screenshot = new byte[]{1, 2, 3};

        assertThrows(IllegalArgumentException.class, () ->
            vision.findVisualElement(screenshot, "   "));
    }
}
