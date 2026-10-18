package com.boris.tooling.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Anotación para declarar un método como herramienta (tool) ejecutable por el LLM.
 *
 * <p><b>Referencia histórica / Equivalencia:</b><br>
 * Reemplaza a {@code org.springframework.ai.tool.annotation.Tool} de <b>Spring AI 1.0.0-M6</b>.
 * Conserva la compatibilidad con los atributos {@code name} y {@code description} para
 * la generación del JSON Schema compatible con OpenAI / Ollama (/v1/chat/completions).
 * </p>
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Tool {
    /**
     * Nombre de la herramienta. Si está vacío, se usará el nombre del método Java.
     */
    String name() default "";

    /**
     * Descripción detallada del propósito y funcionamiento de la herramienta.
     */
    String description() default "";
}
