package com.boris.librixsoft.level3.domain.service;

import com.boris.librixsoft.ai.Message;
import com.boris.librixsoft.dto.ConversationMessage;
import com.boris.librixsoft.dto.ConversationSession;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class ConversationHistoryService {

    private final ConcurrentMap<String, ConversationSession> sessions = new ConcurrentHashMap<>();

    public ConversationSession getOrCreateSession(String sessionId) {
        String normalizedSessionId = normalizeSessionId(sessionId);
        return sessions.computeIfAbsent(normalizedSessionId, id -> {
            long now = System.currentTimeMillis();
            return new ConversationSession(id, new ArrayList<>(), now, now);
        });
    }

    public List<Message> getHistoryAsAiMessages(String sessionId) {
        return new ArrayList<>(getOrCreateSession(sessionId).getMessages().stream()
                .map(ConversationMessage::toAiMessage)
                .toList());
    }

    public List<ConversationMessage> getHistory(String sessionId) {
        return new ArrayList<>(getOrCreateSession(sessionId).getMessages());
    }

    public ConversationSession getSessionSnapshot(String sessionId) {
        ConversationSession session = getOrCreateSession(sessionId);
        return new ConversationSession(
                session.getSessionId(),
                new ArrayList<>(session.getMessages()),
                session.getCreatedAt(),
                session.getUpdatedAt()
        );
    }

    public void appendUserMessage(String sessionId, String content) {
        appendMessage(sessionId, "user", content);
    }

    public void appendAssistantMessage(String sessionId, String content) {
        appendMessage(sessionId, "assistant", content);
    }

    public void appendSystemMessage(String sessionId, String content) {
        appendMessage(sessionId, "system", content);
    }

    public void clearHistory(String sessionId) {
        sessions.remove(normalizeSessionId(sessionId));
    }

    private void appendMessage(String sessionId, String role, String content) {
        if (content == null || content.isBlank()) {
            return;
        }

        ConversationSession session = getOrCreateSession(sessionId);
        synchronized (session) {
            long now = System.currentTimeMillis();
            session.getMessages().add(new ConversationMessage(role, content, now));
            session.setUpdatedAt(now);
        }
    }

    private String normalizeSessionId(String sessionId) {
        return (sessionId == null || sessionId.isBlank()) ? "default-session" : sessionId;
    }
}
