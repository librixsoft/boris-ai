package com.boris.ui.automation.model;

/**
 * Contrato de un elemento visual identificado en la imagen del viewport.
 */
public record VisualElementTarget(
    String label,
    BoundingBox boundingBox,
    ScreenCoordinate targetCoordinate,
    double confidence
) {}
