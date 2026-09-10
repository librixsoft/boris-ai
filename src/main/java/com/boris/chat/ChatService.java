package com.boris.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.boris.llm.think.OllamaThinkSpringAiFactory;
import com.boris.llm.think.ThinkContextHolder;
import com.boris.settings.Settings;
import com.boris.settings.SettingsManager;
import com.boris.task.TaskAborter;

public class ChatService {

    public static final String EXIT_COMMAND = "EXIT";

    private final Supplier<ChatClient> chatClientSupplier;
    private final String botName;
    private final TaskAborter taskAborter;
    private final List<String> conversationHistory;
    private final int maxHistorySize;
    private final boolean enableHistory;
    private boolean thinkingEnabled;
    private String thinkingMode;
    private final String modelName;
    private String lastThinkingContent;
    private static final Pattern THINKING_PATTERN = Pattern.compile("```(.*?)```", Pattern.DOTALL);

    public ChatService(Supplier<ChatClient> chatClientSupplier, String botName, TaskAborter taskAborter, int maxHistorySize, boolean enableHistory) {
        this(chatClientSupplier, botName, taskAborter, maxHistorySize, enableHistory, true, "think", "");
    }

    public ChatService(Supplier<ChatClient> chatClientSupplier, String botName, TaskAborter taskAborter, int maxHistorySize, boolean enableHistory, boolean thinkingEnabled, String thinkingMode) {
        this(chatClientSupplier, botName, taskAborter, maxHistorySize, enableHistory, thinkingEnabled, thinkingMode, "");
    }

    public ChatService(Supplier<ChatClient> chatClientSupplier, String botName, TaskAborter taskAborter, int maxHistorySize, boolean enableHistory, boolean thinkingEnabled, String thinkingMode, String modelName) {
        this(chatClientSupplier, botName, taskAborter, maxHistorySize, enableHistory, thinkingEnabled, thinkingMode, modelName, null);
    }

    public ChatService(Supplier<ChatClient> chatClientSupplier, String botName, TaskAborter taskAborter, int maxHistorySize, boolean enableHistory, boolean thinkingEnabled, String thinkingMode, String modelName, String ollamaBaseUrl) {
        this.chatClientSupplier = chatClientSupplier;
        this.botName = botName;
        this.taskAborter = taskAborter;
        this.conversationHistory = new ArrayList<>();
        this.maxHistorySize = maxHistorySize;
        this.enableHistory = enableHistory;
        this.thinkingEnabled = thinkingEnabled;
        this.thinkingMode = thinkingMode;
        this.modelName = modelName;
    }

