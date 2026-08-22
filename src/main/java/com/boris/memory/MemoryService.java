package com.boris.memory;

import com.boris.settings.Settings;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
@Transactional
public class MemoryService {

    private final ConversationRepository repository;
    private String sessionId;
    private int maxContextTokens;
    private int maxHistoryMessages;
    private final int recentFull;
    private final int searchLimit;

    public MemoryService(ConversationRepository repository, MemoryProperties props) {
        this.repository = repository;
        this.sessionId = props.getSessionId() != null && !props.getSessionId().isEmpty()
                ? props.getSessionId() : UUID.randomUUID().toString();
        this.maxContextTokens = props.getMaxContextTokens();
        this.maxHistoryMessages = props.getMaxHistoryMessages();
        this.recentFull = props.getRecentFull();
        this.searchLimit = props.getSearchLimit();
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
        return getMessagesForContext(currentQuery, maxContextTokens, searchLimit);
    }

    public List<ConversationMessage> getMessagesForContext(String currentQuery, int tokenBudget, int maxMessages) {
        List<String> keywords = extractKeywords(currentQuery);
        if (keywords.isEmpty()) {
            return List.of();
        }

        Map<Long, ConversationMessage> unique = new LinkedHashMap<>();
        for (String keyword : keywords) {
            for (ConversationMessage msg : repository.findByKeyword(sessionId, keyword, PageRequest.of(0, searchLimit))) {
                if (msg.getId() != null) {
                    unique.putIfAbsent(msg.getId(), msg);
                }
            }
        }

        List<ConversationMessage> chronological = unique.values().stream()
                .sorted(Comparator.comparing(ConversationMessage::getTimestamp))
                .toList();

        List<ConversationMessage> selectedMessages = new ArrayList<>();
        int estimatedTokens = 0;

        for (int i = chronological.size() - 1; i >= 0; i--) {
            ConversationMessage msg = chronological.get(i);
            int msgTokens = msg.getTokens() != null ? msg.getTokens() : estimateTokens(msg.getContent());

            if (estimatedTokens + msgTokens > tokenBudget || selectedMessages.size() >= maxMessages) {
                break;
            }

            selectedMessages.add(0, msg);
            estimatedTokens += msgTokens;
        }

        return selectedMessages;
    }

    private List<String> extractKeywords(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return Arrays.stream(query.toLowerCase().split("[^a-záéíóúñü0-9]+"))
                .filter(word -> word.length() >= 3)
                .distinct()
                .limit(8)
                .toList();
    }

    public String buildContextPrompt(String currentMessage, int tokenBudget, int maxMessages) {
        List<ConversationMessage> contextMessages = getMessagesForContext(currentMessage, tokenBudget, maxMessages);

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("===== CONTEXTO DE LA CONVERSACIÓN =====\n");

        if (!contextMessages.isEmpty()) {
            promptBuilder.append("--- CONTEXTO RECUPERADO DE MEMORIA ---\n");
            for (ConversationMessage msg : contextMessages) {
                promptBuilder.append(msg.getRole().toUpperCase()).append(": ").append(msg.getContent()).append("\n");
            }
            promptBuilder.append("\n");
        }

        promptBuilder.append("===== FIN DEL CONTEXTO =====\n");
        promptBuilder.append("MENSAJE ACTUAL: ").append(currentMessage);
        promptBuilder.append("\n\nINSTRUCCIÓN: Usa el contexto recuperado solo si es relevante para el mensaje actual.");

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
            promptBuilder.append("--- CONTEXTO RECUPERADO DE MEMORIA ---\n");
            for (ConversationMessage msg : contextMessages) {
                promptBuilder.append(msg.getRole().toUpperCase()).append(": ").append(msg.getContent()).append("\n");
            }
            promptBuilder.append("\n");
        }

        promptBuilder.append("===== FIN DEL CONTEXTO =====\n");
        promptBuilder.append("MENSAJE ACTUAL: ").append(currentMessage);
        promptBuilder.append("\n\nINSTRUCCIÓN: Usa el contexto recuperado solo si es relevante para el mensaje actual.");

        return promptBuilder.toString();
    }

    public List<ConversationMessage> searchRelevantContext(String userMessage, int limit) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return List.of();
        }
        return repository.findByKeyword(sessionId, userMessage.trim(), PageRequest.of(0, limit));
    }

    public String buildSwappedContextPrompt(String currentMessage, List<ConversationMessage> relevantMessages) {
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("===== CONTEXTO RECUPERADO DE MEMORIA =====\n");

        if (!relevantMessages.isEmpty()) {
            for (ConversationMessage msg : relevantMessages) {
                promptBuilder.append(msg.getRole().toUpperCase()).append(": ").append(msg.getContent()).append("\n");
            }
            promptBuilder.append("\n");
        }

        promptBuilder.append("===== FIN CONTEXTO RECUPERADO =====\n");
        promptBuilder.append("MENSAJE ACTUAL: ").append(currentMessage);

        return promptBuilder.toString();
    }

    public void clearSession() {
        repository.deleteBySessionId(sessionId);
    }

    public long getMessageCount() {
        return repository.countBySessionId(sessionId);
    }

    public long getPersistedTokens() {
        return repository.sumTokensBySessionId(sessionId);
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
