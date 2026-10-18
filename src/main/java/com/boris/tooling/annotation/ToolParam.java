package com.boris.tooling.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación para documentar los parámetros de un método anotado con {@link Tool}.
 *
 * <p><b>Referencia histórica / Equivalencia:</b><br>
 * Reemplaza a {@code org.springframework.ai.tool.annotation.ToolParam} de <b>Spring AI 1.0.0-M6</b>.
 * Conserva la compatibilidad con el atributo {@code description} y {@code required} para
 * la generación del JSON Schema en {@code ToolRegistry}.
 * </p>
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface ToolParam {
    /**
     * Descripción del parámetro para el schema del LLM.
     */
    String description() default "";

    /**
     * Indica si el parámetro es obligatorio. Por defecto true.
     */
    boolean required() default true;
}
