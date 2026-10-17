package com.boris.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;

import com.boris.edit.StreamingFileWriter;
import com.boris.exceptions.ChatException;
import com.boris.exceptions.ChatException.ErrorType;
import com.boris.llm.think.OllamaThinkSpringAiFactory;
import com.boris.llm.think.ThinkContextHolder;
import com.boris.tooling.ToolCallLimiter;
import com.boris.tooling.ToolResultNotifier;
import com.boris.settings.Settings;
import com.boris.settings.SettingsManager;
import com.boris.task.TaskAborter;

public class ChatService {

    public static final String EXIT_COMMAND = "EXIT";

    private final Supplier<ChatClient> chatClientSupplier;
    private final String botName;
    private final TaskAborter taskAborter;
    private final List<String> conversationHistory;
    private final List<ToolResultNotifier.ToolQueryResult> queryResultsCache;
    private static final int MAX_QUERY_CACHE_SIZE = 5;
    private final int maxHistorySize;
    private final boolean enableHistory;
    private boolean thinkingEnabled;
    private String thinkingMode;
    private volatile String modelName;
    private String lastThinkingContent;
    private volatile ChatClient currentClient;
    private volatile Settings currentSettings;
    private final StreamingFileWriter fileWriter;
    private static final Pattern THINKING_PATTERN = Pattern.compile("(?i)(?:<think>|<thinking>|<thought>|<reasoning>|```)(.*?)(?:</think>|</thinking>|</thought>|</reasoning>|```)", Pattern.DOTALL);

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
        this.queryResultsCache = new ArrayList<>();
        this.maxHistorySize = maxHistorySize;
        this.enableHistory = enableHistory;
        this.thinkingEnabled = thinkingEnabled;
        this.thinkingMode = thinkingMode;
        this.modelName = modelName;
        this.fileWriter = new StreamingFileWriter();
        if (taskAborter != null && modelName != null) {
            taskAborter.setActiveModel(modelName);
        }
        ToolResultNotifier.addQueryListener(this::onQueryResult);
    }

    private void onQueryResult(ToolResultNotifier.ToolQueryResult result) {
        synchronized (queryResultsCache) {
            queryResultsCache.add(result);
            while (queryResultsCache.size() > MAX_QUERY_CACHE_SIZE) {
                queryResultsCache.remove(0);
            }
        }
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
        ToolCallLimiter.reset();
        ChatClient client = getActiveClient();
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
                return "*%s*\n<thinking>\n%s\n</thinking>\n%s".formatted(botName, thinkingContent, finalResponse != null ? finalResponse : "");
            }
            return "*%s* %s".formatted(botName, finalResponse != null ? finalResponse : "");
        } catch (Exception e) {
            ThinkContextHolder.clearAll();
            if (taskAborter.isAborted()) {
                return null;
            }
            throw new ChatException("Error en chat", e, classifyError(e), modelName);
        }
    }

    public void sendMessageStream(String userMessage, Consumer<String> onChunk, Runnable onComplete) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new ChatException("El mensaje no puede estar vacío", ErrorType.INVALID_INPUT);
        }
        if (taskAborter.isAborted()) {
            return;
        }
        String lower = userMessage.toLowerCase().trim();
        if ("q".equals(lower) || "exit".equals(lower)) {
            return;
        }
        ToolCallLimiter.reset();
        ChatClient client = getActiveClient();
        String effectiveThink = thinkingEnabled ? resolveThinkMode() : null;

        try {
            String fullMessage = buildPromptWithHistory(userMessage);
            if (enableHistory) {
                conversationHistory.add("User: " + userMessage);
                trimHistory();
            }

            ThinkContextHolder.setThinkMode(effectiveThink);
            Thread currentThread = Thread.currentThread();
            if (taskAborter != null) {
                taskAborter.markGenerationStart();
                taskAborter.startTask(currentThread);
            }

            // STREAMING REAL - cada chunk se procesa conforme llega
            fileWriter.reset();
            StringBuilder fullResponse = new StringBuilder();
            CountDownLatch latch = new CountDownLatch(1);
            AtomicReference<Throwable> errorRef = new AtomicReference<>();

            client.prompt(fullMessage)
                .options(buildSpringAiOptions(effectiveThink))
                .stream()
                .chatResponse()
                .doOnNext(response -> {
                    if (taskAborter != null && taskAborter.isAborted()) {
                        return;
                    }
                    String content = null;
                    try {
                        if (response != null && response.getResult() != null &&
                            response.getResult().getOutput() != null) {
                            content = response.getResult().getOutput().getText();
                        }
                    } catch (Exception ignored) {}

                    if (content != null && !content.isEmpty()) {
                        fullResponse.append(content);
                        // Enviar directamente al chat (sin pasar por StreamingFileWriter por ahora)
                        onChunk.accept(content);
                    }
                })
                .doOnError(error -> {
                    errorRef.set(error);
                    latch.countDown();
                })
                .doOnComplete(() -> {
                    latch.countDown();
                })
                .subscribe();

            // Esperar a que termine el stream (máximo 10 minutos)
            latch.await(10, TimeUnit.MINUTES);

            if (errorRef.get() != null) {
                throw new RuntimeException(errorRef.get());
            }

            // Guardar en historial
            String finalResponse = fullResponse.toString();
            if (enableHistory && !finalResponse.isEmpty()) {
                conversationHistory.add(botName + ": " + finalResponse);
                trimHistory();
            }

            if (onComplete != null) {
                onComplete.run();
            }

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (onComplete != null) {
                onComplete.run();
            }
        } catch (Exception e) {
            ThinkContextHolder.clearAll();
            if (taskAborter != null && taskAborter.isAborted()) {
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }
            if (Thread.currentThread().isInterrupted()) {
                Thread.currentThread().interrupt();
                if (onComplete != null) {
                    onComplete.run();
                }
                return;
            }
            throw new ChatException("Error en streaming", e, classifyError(e), modelName);
        } finally {
            if (taskAborter != null) {
                taskAborter.unregisterTask(Thread.currentThread());
                taskAborter.markGenerationEnd();
            }
            ThinkContextHolder.clearAll();
        }
    }

    private ErrorType classifyError(Throwable e) {
        if (e == null) return ErrorType.UNKNOWN;

        String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
        String className = e.getClass().getName().toLowerCase();

        if (msg.contains("timeout") || msg.contains("timed out")) {
            return ErrorType.TIMEOUT;
        }
        if (msg.contains("connection") || msg.contains("connect") ||
            msg.contains("refused") || msg.contains("unreachable") ||
            className.contains("connectexception")) {
            return ErrorType.CONNECTION_ERROR;
        }
        if (msg.contains("stream") || className.contains("flux") ||
            className.contains("reactor")) {
            return ErrorType.STREAMING_ERROR;
        }
        if (e instanceof InterruptedException) {
            return ErrorType.ABORTED;
        }

        Throwable cause = e.getCause();
        if (cause != null && cause != e) {
            return classifyError(cause);
        }

        return ErrorType.UNKNOWN;
    }

    // Unico punto de llamada al LLM: todo pasa por el ChatClient de Spring AI.
    // El think viaja como reasoning_effort (nivel o "none" si va apagado) y el
    // trace se recupera de ThinkContextHolder (campos fuera del content).
    private ChatTurn callSpringAiOnce(ChatClient client, String fullMessage, String effectiveThink) {
        ThinkContextHolder.setThinkMode(effectiveThink);
        ThinkContextHolder.clearLastThinking();
        Thread currentThread = Thread.currentThread();
        if (taskAborter != null) {
            taskAborter.markGenerationStart();
            taskAborter.startTask(currentThread);
        }
        try {
            if (taskAborter != null && taskAborter.isAborted()) {
                return new ChatTurn("", null);
            }
            String response = client.prompt(fullMessage)
                    .options(buildSpringAiOptions(effectiveThink))
                    .call().content();
            if (taskAborter != null && taskAborter.isAborted()) {
                return new ChatTurn("", null);
            }
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
        } catch (Exception e) {
            if (taskAborter != null && taskAborter.isAborted()) {
                return new ChatTurn("", null);
            }
            throw e;
        } finally {
            if (taskAborter != null) {
                taskAborter.unregisterTask(currentThread);
                taskAborter.markGenerationEnd();
            }
            ThinkContextHolder.clearThinkMode();
        }
    }

    private record ChatTurn(String response, String thinking) {
    }

    public static ChatService withTools(String settingsPath, String botName) throws Exception {
        SettingsManager mgr = new SettingsManager();
        mgr.ensureAgentsMd();
        mgr.ensureExists(settingsPath);
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
        if (s.getModel().getBaseUrl() != null) {
            aborter.setOllamaBaseUrl(s.getModel().getBaseUrl());
        }
        ChatService service = new ChatService(() -> client, botName, aborter, historySize, enableHistory, thinkingEnabled, thinkingMode, modelName);
        service.setSettings(s);
        return service;
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

    public void setThinkingMode(String mode) {
        if (mode == null || mode.isBlank()) {
            this.thinkingMode = "low";
            return;
        }
        String lower = mode.toLowerCase().trim();
        this.thinkingMode = switch (lower) {
            case "high", "alto", "max" -> "high";
            case "medium", "medio", "med" -> "medium";
            case "low", "bajo", "min" -> "low";
            case "off", "none", "disabled" -> {
                this.thinkingEnabled = false;
                yield "none";
            }
            default -> "medium";
        };
        if (!lower.equals("off") && !lower.equals("none") && !lower.equals("disabled")) {
            this.thinkingEnabled = true;
        }
    }

    public String getThinkingMode() {
        return thinkingMode;
    }

    public void setModelName(String modelName) {
        this.modelName = modelName;
    }

    public String getModelName() {
        return modelName;
    }

    public void setSettings(Settings settings) {
        this.currentSettings = settings;
    }

    public Settings getSettings() {
        return currentSettings;
    }

    private ChatClient getActiveClient() {
        if (currentClient != null) {
            return currentClient;
        }
        ChatClient client = chatClientSupplier.get();
        if (client == null) {
            throw new IllegalStateException("Spring AI ChatClient is required");
        }
        return client;
    }

    public void rebuildClient(String newModelName) {
        if (currentSettings == null) {
            throw new IllegalStateException("Settings not available for client rebuild");
        }
        this.modelName = newModelName;
        currentSettings.getModel().setName(newModelName);
        String thinkMode = OllamaThinkSpringAiFactory.resolveThinkMode(currentSettings);
        this.thinkingEnabled = thinkMode != null;
        this.thinkingMode = thinkMode != null ? thinkMode : "low";
        try {
            this.currentClient = OllamaThinkSpringAiFactory.createChatClient(currentSettings, thinkingEnabled ? thinkingMode : null);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to rebuild ChatClient for model " + newModelName + ": " + e.getMessage(), e);
        }
    }

    public void clearHistory() {
        conversationHistory.clear();
        synchronized (queryResultsCache) {
            queryResultsCache.clear();
        }
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
        StringBuilder promptBuilder = new StringBuilder();

        synchronized (queryResultsCache) {
            if (!queryResultsCache.isEmpty()) {
                promptBuilder.append("===== CACHED TOOL RESULTS (from this session) =====\n");
                for (ToolResultNotifier.ToolQueryResult qr : queryResultsCache) {
                    promptBuilder.append("[").append(qr.toolName()).append("] Query: ").append(qr.query()).append("\n");
                    promptBuilder.append(qr.truncatedResult()).append("\n\n");
                }
                promptBuilder.append("===== END CACHED RESULTS =====\n\n");
            }
        }

        if (enableHistory && !conversationHistory.isEmpty()) {
            promptBuilder.append("===== CONVERSATION HISTORY =====\n");
            for (String message : conversationHistory) {
                promptBuilder.append(message).append("\n");
            }
            promptBuilder.append("===== END HISTORY =====\n\n");
        }

        promptBuilder.append(currentMessage);
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
