package com.boris.librixsoft.server.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ChatSessionService {

    private final Map<String, List<Message>> sessionHistory = new ConcurrentHashMap<>();

    /**
     * Creates a new session ID.
     */
    public String createSession() {
        String sessionId = UUID.randomUUID().toString();
        sessionHistory.put(sessionId, new ArrayList<>());
        log.info("Created new chat session: {}", sessionId);
        return sessionId;
    }

    /**
     * Gets or creates a session ID. If the provided ID is null or blank, creates a new one.
     * If the provided ID doesn't exist, initializes it in the session history silently.
     */
    public String getOrCreateSession(String sessionId) {
        if (sessionId == null || sessionId.isBlank()) {
            return createSession();
        }
        if (!sessionHistory.containsKey(sessionId)) {
            sessionHistory.put(sessionId, new ArrayList<>());
            log.debug("Initialized new chat session: {}", sessionId);
        }
        return sessionId;
    }

    /**
     * Gets the conversation history for a session.
     */
    public List<Message> getSessionHistory(String sessionId) {
        return sessionHistory.getOrDefault(sessionId, new ArrayList<>());
    }

    /**
     * Adds a message to the session history.
     */
    public void addMessage(String sessionId, Message message) {
        sessionHistory.computeIfAbsent(sessionId, k -> new ArrayList<>()).add(message);
        log.debug("Added message to session {}: role={}", sessionId, message.getMessageType());
    }

    /**
     * Adds multiple messages to the session history.
     */
    public void addMessages(String sessionId, List<Message> messages) {
        sessionHistory.computeIfAbsent(sessionId, k -> new ArrayList<>()).addAll(messages);
        log.debug("Added {} messages to session {}", messages.size(), sessionId);
    }

    /**
     * Clears the conversation history for a session.
     */
    public void clearSession(String sessionId) {
        if (sessionId != null && sessionHistory.containsKey(sessionId)) {
            sessionHistory.get(sessionId).clear();
            log.info("Cleared session: {}", sessionId);
        }
    }

    /**
     * Deletes a session entirely.
     */
    public void deleteSession(String sessionId) {
        if (sessionId != null) {
            sessionHistory.remove(sessionId);
            log.info("Deleted session: {}", sessionId);
        }
    }

    /**
     * Deletes all sessions entirely.
     */
    public void clearAll() {
        sessionHistory.clear();
        log.info("Cleared all chat sessions.");
    }

    /**
     * Gets the number of active sessions.
     */
    public int getActiveSessionCount() {
        return sessionHistory.size();
    }
}
