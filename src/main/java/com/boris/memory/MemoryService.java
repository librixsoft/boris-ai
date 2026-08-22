package com.boris.memory;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class MemoryService {

    private final ConversationRepository repository;
    private final String sessionId;
    private final int maxContextTokens;
    private final int maxHistoryMessages;

    public MemoryService(ConversationRepository repository,
                         @Value("${spring.ai.memory.session-id:default}") String sessionId,
                         @Value("${spring.ai.memory.max-context-tokens:8000}") int maxContextTokens,
                         @Value("${spring.ai.memory.max-history-messages:50}") int maxHistoryMessages) {
        this.repository = repository;
        this.sessionId = sessionId != null && !sessionId.isEmpty() ? sessionId : UUID.randomUUID().toString();
        this.maxContextTokens = maxContextTokens;
        this.maxHistoryMessages = maxHistoryMessages;
    }

    public void saveUserMessage(String content) {
        saveMessage("user", content, estimateTokens(content));
    }

    public void saveAssistantMessage(String content) {
        saveMessage("assistant", content, estimateTokens(content));
    }

    private void saveMessage(String role, String content, int tokens) {
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
        List<ConversationMessage> allMessages = getAllMessages();
        if (allMessages.isEmpty()) {
            return List.of();
        }

        int estimatedTokens = 0;
        List<ConversationMessage> selectedMessages = new ArrayList<>();

        for (int i = allMessages.size() - 1; i >= 0; i--) {
            ConversationMessage msg = allMessages.get(i);
            int msgTokens = msg.getTokens() != null ? msg.getTokens() : estimateTokens(msg.getContent());

            if (estimatedTokens + msgTokens > maxContextTokens) {
                break;
            }

            selectedMessages.add(0, msg);
            estimatedTokens += msgTokens;

            if (selectedMessages.size() >= maxHistoryMessages) {
                break;
            }
        }

        return selectedMessages;
    }

    public List<ConversationMessage> searchRelevantMessages(String query, int limit) {
        List<ConversationMessage> allMessages = getAllMessages();
        if (allMessages.isEmpty() || query == null || query.isBlank()) {
            return List.of();
        }

        String lowerQuery = query.toLowerCase();
        List<ConversationMessage> scored = new ArrayList<>();

        for (ConversationMessage msg : allMessages) {
            int score = calculateRelevance(msg.getContent().toLowerCase(), lowerQuery);
            if (score > 0) {
                scored.add(msg);
            }
        }

        scored.sort((a, b) -> {
            int scoreA = calculateRelevance(a.getContent().toLowerCase(), lowerQuery);
            int scoreB = calculateRelevance(b.getContent().toLowerCase(), lowerQuery);
            return Integer.compare(scoreB, scoreA);
        });

        return scored.stream().limit(limit).toList();
    }

    private int calculateRelevance(String content, String query) {
        int score = 0;
        String[] queryWords = query.split("\\s+");

        for (String word : queryWords) {
            if (word.length() < 3) continue;
            int occurrences = countOccurrences(content, word);
            score += occurrences * word.length();
        }

        return score;
    }

    private int countOccurrences(String text, String word) {
        int count = 0;
        int index = 0;
        while ((index = text.indexOf(word, index)) != -1) {
            count++;
            index += word.length();
        }
        return count;
    }

    public String buildContextPrompt(String currentMessage) {
        List<ConversationMessage> contextMessages = getMessagesForContext(currentMessage);
        List<ConversationMessage> relevantMessages = searchRelevantMessages(currentMessage, 5);

        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("===== CONTEXTO DE LA CONVERSACIÓN (MEMORIA PERSISTENTE) =====\n");
        promptBuilder.append("IMPORTANTE: Mantén el contexto de lo que estamos trabajando. Si estábamos en medio de una tarea, continúa desde donde nos quedamos.\n\n");

        if (!contextMessages.isEmpty()) {
            promptBuilder.append("--- HISTORIAL RECIENTE ---\n");
            for (ConversationMessage msg : contextMessages) {
                promptBuilder.append(msg.getRole().toUpperCase()).append(": ").append(msg.getContent()).append("\n");
            }
            promptBuilder.append("\n");
        }

        if (!relevantMessages.isEmpty()) {
            boolean hasRelevantNotInContext = relevantMessages.stream()
                    .anyMatch(rm -> contextMessages.stream().noneMatch(cm -> cm.getId().equals(rm.getId())));

            if (hasRelevantNotInContext) {
                promptBuilder.append("--- MENSAJES RELEVANTES ENCONTRADOS EN MEMORIA ---\n");
                for (ConversationMessage msg : relevantMessages) {
                    if (contextMessages.stream().noneMatch(cm -> cm.getId().equals(msg.getId()))) {
                        promptBuilder.append("[MEMORIA] ").append(msg.getRole().toUpperCase()).append(": ").append(msg.getContent()).append("\n");
                    }
                }
                promptBuilder.append("\n");
            }
        }

        promptBuilder.append("===== FIN DEL CONTEXTO =====\n");
        promptBuilder.append("MENSAJE ACTUAL: ").append(currentMessage);
        promptBuilder.append("\n\nINSTRUCCIÓN: Si esto es una continuación de una tarea anterior, continúa secuencialmente desde donde nos quedamos. No empieces de nuevo ni saltes pasos. Usa la información de MEMORIA si es relevante.");

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