    public String sendMessage(String userMessage) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new com.boris.exceptions.BorisException("User message cannot be null or empty");
        }
        if (taskAborter.isAborted()) {
            return null;
        }
        String lower = userMessage.toLowerCase().trim();
        if ("q".equals(lower) || "exit".equals(lower)) {
            return EXIT_COMMAND;
        }
        ChatClient client = chatClientSupplier.get();
        if (client == null) {
            throw new IllegalStateException("Spring AI ChatClient is required");
        }
        try {
            String fullMessage = buildPromptWithHistory(userMessage);
            if (enableHistory) {
                conversationHistory.add("User: " + userMessage);
                trimHistory();
            }
            String effectiveThink = thinkingEnabled ? resolveThinkMode() : null;
            ChatTurn turn = callSpringAiOnce(client, fullMessage, effectiveThink);
            String thinkingContent = turn.thinking();
            String finalResponse = turn.response();
            if (enableHistory && finalResponse != null && !finalResponse.isEmpty()) {
                conversationHistory.add(botName + ": " + finalResponse);
                trimHistory();
            }
            if (thinkingContent != null && !thinkingContent.isEmpty()) {
                return "*%s* ```\n%s\n```\n%s".formatted(botName, thinkingContent, finalResponse != null ? finalResponse : "");
            }
            return "*%s* %s".formatted(botName, finalResponse != null ? finalResponse : "");
        } catch (Exception e) {
            ThinkContextHolder.clearAll();
            if (taskAborter.isAborted()) {
                return null;
            }
            throw new com.boris.exceptions.BorisException("Chat error", e);
        }
    }

    public void sendMessageStream(String userMessage, Consumer<String> onChunk, Runnable onComplete) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new com.boris.exceptions.BorisException("User message cannot be null or empty");
        }
        if (taskAborter.isAborted()) {
            return;
        }
        String lower = userMessage.toLowerCase().trim();
        if ("q".equals(lower) || "exit".equals(lower)) {
            return;
        }
        ChatClient client = chatClientSupplier.get();
        if (client == null) {
            throw new IllegalStateException("Spring AI ChatClient is required");
        }
        String effectiveThink = thinkingEnabled ? resolveThinkMode() : null;
        try {
            String fullMessage = buildPromptWithHistory(userMessage);
            if (enableHistory) {
                conversationHistory.add("User: " + userMessage);
                trimHistory();
            }
            ChatTurn streamTurn = callSpringAiOnce(client, fullMessage, effectiveThink);
            String thinkingContent = streamTurn.thinking();
            String finalResponse = streamTurn.response();
            if (enableHistory && finalResponse != null && !finalResponse.isEmpty()) {
                conversationHistory.add(botName + ": " + finalResponse);
                trimHistory();
            }
            if (finalResponse != null && !finalResponse.isEmpty()) {
                onChunk.accept(finalResponse);
            }
            if (thinkingContent != null && !thinkingContent.isEmpty()) {
                onChunk.accept("\n<thinking>\n" + thinkingContent + "\n</thinking>\n");
            }
            if (onComplete != null) {
                onComplete.run();
            }
        } catch (Exception e) {
            ThinkContextHolder.clearAll();
            if (taskAborter.isAborted()) {
                return;
            }
            throw new com.boris.exceptions.BorisException("Chat error", e);
        } finally {
            ThinkContextHolder.clearAll();
        }
    }

    // Unico punto de llamada al LLM: todo pasa por el ChatClient de Spring AI.
    // El think viaja como reasoning_effort (nivel o "none" si va apagado) y el
    // trace se recupera de ThinkContextHolder (campos fuera del content).
    private ChatTurn callSpringAiOnce(ChatClient client, String fullMessage, String effectiveThink) {
        ThinkContextHolder.setThinkMode(effectiveThink);
        ThinkContextHolder.clearLastThinking();
        try {
            String response = client.prompt(fullMessage)
                    .options(buildSpringAiOptions(effectiveThink))
                    .call().content();
            String thinkingContent = ThinkContextHolder.getLastThinking();
            if ((thinkingContent == null || thinkingContent.isBlank()) && thinkingEnabled) {
                thinkingContent = extractThinkingContent(response);
            }
            String finalResponse = response;
            if (thinkingContent != null && !thinkingContent.isBlank() && response != null && THINKING_PATTERN.matcher(response).find()) {
                finalResponse = extractFinalResponse(response);
            }
            if (thinkingContent != null && !thinkingContent.isBlank()) {
                this.lastThinkingContent = thinkingContent;
            }
            return new ChatTurn(finalResponse, thinkingContent);
        } finally {
            ThinkContextHolder.clearThinkMode();
        }
    }

    private record ChatTurn(String response, String thinking) {
    }

    public static ChatService withTools(String settingsPath, String botName) throws Exception {
        SettingsManager mgr = new SettingsManager();
        mgr.ensureAgentsMd();
        Settings s = mgr.loadSettings(settingsPath);
        if (s == null || s.getModel() == null) {
            throw new IllegalStateException("Settings file not found or invalid: " + settingsPath);
        }
        String modelName = s.getModel().getName();
        String resolvedThink = OllamaThinkSpringAiFactory.resolveThinkMode(s);
        boolean thinkingEnabled = resolvedThink != null;
        String thinkingMode = resolvedThink != null ? resolvedThink : "low";
        ChatClient client = OllamaThinkSpringAiFactory.createChatClient(s, thinkingEnabled ? thinkingMode : null);
        int historySize = s.getMaxHistorySize();
        boolean enableHistory = s.getEnableHistory() != null ? s.getEnableHistory() : true;
        TaskAborter aborter = new TaskAborter();
        return new ChatService(() -> client, botName, aborter, historySize, enableHistory, thinkingEnabled, thinkingMode, modelName);
    }

    public TaskAborter getTaskAborter() {
        return taskAborter;
    }

    public void setThinkingEnabled(boolean enabled) {
        this.thinkingEnabled = enabled;
    }

    public boolean isThinkingEnabled() {
        return thinkingEnabled;
    }

    public void clearHistory() {
        conversationHistory.clear();
    }

    public List<String> getConversationHistory() {
        return new ArrayList<>(conversationHistory);
    }

    private void trimHistory() {
        while (conversationHistory.size() > maxHistorySize) {
            conversationHistory.remove(0);
        }
    }

    private String buildPromptWithHistory(String currentMessage) {
        if (!enableHistory || conversationHistory.isEmpty()) {
            return currentMessage;
        }
        StringBuilder promptBuilder = new StringBuilder();
        promptBuilder.append("===== CONTEXTO DE LA CONVERSACIÓN ANTERIOR =====\n");
        promptBuilder.append("IMPORTANTE: Mantén el contexto de lo que estamos trabajando. Si estábamos en medio de una tarea, continúa desde donde nos quedamos.\n\n");
        for (String message : conversationHistory) {
            promptBuilder.append(message).append("\n");
        }
        promptBuilder.append("\n===== FIN DEL CONTEXTO =====\n");
        promptBuilder.append("MENSAJE ACTUAL: ").append(currentMessage);
        promptBuilder.append("\n\nINSTRUCCIÓN: Si esto es una continuación de una tarea anterior, continúa secuencialmente desde donde nos quedamos. No empieces de nuevo ni saltes pasos.");
        return promptBuilder.toString();
    }

    private String extractThinkingContent(String response) {
        if (response == null) {
            return null;
        }
        Matcher matcher = THINKING_PATTERN.matcher(response);
        if (matcher.find()) {
            return matcher.group(1).trim();
        }
        return null;
    }

    private String extractFinalResponse(String response) {
        if (response == null) {
            return null;
        }
        return THINKING_PATTERN.matcher(response).replaceAll("").trim();
    }

    public String getLastThinkingContent() {
        String holder = ThinkContextHolder.getLastThinking();
        if (holder != null && !holder.isBlank()) {
            return holder;
        }
        return lastThinkingContent;
    }

    public boolean usesOllamaApi() {
        return false;
    }

    // Opciones Spring AI por llamada: el nivel de think o "none" para
    // apagarlo (Ollama lo auto-activa si reasoning_effort se omite).
    private OpenAiChatOptions buildSpringAiOptions(String effectiveThink) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder().model(this.modelName);
        if (effectiveThink != null && !effectiveThink.isBlank()) {
            builder.reasoningEffort(effectiveThink);
        } else {
            builder.reasoningEffort("none");
        }
        return builder.build();
    }

    private String resolveThinkMode() {
        if (thinkingMode != null && !thinkingMode.isBlank()) {
            return thinkingMode;
        }
        return "low";
    }
}
