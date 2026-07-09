package com.boris.librixsoft.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConversationSession {
    private String sessionId;
    private List<ConversationMessage> messages = new ArrayList<>();
    private long createdAt;
    private long updatedAt;
}
