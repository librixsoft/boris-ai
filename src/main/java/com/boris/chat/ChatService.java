package com.boris.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;

import com.boris.settings.Settings;
import com.boris.settings.SettingsManager;
import com.boris.task.TaskAborter;
import com.boris.tooling.integration.ToolCallingConfig;

public class ChatService {

    public static final String EXIT_COMMAND = "EXIT";

    private final Supplier<ChatClient> chatClientSupplier;
    private final String botName;
    private final TaskAborter taskAborter;
    private final List<String> conversationHistory;
    private final int maxHistorySize;

    public ChatService(Supplier<ChatClient> chatClientSupplier, String botName, TaskAborter taskAborter, int maxHistorySize) {
        this.chatClientSupplier = chatClientSupplier;
        this.botName = botName;
        this.taskAborter = taskAborter;
        this.conversationHistory = new ArrayList<>();
        this.maxHistorySize = maxHistorySize;
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
            throw new IllegalStateException("Spring AI ChatClient is required — tool calling must be used. Use ChatService.withTools() to construct.");
        }

        try {
            // Construir prompt con historial incluido en el mensaje del usuario
            String fullMessage = buildPromptWithHistory(userMessage);
            
            // Agregar mensaje del usuario al historial
            conversationHistory.add("User: " + userMessage);
            trimHistory();
            
            String response = client.prompt(fullMessage).call().content();
            
            // Agregar respuesta al historial
            if (response != null && !response.isEmpty()) {
                conversationHistory.add(botName + ": " + response);
                trimHistory();
            }
            
            return "*%s* %s".formatted(botName, response != null ? response : "");
        } catch (Exception e) {
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
            throw new IllegalStateException("Spring AI ChatClient is required — tool calling must be used. Use ChatService.withTools() to construct.");
        }

        try {
            client.prompt(userMessage)
                .stream()
                .content()
                .doOnNext(chunk -> onChunk.accept(chunk))
                .doOnComplete(() -> {
                    if (onComplete != null) onComplete.run();
                })
                .doOnError(e -> {
                    if (!taskAborter.isAborted()) {
                        throw new com.boris.exceptions.BorisException("Chat error", e);
                    }
                })
                .subscribe();
        } catch (Exception e) {
            if (taskAborter.isAborted()) {
                return;
            }
            throw new com.boris.exceptions.BorisException("Chat error", e);
        }
    }

    public static ChatService withTools(String settingsPath, String botName) throws Exception {
        SettingsManager mgr = new SettingsManager();
        mgr.ensureAgentsMd();
        Settings s = mgr.loadSettings(settingsPath);
        if (s == null || s.getModel() == null) {
            throw new IllegalStateException("Settings file not found or invalid: " + settingsPath);
        }

        String prompt = ToolCallingConfig.loadSystemPrompt(s);
        var chatModel = buildChatModel(s);
        
        // Usar parámetros de configuración
        int historySize = s.getMaxHistorySize();
        
        // Crear ChatService con historial
        TaskAborter aborter = new TaskAborter();
        ChatService chatService = new ChatService(() -> null, botName, aborter, historySize);
        
        // Crear ChatClient con el system prompt y tools
        ChatClient client = ChatClient.builder(chatModel)
                .defaultSystem(prompt)
                .defaultTools(ToolCallingConfig.buildNativeToolCallbacks())
                .build();
        
        // Crear nuevo ChatService con el client real
        return new ChatService(() -> client, botName, aborter, historySize);
    }

    /** Expose the aborter so UI can wire ESC key to it. */
    public TaskAborter getTaskAborter() {
        return taskAborter;
    }

    /** Clear the chat history */
    public void clearHistory() {
        conversationHistory.clear();
    }

    /** Get the conversation history */
    public List<String> getConversationHistory() {
        return new ArrayList<>(conversationHistory);
    }

    /** Trim history to max size */
    private void trimHistory() {
        while (conversationHistory.size() > maxHistorySize) {
            conversationHistory.remove(0);
        }
    }

    /** Build prompt with conversation history */
    private String buildPromptWithHistory(String currentMessage) {
        if (conversationHistory.isEmpty()) {
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

    private static org.springframework.ai.chat.model.ChatModel buildChatModel(Settings settings) throws Exception {
        String baseUrl = settings.getModel().getBaseUrl();
        String modelName = settings.getModel().getName();
        Map<String, String> envMap = settings.getEnv();
        String apiKey = (String) envMap.getOrDefault("OLLAMA_API_KEY", "ollama");

        OpenAiApi openAiApi = new OpenAiApi.Builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        return org.springframework.ai.openai.OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model(modelName).build())
                .build();
    }
}
