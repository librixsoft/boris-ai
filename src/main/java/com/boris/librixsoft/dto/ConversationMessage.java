package com.boris.librixsoft.dto;

import com.boris.librixsoft.ai.AssistantMessage;
import com.boris.librixsoft.ai.Message;
import com.boris.librixsoft.ai.SystemMessage;
import com.boris.librixsoft.ai.UserMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessage {
    private String role;
    private String content;
    private long timestamp;

    public Message toAiMessage() {
        return switch ((role == null ? "" : role).toLowerCase()) {
            case "assistant" -> new AssistantMessage(content);
            case "system" -> new SystemMessage(content);
            case "user" -> new UserMessage(content);
            default -> new UserMessage(content);
        };
    }
}
