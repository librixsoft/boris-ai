package com.boris.tooling.engine;

import java.lang.reflect.Type;

/**
 * Metadatos de un parámetro individual de una herramienta.
 *
 * <p><b>Referencia histórica / Equivalencia:</b><br>
 * Basado en el modelo de parámetros de herramientas de <b>Spring AI 1.0.0-M6</b>.
 * Retiene la información de tipos primitivos, genéricos y obligatoriedad para
 * el generador de JSON Schema y Jackson converter.
 * </p>
 */
public record ToolParameter(
        String name,
        Class<?> javaType,
        Type genericType,
        String description,
        boolean required
) {}
