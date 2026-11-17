package com.boris.ui.automation;

import com.boris.ui.automation.model.VisualElementTarget;

/**
 * Servicio de grounding visual: resuelve la posición en píxeles de un elemento
 * visual (texto, botón, enlace) a partir de una captura de pantalla.
 */
public interface VisionGroundingService {

    /**
     * Busca un elemento visual cuyo texto coincida con targetText en el screenshot
     * proporcionado y devuelve su coordenada objetivo.
     *
     * @param screenshotPngBytes bytes PNG del screenshot del viewport
     * @param targetText         texto visual a buscar (ej. "Buscar", "Conectar")
     * @return VisualElementTarget con bounding box, coordenada y confianza
     * @throws VisualElementNotFoundException si el elemento no se encuentra
     */
    VisualElementTarget findVisualElement(byte[] screenshotPngBytes, String targetText);
}
