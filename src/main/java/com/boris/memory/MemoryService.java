package com.boris.memory;

import com.boris.settings.Settings;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MemoryService {

    private final ConversationRepository repository;
    private String sessionId;
    private int maxContextTokens;
    private int maxHistoryMessages;
    private final int recentFull;

    public MemoryService(ConversationRepository repository, MemoryProperties props) {
        this.repository = repository;
        this.sessionId = props.getSessionId() != null && !props.getSessionId().isEmpty() 
            ? props.getSessionId() : UUID.randomUUID().toString();
        this.maxContextTokens = props.getMaxContextTokens();
        this.maxHistoryMessages = props.getMaxHistoryMessages();
        this.recentFull = props.getRecentFull();
    }

    public void configureFromSettings(Settings.MemoryConfig memoryConfig) {
        if (memoryConfig == null) return;
        if (memoryConfig.getMaxContextTokens() != null) this.maxContextTokens = memoryConfig.getMaxContextTokens();
        if (memoryConfig.getMaxHistoryMessages() != null) this.maxHistoryMessages = memoryConfig.getMaxHistoryMessages();
        if (memoryConfig.getSessionId() != null && !memoryConfig.getSessionId().isEmpty()) {
            this.sessionId = memoryConfig.getSessionId();
        }
    }

    public void saveUserMessage(String content) {
        saveMessage("user", content);
    }

    public void saveAssistantMessage(String content) {
        saveMessage("assistant", content);
    }

    private void saveMessage(String role, String content) {
        int tokens = estimateTokens(content);
        ConversationMessage message = new ConversationMessage(sessionId, role, content, tokens);
        repository.save(message);
    }

    public List<ConversationMessage> getRecentMessages(int limit) {
        return repository.findBySessionIdOrderByTimestampDesc(sessionId, PageRequest.of(0, limit)).getContent();
    }

    public List<ConversationMessage> getAllMessages() {
        return repository.findBySessionIdOrderByTimestampAsc(sessionId);
    }

    public List<ConversationMessage> getMessagesForContext(String currentQuery) {
        return getMessagesForContext(currentQuery, maxContextTokens, maxHistoryMessages);
    }

    public List<ConversationMessage> getMessagesForContext(String currentQuery, int tokenBudget, int maxMessages) {
        List<ConversationMessage> recent = repository.findBySessionIdOrderByTimestampDesc(sessionId, PageRequest.of(0, recentFull)).getContent();
        if (recent.isEmpty()) {
            return List.of();
        }

        List<ConversationMessage> selectedMessages = new ArrayList<>();
        int estimatedTokens = 0;

        for (int i = recent.size() - 1; i >= 0; i--) {
            ConversationMessage msg = recent.get(i);
            int msgTokens = msg.getTokens() != null ? msg.getTokens() : estimateTokens(msg.getContent());

            if (estimatedTokens + msgTokens > tokenBudget) {
                break;
            }

            selectedMessages.add(0, msg);
            estimatedTokens += msgTokens;

            if (selectedMessages.size() >= maxMessages) {
                break;
            }
        }

        return selectedMessages;
    }

    public String buildContextPrompt(String currentMessage, int tokenBudget, int maxMessages) {
        List<ConversationMessage> contextMessages = getMessagesForContext(currentMessage, tokenBudget, maxMessages);

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("===== CONTEXTO DE LA CONVERSACIÓN =====\n");

        if (!contextMessages.isEmpty()) {
            promptBuilder.append("--- HISTORIAL RECIENTE ---\n");
            for (ConversationMessage msg : contextMessages) {
                promptBuilder.append(msg.getRole().toUpperCase()).append(": ").append(msg.getContent()).append("\n");
            }
            promptBuilder.append("\n");
        }

        promptBuilder.append("===== FIN DEL CONTEXTO =====\n");
        promptBuilder.append("MENSAJE ACTUAL: ").append(currentMessage);
        promptBuilder.append("\n\nINSTRUCCIÓN: Continúa secuencialmente desde donde nos quedamos.");

        return promptBuilder.toString();
    }

    public int estimatePromptTokens(String prompt) {
        return estimateTokens(prompt);
    }

    public List<ConversationMessage> searchRelevantMessages(String query, int limit) {
        return repository.findBySessionIdOrderByTimestampDesc(sessionId, PageRequest.of(0, limit)).getContent();
    }

    public String buildContextPrompt(String currentMessage) {
        List<ConversationMessage> contextMessages = getMessagesForContext(currentMessage);

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("===== CONTEXTO DE LA CONVERSACIÓN =====\n");

        if (!contextMessages.isEmpty()) {
            promptBuilder.append("--- HISTORIAL RECIENTE ---\n");
            for (ConversationMessage msg : contextMessages) {
                promptBuilder.append(msg.getRole().toUpperCase()).append(": ").append(msg.getContent()).append("\n");
            }
            promptBuilder.append("\n");
        }

        promptBuilder.append("===== FIN DEL CONTEXTO =====\n");
        promptBuilder.append("MENSAJE ACTUAL: ").append(currentMessage);
        promptBuilder.append("\n\nINSTRUCCIÓN: Continúa secuencialmente desde donde nos quedamos.");

        return promptBuilder.toString();
    }

    public void clearSession() {
        repository.deleteBySessionId(sessionId);
    }

    public long getMessageCount() {
        return repository.countBySessionId(sessionId);
    }

    public String getSessionId() {
        return sessionId;
    }

    private int estimateTokens(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        return (int) Math.ceil(text.length() / 3.5);
    }
}