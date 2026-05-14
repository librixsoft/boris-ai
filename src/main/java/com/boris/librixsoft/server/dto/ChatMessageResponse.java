package com.boris.librixsoft.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO para representar la respuesta de un mensaje de chat procesado.
 * Incluye el messageId original para correlacionar con la solicitud.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private String messageId;
    private String status;
    private String message;
    private Object content;
    private long processedAt;

    public ChatMessageResponse(String messageId, String status, String message, Object content) {
        this.messageId = messageId;
        this.status = status;
        this.message = message;
        this.content = content;
        this.processedAt = System.currentTimeMillis();
    }
}
