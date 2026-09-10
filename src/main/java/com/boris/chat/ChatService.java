package com.boris.chat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonArray;

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
    private final boolean enableHistory;
    private boolean thinkingEnabled;
    private String thinkingMode;
    private final String modelName;
    private String lastThinkingContent;
    private static final Pattern THINKING_PATTERN = Pattern.compile("```(.*?)```", Pattern.DOTALL);
    private final String ollamaBaseUrl;
    private final Gson gson = new Gson();

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
        this.ollamaBaseUrl = ollamaBaseUrl;
        

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

        // Si está habilitado thinking y tenemos URL de Ollama, usar API directa
        if (thinkingEnabled && ollamaBaseUrl != null) {
            String response = sendMessageWithOllamaApi(userMessage);
            // Agregar info de thinking a la respuesta
            return response + "\n\n[DEBUG] Thinking: ENABLED (mode: %s, API: %s)".formatted(thinkingMode, ollamaBaseUrl);
        }

        ChatClient client = chatClientSupplier.get();
        if (client == null) {
            throw new IllegalStateException("Spring AI ChatClient is required — tool calling must be used. Use ChatService.withTools() to construct.");
        }

        try {
            // Construir prompt con historial incluido en el mensaje del usuario
            String fullMessage = buildPromptWithHistory(userMessage);
            
            // Agregar mensaje del usuario al historial (si está habilitado)
            if (enableHistory) {
                conversationHistory.add("User: " + userMessage);
                trimHistory();
            }
            
            String response = client.prompt(fullMessage)
                .options(OpenAiChatOptions.builder()
                        .model(this.modelName)
                        .reasoningEffort(thinkingEnabled ? "medium" : null)
                        .build())
                .call().content();
            
            // Procesar thinking si está habilitado
            String thinkingContent = null;
            String finalResponse = response;
            
            if (thinkingEnabled && response != null) {
                thinkingContent = extractThinkingContent(response);
                if (thinkingContent != null) {
                    finalResponse = extractFinalResponse(response);
                    this.lastThinkingContent = thinkingContent;
                }
            }
            
            // Agregar respuesta al historial (si está habilitado)
            if (enableHistory && finalResponse != null && !finalResponse.isEmpty()) {
                conversationHistory.add(botName + ": " + finalResponse);
                trimHistory();
            }
            
            // Si hay thinking, devolver formato con tags de Ollama granite4.2
            if (thinkingContent != null && !thinkingContent.isEmpty()) {
                return "*%s* ```\n%s\n```\n%s".formatted(botName, thinkingContent, finalResponse != null ? finalResponse : "");
            }
            
            return "*%s* %s".formatted(botName, finalResponse != null ? finalResponse : "");
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

        // Si está habilitado thinking y tenemos URL de Ollama, usar API directa con streaming simulado
        if (thinkingEnabled && ollamaBaseUrl != null) {
            sendMessageStreamWithOllamaApi(userMessage, onChunk, onComplete);
            return;
        }

        ChatClient client = chatClientSupplier.get();
        if (client == null) {
            throw new IllegalStateException("Spring AI ChatClient is required — tool calling must be used. Use ChatService.withTools() to construct.");
        }

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(this.modelName);
        if (thinkingEnabled) {
            optionsBuilder.reasoningEffort("medium");
        }

        try {
            // For streaming with thinking, we need to accumulate the full response first
            if (thinkingEnabled) {
                StringBuilder fullResponse = new StringBuilder();
                
                client.prompt(userMessage)
                    .options(optionsBuilder.build())
                    .stream()
                    .content()
                    .doOnNext(chunk -> {
                        fullResponse.append(chunk);
                        // Still send chunks for immediate feedback
                        onChunk.accept(chunk);
                    })
                    .doOnComplete(() -> {
                        // Process the complete response for thinking
                        String completeResponse = fullResponse.toString();
                        String thinkingContent = extractThinkingContent(completeResponse);
                        String finalResponse = extractFinalResponse(completeResponse);
                        
                        if (thinkingContent != null && !thinkingContent.isEmpty()) {
                            this.lastThinkingContent = thinkingContent;
                            // Send thinking content with XML tags that the renderer supports
                            onChunk.accept("\n<thinking>\n" + thinkingContent + "\n</thinking>\n" + finalResponse);
                        }
                        
                        if (onComplete != null) onComplete.run();
                    })
                    .doOnError(e -> {
                        if (!taskAborter.isAborted()) {
                            throw new com.boris.exceptions.BorisException("Chat error", e);
                        }
                    })
                    .subscribe();
            } else {
                // Regular streaming without thinking processing
                client.prompt(userMessage)
                    .options(optionsBuilder.build())
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
            }
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
        String modelName = s.getModel().getName();
        
        // Determinar si thinking está habilitado basado en el campo options.think
        boolean thinkingEnabled = false;
        String thinkingMode = "low";
        if (s.getOptions() != null && s.getOptions().containsKey("think")) {
            thinkingEnabled = true;
            Object thinkValue = s.getOptions().get("think");
            if (thinkValue instanceof String) {
                thinkingMode = (String) thinkValue;
            }
        }
        
        var chatModel = buildChatModel(s, thinkingEnabled, thinkingMode);
        
        // Usar parámetros de configuración
        int historySize = s.getMaxHistorySize();
        boolean enableHistory = s.getEnableHistory() != null ? s.getEnableHistory() : true;
        
        // Crear ChatService con historial
        TaskAborter aborter = new TaskAborter();
        ChatService chatService = new ChatService(() -> null, botName, aborter, historySize, enableHistory);
        
        // Crear ChatClient con el system prompt y tools
        ChatClient client = ChatClient.builder(chatModel)
                .defaultSystem(prompt)
                .defaultTools(ToolCallingConfig.buildNativeToolCallbacks())
                .build();
        
        // Crear nuevo ChatService con el client real
        String ollamaBaseUrl = s.getModel().getBaseUrl();
        return new ChatService(() -> client, botName, aborter, historySize, enableHistory, thinkingEnabled, thinkingMode, modelName, ollamaBaseUrl);
    }

    /** Expose the aborter so UI can wire ESC key to it. */
    public TaskAborter getTaskAborter() {
        return taskAborter;
    }

    /** Toggle thinking mode and rebuild the chat client. */
    public void setThinkingEnabled(boolean enabled) {
        this.thinkingEnabled = enabled;
    }

    public boolean isThinkingEnabled() {
        return thinkingEnabled;
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

    /** Extract thinking content from response */
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

    /** Extract final response without thinking block */
    private String extractFinalResponse(String response) {
        if (response == null) {
            return null;
        }
        
        // Remove thinking block from response
        return THINKING_PATTERN.matcher(response).replaceAll("").trim();
    }

    /** Get the last thinking content */
    public String getLastThinkingContent() {
        return lastThinkingContent;
    }

    /** Check if using Ollama API directly */
    public boolean usesOllamaApi() {
        return thinkingEnabled && ollamaBaseUrl != null;
    }

    /** Send message using Ollama REST API directly with think parameter (streaming simulated) */
    private void sendMessageStreamWithOllamaApi(String userMessage, Consumer<String> onChunk, Runnable onComplete) {
        try {
            this.lastThinkingContent = "[API STREAM] Starting Ollama API call...";
            
            // Construir prompt con historial
            String fullMessage = buildPromptWithHistory(userMessage);
            
            // Agregar mensaje del usuario al historial
            if (enableHistory) {
                conversationHistory.add("User: " + userMessage);
                trimHistory();
            }

            // Crear request para API de Ollama
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", modelName);
            requestBody.addProperty("stream", false); // Disable real streaming for simplicity
            
            JsonArray messages = new JsonArray();
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", fullMessage);
            messages.add(userMsg);
            requestBody.add("messages", messages);
            
            // Agregar opciones con parámetro think según documentación de Ollama
            JsonObject options = new JsonObject();
            if (thinkingMode != null && !thinkingMode.isEmpty()) {
                options.addProperty("think", thinkingMode);
            } else {
                options.addProperty("think", "low");
            }
            requestBody.add("options", options);
            
            // Guardar el request para depuración (no sobrescribir después)
            String requestJson = gson.toJson(requestBody);
            
            // Enviar request a Ollama (sin barra al final)
            String apiUrl = ollamaBaseUrl.endsWith("/") ? ollamaBaseUrl + "api/chat" : ollamaBaseUrl + "/api/chat";
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build();
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                this.lastThinkingContent = "[API ERROR] Status: " + response.statusCode();
                onChunk.accept("[ERROR] Ollama API error: " + response.statusCode());
                if (onComplete != null) onComplete.run();
                return;
            }
            
            // Procesar respuesta JSON de Ollama (puede tener múltiples mensajes en streaming)
            String fullThinking = "";
            String fullContent = "";
            
            // La respuesta puede ser un solo objeto JSON o streaming
            try {
                JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
                JsonObject message = responseJson.getAsJsonObject("message");
                
                if (message.has("thinking")) {
                    fullThinking = message.get("thinking").getAsString();
                }
                if (message.has("content")) {
                    fullContent = message.get("content").getAsString();
                }
            } catch (Exception e) {
                // Si falla, intenta procesar como streaming
                String[] lines = response.body().split("\n");
                for (String line : lines) {
                    if (line.trim().isEmpty()) continue;
                    try {
                        JsonObject msg = gson.fromJson(line, JsonObject.class);
                        JsonObject message = msg.getAsJsonObject("message");
                        if (message.has("thinking")) {
                            fullThinking += message.get("thinking").getAsString();
                        }
                        if (message.has("content")) {
                            fullContent += message.get("content").getAsString();
                        }
                    } catch (Exception ex) {
                        // Ignorar líneas que no son JSON válido
                    }
                }
            }
            
            // Guardar respuesta cruda para depuración
            this.lastThinkingContent = "[REQUEST] " + requestJson + " | [THINKING] " + fullThinking + " | [CONTENT] " + fullContent;
            
            // Procesar thinking (ahora viene en campo separado)
            String thinkingContent = fullThinking;
            String finalResponse = fullContent;
            
            if (thinkingContent != null && !thinkingContent.isEmpty()) {
                this.lastThinkingContent = "[THINKING] " + thinkingContent;
            }
            
            // Simular streaming enviando chunks
            if (finalResponse != null && !finalResponse.isEmpty()) {
                onChunk.accept(finalResponse);
            }
            
            // Agregar respuesta al historial
            if (enableHistory && finalResponse != null && !finalResponse.isEmpty()) {
                conversationHistory.add(botName + ": " + finalResponse);
                trimHistory();
            }
            
            if (onComplete != null) onComplete.run();
            
        } catch (Exception e) {
            this.lastThinkingContent = "[API EXCEPTION] " + e.getMessage();
            onChunk.accept("[ERROR] Ollama API chat error: " + e.getMessage());
            if (onComplete != null) onComplete.run();
        }
    }

    /** Send message using Ollama REST API directly with think parameter */
    private String sendMessageWithOllamaApi(String userMessage) {
        try {
            // Construir prompt con historial
            String fullMessage = buildPromptWithHistory(userMessage);
            
            // Agregar mensaje del usuario al historial
            if (enableHistory) {
                conversationHistory.add("User: " + userMessage);
                trimHistory();
            }

            // Crear request para API de Ollama
            JsonObject requestBody = new JsonObject();
            requestBody.addProperty("model", modelName);
            
            JsonArray messages = new JsonArray();
            JsonObject userMsg = new JsonObject();
            userMsg.addProperty("role", "user");
            userMsg.addProperty("content", fullMessage);
            messages.add(userMsg);
            requestBody.add("messages", messages);
            
            // Agregar opciones con parámetro think
            JsonObject options = new JsonObject();
            if (thinkingMode != null && !thinkingMode.isEmpty()) {
                options.addProperty("think", thinkingMode);
            } else {
                options.addProperty("think", "low"); // default
            }
            requestBody.add("options", options);
            
            // Guardar el request para depuración (no sobrescribir después)
            String requestJson = gson.toJson(requestBody);
            
            // Enviar request a Ollama (sin barra al final)
            String apiUrl = ollamaBaseUrl.endsWith("/") ? ollamaBaseUrl + "api/chat" : ollamaBaseUrl + "/api/chat";
            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apiUrl))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(gson.toJson(requestBody)))
                .build();
            
            this.lastThinkingContent = "[API CALL] Calling Ollama API...";
            
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            
            if (response.statusCode() != 200) {
                this.lastThinkingContent = "[API ERROR] Status: " + response.statusCode();
                return "*%s* [ERROR] Ollama API error: %s %s".formatted(botName, response.statusCode(), response.body());
            }
            
            JsonObject responseJson = gson.fromJson(response.body(), JsonObject.class);
            String content = responseJson.getAsJsonObject("message").get("content").getAsString();
            
            // Procesar thinking de la respuesta (campo separado)
            String thinkingContent = null;
            if (responseJson.getAsJsonObject("message").has("thinking")) {
                thinkingContent = responseJson.getAsJsonObject("message").get("thinking").getAsString();
            }
            String finalResponse = content;
            
            if (thinkingContent != null && !thinkingContent.isEmpty()) {
                this.lastThinkingContent = "[THINKING] " + thinkingContent;
            }
            
            // Agregar respuesta al historial
            if (enableHistory && finalResponse != null && !finalResponse.isEmpty()) {
                conversationHistory.add(botName + ": " + finalResponse);
                trimHistory();
            }
            
            // Si hay thinking, devolver formato especial
            if (thinkingContent != null && !thinkingContent.isEmpty()) {
                return "*%s* ```\n%s\n```\n%s".formatted(botName, thinkingContent, finalResponse != null ? finalResponse : "");
            } else {
                return "*%s* %s".formatted(botName, finalResponse != null ? finalResponse : "");
            }
            
        } catch (Exception e) {
            this.lastThinkingContent = "[API EXCEPTION] " + e.getMessage();
            if (taskAborter.isAborted()) {
                return null;
            }
            return "*%s* [ERROR] Ollama API chat error: %s".formatted(botName, e.getMessage());
        }
    }

    private static org.springframework.ai.chat.model.ChatModel buildChatModel(Settings settings) throws Exception {
        return buildChatModel(settings, true, "think");
    }

    private static org.springframework.ai.chat.model.ChatModel buildChatModel(Settings settings, boolean thinkingEnabled, String thinkingMode) throws Exception {
        String baseUrl = settings.getModel().getBaseUrl();
        String modelName = settings.getModel().getName();
        Map<String, String> envMap = settings.getEnv();
        String apiKey = (String) envMap.getOrDefault("OLLAMA_API_KEY", "ollama");

        OpenAiApi openAiApi = new OpenAiApi.Builder()
                .baseUrl(baseUrl)
                .apiKey(apiKey)
                .build();

        OpenAiChatOptions.Builder optionsBuilder = OpenAiChatOptions.builder()
                .model(modelName);

        if (settings.getTemperature() != null) {
            optionsBuilder.temperature(settings.getTemperature());
        }

        return org.springframework.ai.openai.OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(optionsBuilder.build())
                .build();
    }
}
