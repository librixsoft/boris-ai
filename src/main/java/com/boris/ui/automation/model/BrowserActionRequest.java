package com.boris.ui.automation.model;

/**
 * Solicitud de acción de automatización de navegador encapsulando dominio objetivo,
 * texto de clic, texto a escribir, tecla de confirmación y dirección de scroll.
 */
public record BrowserActionRequest(
    String targetDomain,
    String clickTargetText,
    String typeText,
    String pressKeyAfter,
    String scrollDirection
) {}
