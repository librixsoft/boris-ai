package com.boris.librixsoft.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * DTO para representar un mensaje de chat en la cola.
 * Contiene la instrucción del usuario y el sessionId para mantener el orden por sesión.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageRequest {
    private String messageId;
    private String instruction;
    private String sessionId;
    private long timestamp;

    public ChatMessageRequest(String instruction, String sessionId) {
        this.messageId = UUID.randomUUID().toString();
        this.instruction = instruction;
        this.sessionId = sessionId;
        this.timestamp = System.currentTimeMillis();
    }
}
