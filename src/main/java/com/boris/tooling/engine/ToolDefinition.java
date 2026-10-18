package com.boris.tooling.engine;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

/**
 * Representa la definición en memoria de una herramienta ejecutable por el LLM.
 *
 * <p><b>Referencia histórica / Equivalencia:</b><br>
 * Reemplaza a {@code org.springframework.ai.tool.definition.ToolDefinition} de <b>Spring AI 1.0.0-M6</b>.
 * Almacena los metadatos necesarios (nombre, descripción, método reflexivo, instancia y schema OpenAI)
 * para la resolución y ejecución de tool calls.
 * </p>
 */
public record ToolDefinition(
        String name,
        String description,
        Object target,
        Method method,
        List<ToolParameter> parameters,
        Map<String, Object> openAiToolSchema
) {}
