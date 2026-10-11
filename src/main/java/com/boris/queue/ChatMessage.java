package com.boris.queue;

import java.time.Instant;
import java.util.UUID;

public record ChatMessage(
    String id,
    String content,
    Instant timestamp,
    MessageStatus status
) {
    public static ChatMessage create(String content) {
        return new ChatMessage(
            UUID.randomUUID().toString(),
            content,
            Instant.now(),
            MessageStatus.PENDING
        );
    }

    public ChatMessage withStatus(MessageStatus newStatus) {
        return new ChatMessage(id, content, timestamp, newStatus);
    }
}
