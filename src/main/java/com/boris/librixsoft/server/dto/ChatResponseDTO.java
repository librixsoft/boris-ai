package com.boris.librixsoft.server.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para la respuesta del modelo en el flujo de orquestación single-model.
 * - CREATE / REDESIGN: el modelo devuelve el código en "content".
 * - EDIT:             el modelo devuelve instrucciones en "instructions".
 * - DELETE:           solo necesita "type" y "path".
 */
@Data
@NoArgsConstructor
public class ChatResponseDTO {
    private String type;
    private String path;
    private String instructions;
    private String content;

    /** Devuelve el código/instrucciones según lo que haya rellenado el modelo. */
    public String getEffectiveContent() {
        return (content != null && !content.isBlank()) ? content : instructions;
    }

    /** Alias de compatibilidad para código existente. */
    public String getEffectiveInstructions() {
        return getEffectiveContent();
    }
}
