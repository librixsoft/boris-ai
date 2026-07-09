package com.boris.librixsoft.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageResponse {
    private String messageId;
    private String status;
    private String message;
    private Object content;
    private long processedAt;
    private Integer promptTokens;
    private Integer completionTokens;
    private Integer totalTokens;
    private Integer contextSize;

    // Constructor original sin tokens (para eventos de texto)
    public ChatMessageResponse(String messageId, String status, String message, Object content) {
        this.messageId = messageId;
        this.status = status;
        this.message = message;
        this.content = content;
        this.processedAt = System.currentTimeMillis();
    }

    // Constructor para el evento final de tokens
    public ChatMessageResponse(String messageId, String status, String message, Object content,
                                Integer promptTokens, Integer completionTokens, Integer totalTokens, Integer contextSize) {
        this.messageId = messageId;
        this.status = status;
        this.message = message;
        this.content = content;
        this.processedAt = System.currentTimeMillis();
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
        this.contextSize = contextSize;
    }
}