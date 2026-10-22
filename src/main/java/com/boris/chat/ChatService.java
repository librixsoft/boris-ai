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

import com.boris.edit.StreamingFileWriter;
import com.boris.exceptions.ChatException;
import com.boris.exceptions.ChatException.ErrorType;
import com.boris.llm.client.BorisLlmClient;
import com.boris.llm.client.ChatMessage;
import com.boris.llm.client.ChatRequest;
import com.boris.llm.think.OllamaThinkSpringAiFactory;
import com.boris.llm.think.ThinkContextHolder;
import com.boris.tooling.ToolResultNotifier;
import com.boris.tooling.DiffLineNotifier;
import com.boris.tooling.WriteArgsPreviewStreamer;
import com.boris.tooling.engine.ToolRegistry;
import com.boris.tooling.integration.ToolCallingConfig;
import com.boris.tooling.LearningLimiter;
import com.boris.settings.Settings;
import com.boris.settings.SettingsManager;
import com.boris.task.TaskAborter;
import com.boris.context.ProjectContext;
import com.boris.tooling.tool.WebSearchTool;

public class ChatService {

    public static final String EXIT_COMMAND = "EXIT";

    private final Supplier<BorisLlmClient> llmClientSupplier;
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
    private volatile BorisLlmClient currentClient;
    private volatile Settings currentSettings;
    private volatile ToolRegistry toolRegistry;
    private volatile String systemPrompt;
    private final StreamingFileWriter fileWriter;
    private static final Pattern THINKING_PATTERN = Pattern.compile("(?is)<(think|thinking|thought|reasoning)>(.*?)</\\1>");
    private static final Pattern ORPHAN_THINK_TAGS = Pattern.compile("(?i)</?(?:think|thinking|thought|reasoning)>");
    private static final Pattern PROJECT_PATH_PATTERN = Pattern.compile("^\\s*(/[^\\s]+|~/[^\\s]+)\\s+(.+)$", Pattern.DOTALL);
    private static final Pattern INLINE_PATH_PATTERN = Pattern.compile("(?:en|in|to|at|dentro de|path|ruta|directorio|carpeta|folder)\\s+(/[^\\s\"']+|~/[^\\s\"']+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_ABSOLUTE_PATH_PATTERN = Pattern.compile("(?<![\\w:/~.-])(~?/[^\\s\"'`,;]+)");
    private static final List<String> FORBIDDEN_PROJECT_ROOTS = List.of(
            "/etc", "/usr", "/bin", "/sbin", "/lib", "/lib64", "/boot", "/dev", "/proc", "/sys",
            "/System", "/Library", "/Applications", "/private", "/var", "/root");
    private volatile String currentProjectPath;

    public ChatService(Supplier<BorisLlmClient> llmClientSupplier, String botName, TaskAborter taskAborter, int maxHistorySize, boolean enableHistory) {
        this(llmClientSupplier, botName, taskAborter, maxHistorySize, enableHistory, true, "think", "");
    }

    public ChatService(Supplier<BorisLlmClient> llmClientSupplier, String botName, TaskAborter taskAborter, int maxHistorySize, boolean enableHistory, boolean thinkingEnabled, String thinkingMode) {
        this(llmClientSupplier, botName, taskAborter, maxHistorySize, enableHistory, thinkingEnabled, thinkingMode, "");
    }

    public ChatService(Supplier<BorisLlmClient> llmClientSupplier, String botName, TaskAborter taskAborter, int maxHistorySize, boolean enableHistory, boolean thinkingEnabled, String thinkingMode, String modelName) {
        this(llmClientSupplier, botName, taskAborter, maxHistorySize, enableHistory, thinkingEnabled, thinkingMode, modelName, null);
    }

    public ChatService(Supplier<BorisLlmClient> llmClientSupplier, String botName, TaskAborter taskAborter, int maxHistorySize, boolean enableHistory, boolean thinkingEnabled, String thinkingMode, String modelName, String ollamaBaseUrl) {
        this.llmClientSupplier = llmClientSupplier;
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
        
        // Reset learning limits for new user message
        LearningLimiter.reset();
        
        if (taskAborter != null && taskAborter.isAborted()) {
            return null;
        }
        String lower = userMessage.toLowerCase().trim();
        if ("q".equals(lower) || "exit".equals(lower)) {
            return EXIT_COMMAND;
        }

        String[] extracted = extractProjectPath(userMessage);
        String cleanMessage = extracted[0];
        if (extracted[1] != null) {
            this.currentProjectPath = extracted[1];
            ProjectContext.setProjectPath(extracted[1]);
        }
        BorisLlmClient client = getActiveClient();
        try {
            String fullMessage = buildPromptWithHistory(cleanMessage);
            if (enableHistory) {
                conversationHistory.add("User: " + userMessage);
                trimHistory();
            }
            String effectiveThink = thinkingEnabled ? resolveThinkMode() : null;
            ChatTurn turn = callLlmOnce(client, fullMessage, effectiveThink);
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
            if (taskAborter != null && taskAborter.isAborted()) {
                return null;
            }
            throw new ChatException("Error en chat", e, classifyError(e), modelName);
        }
    }

    public void sendMessageStream(String userMessage, Consumer<String> onChunk, Runnable onComplete) {
        if (userMessage == null || userMessage.trim().isEmpty()) {
            throw new ChatException("El mensaje no puede estar vacío", ErrorType.INVALID_INPUT);
        }
        if (taskAborter != null && taskAborter.isAborted()) {
            return;
        }
        String lower = userMessage.toLowerCase().trim();
        if ("q".equals(lower) || "exit".equals(lower)) {
            return;
        }
        WebSearchTool.resetCallCount();

        String[] extracted = extractProjectPath(userMessage);
        String cleanMessage = extracted[0];
        if (extracted[1] != null) {
            this.currentProjectPath = extracted[1];
            ProjectContext.setProjectPath(extracted[1]);
        }
        BorisLlmClient client = getActiveClient();
        String effectiveThink = thinkingEnabled ? resolveThinkMode() : null;

        try {
            String fullMessage = buildPromptWithHistory(cleanMessage);
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

            fileWriter.reset();
            StringBuilder fullResponse = new StringBuilder();

            List<ChatMessage> conv = new ArrayList<>();
            for (String hist : conversationHistory) {
                if (hist.startsWith("User: ")) {
                    conv.add(ChatMessage.user(hist.substring(6)));
                } else if (hist.startsWith(botName + ": ")) {
                    conv.add(ChatMessage.assistant(hist.substring(botName.length() + 2)));
                }
            }
            conv.add(ChatMessage.user(fullMessage));

            Double temp = currentSettings != null ? currentSettings.getTemperature() : 0.7;
            String sysPrompt = this.systemPrompt != null ? this.systemPrompt :
                    (currentSettings != null ? ToolCallingConfig.loadSystemPrompt(currentSettings) : "");

            ToolRegistry registry = this.toolRegistry != null ? this.toolRegistry :
                    (currentSettings != null ? ToolCallingConfig.buildToolRegistry(currentSettings) : null);

            fileWriter.setSaveFunction((blockPath, blockContent, shownLines) ->
                    saveBlockViaWriteFileTool(registry, blockPath, blockContent, shownLines));

            BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                    conv,
                    modelName,
                    sysPrompt,
                    registry,
                    effectiveThink,
                    temp,
                    chunk -> {
                        if (taskAborter != null && taskAborter.isAborted()) {
                            return;
                        }
                        if (chunk != null && !chunk.isEmpty()) {
                            if (isThinkingChunk(chunk)) {
                                if (onChunk != null) {
                                    onChunk.accept(chunk);
                                }
                            } else {
                                fullResponse.append(chunk);
                                String chatText = fileWriter.processChunk(chunk);
                                if (chatText != null && !chatText.isEmpty() && onChunk != null) {
                                    onChunk.accept(chatText);
                                }
                            }
                        }
                    },
                    () -> taskAborter != null && taskAborter.isAborted()
            );

            fileWriter.finish();

            String finalResponse = fullResponse.length() > 0 ? fullResponse.toString() : (turn != null ? turn.response() : "");
            if (enableHistory && finalResponse != null && !finalResponse.isEmpty()) {
                conversationHistory.add(botName + ": " + extractFinalResponse(finalResponse));
                trimHistory();
            }

            if (turn != null && turn.thinking() != null && !turn.thinking().isBlank()) {
                this.lastThinkingContent = turn.thinking();
                ThinkContextHolder.setLastThinking(turn.thinking());
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

    private void saveBlockViaWriteFileTool(ToolRegistry registry, String blockPath, String blockContent, int shownLines) {
        if (registry == null || !registry.hasTool("write_file")) {
            throw new IllegalStateException("saveBlockViaWriteFileTool requiere la tool write_file registrada");
        }
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            String json = mapper.writeValueAsString(java.util.Map.of(
                    "path", blockPath != null ? blockPath : "",
                    "content", blockContent != null ? blockContent : ""));

            WriteArgsPreviewStreamer.setLivePreview(new WriteArgsPreviewStreamer.PreviewInfo(blockPath, shownLines));
            String result;
            try {
                result = registry.execute("write_file", json);
            } finally {
                WriteArgsPreviewStreamer.clearLivePreview();
            }

            if (result != null && result.contains("\"success\":false")) {
                String message = extractJsonProperty(result, "error");
                DiffLineNotifier.notify("⚠ write_file no guardo " + blockPath + (message != null ? ": " + message : ""));
            }
        } catch (Exception e) {
            DiffLineNotifier.notify("⚠ Error guardando " + blockPath + ": " + e.getMessage());
        }
    }

    private String extractJsonProperty(String json, String property) {
        try {
            var node = new com.fasterxml.jackson.databind.ObjectMapper().readTree(json);
            var value = node.get(property);
            return value != null && !value.isNull() ? value.asText() : null;
        } catch (Exception e) {
            return null;
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

    private ChatTurn callLlmOnce(BorisLlmClient client, String fullMessage, String effectiveThink) throws Exception {
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

            List<ChatMessage> conv = new ArrayList<>();
            for (String hist : conversationHistory) {
                if (hist.startsWith("User: ")) {
                    conv.add(ChatMessage.user(hist.substring(6)));
                } else if (hist.startsWith(botName + ": ")) {
                    conv.add(ChatMessage.assistant(hist.substring(botName.length() + 2)));
                }
            }
            conv.add(ChatMessage.user(fullMessage));

            Double temp = currentSettings != null ? currentSettings.getTemperature() : 0.7;
            String sysPrompt = this.systemPrompt != null ? this.systemPrompt :
                    (currentSettings != null ? ToolCallingConfig.loadSystemPrompt(currentSettings) : "");

            ToolRegistry registry = this.toolRegistry != null ? this.toolRegistry :
                    (currentSettings != null ? ToolCallingConfig.buildToolRegistry(currentSettings) : null);

            BorisLlmClient.ChatTurn turn = client.executeChatWithTools(
                    conv,
                    modelName,
                    sysPrompt,
                    registry,
                    effectiveThink,
                    temp,
                    () -> taskAborter != null && taskAborter.isAborted()
            );

            if (taskAborter != null && taskAborter.isAborted()) {
                return new ChatTurn("", null);
            }

            String response = turn.response();
            String thinkingContent = turn.thinking();
            if ((thinkingContent == null || thinkingContent.isBlank()) && thinkingEnabled) {
                thinkingContent = extractThinkingContent(response);
            }
            String finalResponse = response;
            if (thinkingContent != null && !thinkingContent.isBlank() && response != null && THINKING_PATTERN.matcher(response).find()) {
                finalResponse = extractFinalResponse(response);
            }
            if (thinkingContent != null && !thinkingContent.isBlank()) {
                this.lastThinkingContent = thinkingContent;
                ThinkContextHolder.setLastThinking(thinkingContent);
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

    public record ChatTurn(String response, String thinking) {}

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
        BorisLlmClient client = OllamaThinkSpringAiFactory.createClient(s);
        int historySize = s.getMaxHistorySize();
        boolean enableHistory = s.getEnableHistory() != null ? s.getEnableHistory() : true;
        TaskAborter aborter = new TaskAborter();
        if (s.getModel().getBaseUrl() != null) {
            aborter.setOllamaBaseUrl(s.getModel().getBaseUrl());
        }
        ToolRegistry registry = ToolCallingConfig.buildToolRegistry(s);
        String prompt = ToolCallingConfig.loadSystemPrompt(s);

        ChatService service = new ChatService(() -> client, botName, aborter, historySize, enableHistory, thinkingEnabled, thinkingMode, modelName);
        service.setSettings(s);
        service.setToolRegistry(registry);
        service.setSystemPrompt(prompt);
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

    public void setToolRegistry(ToolRegistry toolRegistry) {
        this.toolRegistry = toolRegistry;
    }

    public ToolRegistry getToolRegistry() {
        return toolRegistry;
    }

    public void setSystemPrompt(String systemPrompt) {
        this.systemPrompt = systemPrompt;
    }

    public String getSystemPrompt() {
        return systemPrompt;
    }

    private BorisLlmClient getActiveClient() {
        if (currentClient != null) {
            return currentClient;
        }
        BorisLlmClient client = llmClientSupplier.get();
        if (client == null) {
            throw new IllegalStateException("BorisLlmClient is required");
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
            this.currentClient = OllamaThinkSpringAiFactory.createClient(currentSettings);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to rebuild BorisLlmClient for model " + newModelName + ": " + e.getMessage(), e);
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

        if (currentProjectPath != null && !currentProjectPath.isBlank()) {
            promptBuilder.append("===== PROJECT CONTEXT =====\n");
            promptBuilder.append("WORKING DIRECTORY: ").append(currentProjectPath).append("\n");
            promptBuilder.append("BASE PATH: \"").append(currentProjectPath).append("\"\n");
            promptBuilder.append("===== END PROJECT CONTEXT =====\n\n");
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
            return matcher.group(2).trim();
        }
        return null;
    }

    private String extractFinalResponse(String response) {
        if (response == null) {
            return null;
        }
        String clean = THINKING_PATTERN.matcher(response).replaceAll("").trim();
        return ORPHAN_THINK_TAGS.matcher(clean).replaceAll("").trim();
    }

    private boolean isThinkingChunk(String chunk) {
        return chunk != null && chunk.contains("<thinking>");
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

    private String resolveThinkMode() {
        if (thinkingMode != null && !thinkingMode.isBlank()) {
            return thinkingMode;
        }
        return "low";
    }

    private String[] extractProjectPath(String message) {
        if (message == null) return new String[]{message, null};

        Matcher m = PROJECT_PATH_PATTERN.matcher(message);
        if (m.matches()) {
            String path = resolveAndValidatePath(m.group(1));
            if (path != null) {
                return new String[]{m.group(2).trim(), path};
            }
        }

        Matcher inlineMatcher = INLINE_PATH_PATTERN.matcher(message);
        if (inlineMatcher.find()) {
            String path = resolveAndValidatePath(inlineMatcher.group(1));
            if (path != null) {
                return new String[]{message, path};
            }
        }

        String detected = detectProjectPathFromMessage(message);
        if (detected != null) {
            return new String[]{message, detected};
        }

        return new String[]{message, null};
    }

    private String detectProjectPathFromMessage(String message) {
        List<String> candidates = new ArrayList<>();
        Matcher matcher = ANY_ABSOLUTE_PATH_PATTERN.matcher(message);
        while (matcher.find()) {
            String candidate = stripTrailingPunctuation(matcher.group(1));
            if (!candidate.isBlank() && !isForbiddenProjectRoot(candidate)) {
                candidates.add(candidate);
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }

        for (String candidate : candidates) {
            java.io.File f = new java.io.File(expandHome(candidate));
            if (f.isDirectory()) {
                return f.getAbsolutePath();
            }
        }

        for (String candidate : candidates) {
            java.io.File f = new java.io.File(expandHome(candidate));
            if (f.isFile()) {
                java.io.File parent = f.getParentFile();
                if (parent != null && parent.isDirectory()) {
                    return parent.getAbsolutePath();
                }
            }
        }

        for (String candidate : candidates) {
            try {
                String resolved = resolveAndValidatePath(candidate);
                if (resolved != null) {
                    return resolved;
                }
            } catch (IllegalStateException e) {
                continue;
            }
        }
        return null;
    }

    private static String expandHome(String path) {
        if (path.startsWith("~/")) {
            return System.getProperty("user.home") + path.substring(1);
        }
        if ("~".equals(path)) {
            return System.getProperty("user.home");
        }
        return path;
    }

    private static String stripTrailingPunctuation(String path) {
        int end = path.length();
        while (end > 0) {
            char c = path.charAt(end - 1);
            if (".,;:!?".indexOf(c) >= 0) {
                end--;
                continue;
            }
            if (c == ')' || c == ']' || c == '}') {
                char open = c == ')' ? '(' : (c == ']' ? '[' : '{');
                int opens = 0;
                int closes = 0;
                for (int i = 0; i < end; i++) {
                    if (path.charAt(i) == open) opens++;
                    if (path.charAt(i) == c) closes++;
                }
                if (closes > opens) {
                    end--;
                    continue;
                }
            }
            break;
        }
        return path.substring(0, end);
    }

    private static boolean isForbiddenProjectRoot(String path) {
        String normalized = expandHome(path);
        if (normalized.startsWith("/") && normalized.split("/").length < 3) {
            return true;
        }
        for (String forbidden : FORBIDDEN_PROJECT_ROOTS) {
            if (normalized.equals(forbidden) || normalized.startsWith(forbidden + "/")) {
                return true;
            }
        }
        return false;
    }

    private String resolveAndValidatePath(String path) {
        if (path == null || path.isBlank()) return null;
        String resolved = expandHome(path.trim());
        java.io.File f = new java.io.File(resolved);
        if (f.exists() && f.isDirectory()) {
            return f.getAbsolutePath();
        }
        if (f.exists() && f.isFile()) {
            java.io.File parent = f.getParentFile();
            return parent != null && parent.exists() ? parent.getAbsolutePath() : null;
        }
        if (!f.exists()) {
            String lastSegment = f.getName();
            boolean looksLikeFile = lastSegment.contains(".");
            if (looksLikeFile) {
                java.io.File parent = f.getParentFile();
                if (parent == null) return null;
                if (!parent.mkdirs() && !parent.isDirectory()) {
                    throw new IllegalStateException("No se pudo crear la carpeta del proyecto: " + parent.getAbsolutePath());
                }
                return parent.getAbsolutePath();
            }
            if (!f.mkdirs() && !f.isDirectory()) {
                throw new IllegalStateException("No se pudo crear la carpeta del proyecto: " + f.getAbsolutePath());
            }
            return f.getAbsolutePath();
        }
        return null;
    }

    public String getCurrentProjectPath() {
        return currentProjectPath;
    }

    public void setCurrentProjectPath(String path) {
        this.currentProjectPath = path;
        ProjectContext.setProjectPath(path);
    }
}
