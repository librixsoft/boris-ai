package com.boris.librixsoft.server.service;

import com.boris.librixsoft.agent.tools.*;
import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.server.dto.TokenInfo;
import com.boris.librixsoft.server.service.llama.BorisLLamaServerWrapper;
import com.boris.librixsoft.server.service.llama.JnaLlamaChatModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlamaChatService {

    private final JnaLlamaChatModel jnaLlamaChatModel;
    private final BorisProperties borisProperties;
    private final BorisLLamaServerWrapper llamaServerWrapper;
    private final CreateFileTool createFileTool;
    private final ReadFileTool readFileTool;
    private final EditFileTool editFileTool;
    private final EditFileWithReplacementTool editFileWithReplacementTool;
    private final DeleteFileTool deleteFileTool;
    private final DeleteFolderTool deleteFolderTool;
    private final RunCommandTool runCommandTool;
    private String activeModelId = null;

    private static final String ANSI_RESET  = "\u001B[0m";
    private static final String ANSI_CYAN   = "\u001B[96m";
    private static final String ANSI_YELLOW = "\u001B[93m";
    private static final String ANSI_GREEN  = "\u001B[92m";

    // ─── Inferencia central usando JNA ─────────────────────────────────────────

    public String executePrompt(String modelName, String systemPrompt, String userPrompt,
                               Double temperature, AtomicBoolean cancellationRequested) {
        return executePrompt(modelName, systemPrompt, userPrompt, temperature, cancellationRequested, null);
    }

    public String executePrompt(String modelName, String systemPrompt, String userPrompt,
                               Double temperature, AtomicBoolean cancellationRequested,
                               List<Message> history) {
        return executePrompt(modelName, systemPrompt, userPrompt, temperature, cancellationRequested, history, null);
    }

    public Flux<org.springframework.ai.chat.model.ChatResponse> streamPrompt(String modelName, String systemPrompt, String userPrompt,
                                Double temperature, AtomicBoolean cancellationRequested,
                                List<Message> history, Integer maxTokens) {
        if (cancellationRequested != null && cancellationRequested.get()) {
            return Flux.error(new RuntimeException("Task cancelled by user"));
        }

        // Build message list for JNA model
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }
        messages.add(new UserMessage(userPrompt));
        
        var optionsBuilder = org.springframework.ai.chat.prompt.ChatOptions.builder();
        if (maxTokens != null) optionsBuilder.maxTokens(maxTokens);
        if (temperature != null) optionsBuilder.temperature(temperature);
        
        org.springframework.ai.chat.prompt.ChatOptions options = optionsBuilder.build();
        org.springframework.ai.chat.prompt.Prompt prompt = options != null ? new org.springframework.ai.chat.prompt.Prompt(messages, options) : new org.springframework.ai.chat.prompt.Prompt(messages);

        return jnaLlamaChatModel.stream(prompt);
    }

    public String executePrompt(String modelName, String systemPrompt, String userPrompt,
                               Double temperature, AtomicBoolean cancellationRequested,
                               List<Message> history, Integer maxTokens) {
        if (cancellationRequested != null && cancellationRequested.get()) {
            throw new RuntimeException("Task cancelled by user");
        }

        // Build message list for JNA model
        List<Message> messages = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            messages.add(new SystemMessage(systemPrompt));
        }
        if (history != null && !history.isEmpty()) {
            messages.addAll(history);
        }
        messages.add(new UserMessage(userPrompt));
        
        var optionsBuilder = org.springframework.ai.chat.prompt.ChatOptions.builder();
        if (maxTokens != null) optionsBuilder.maxTokens(maxTokens);
        if (temperature != null) optionsBuilder.temperature(temperature);
        
        ChatOptions options = optionsBuilder.build();

        Prompt prompt = options != null ? new Prompt(messages, options) : new Prompt(messages);

        try {
            var response = jnaLlamaChatModel.call(prompt);
            TokenInfo tokenInfo = jnaLlamaChatModel.getLastTokenInfo();
            log.info("📊 [TOKENS] Input: {}, Output: {}, Context: {}, Remaining: {}",
                    tokenInfo.getInputTokens(), tokenInfo.getOutputTokens(),
                    tokenInfo.getContextSize(), tokenInfo.getRemainingTokens());
            return response.getResult().getOutput().getText();
        } catch (Exception e) {
            log.error("JNA inference error for model {}: {}", modelName, e.getMessage(), e);
            throw new RuntimeException("Inference failed: " + e.getMessage(), e);
        }
    }

    public String executePromptWithTools(String modelName, String systemPrompt, String userPrompt,
                                        Double temperature, Object tools, AtomicBoolean cancellationRequested) {
        return executePromptWithTools(modelName, systemPrompt, userPrompt, temperature, tools, cancellationRequested, null);
    }

    public String executePromptWithTools(String modelName, String systemPrompt, String userPrompt,
                                        Double temperature, Object tools, AtomicBoolean cancellationRequested,
                                        Integer maxTokens) {
        return executePromptWithTools(modelName, systemPrompt, userPrompt, temperature, tools, cancellationRequested, maxTokens, null);
    }

    public String executePromptWithTools(String modelName, String systemPrompt, String userPrompt,
                                        Double temperature, Object tools, AtomicBoolean cancellationRequested,
                                        Integer maxTokens, List<Message> history) {
        
        List<Message> localHistory = history != null ? new ArrayList<>(history) : new ArrayList<>();
        String currentInput = userPrompt;
        String lastResponse = "";
        
        int turn = 0;
        int maxTurns = 8; // Permitir flujos complejos (read -> write -> verify -> fix)
        
        while (turn < maxTurns) {
            turn++;
            String response = executePrompt(modelName, systemPrompt, currentInput, temperature, cancellationRequested, localHistory, maxTokens);
            
            if (response == null || response.isBlank()) break;
            lastResponse = response;

            String toolResults = executeNativeTools(response);
            if (!toolResults.equals(response)) {
                    log.info("🛠️ [JNA TOOL CALL] Turno {}: Herramientas ejecutadas en {}", turn, modelName);
                    // Guardamos el intercambio en la historia local para el siguiente turno
                    localHistory.add(new UserMessage(currentInput));
                    localHistory.add(new AssistantMessage(response));

                    // El resultado de la herramienta es la entrada del próximo turno
                    currentInput = toolResults;
                    continue;
                }
            
            // Si no hay más llamadas a herramientas o no se ejecutaron, salimos
            break;
        }
        
        return lastResponse;
    }

    public TokenInfo getTokenInfo() {
        return jnaLlamaChatModel.getLastTokenInfo();
    }

    private String executeNativeTools(String response) {
        String json = extractJsonFromResponse(response);
        if (json == null || json.isBlank()) {
            // Fallback: intentar parseo legacy de texto plano
            return executeLegacyNativeTools(response);
        }

        ObjectMapper mapper = new ObjectMapper();
        StringBuilder toolResults = new StringBuilder();
        boolean found = false;

        try {
            JsonNode root = mapper.readTree(json);
            JsonNode actions = root.path("actions");
            if (actions.isArray()) {
                for (JsonNode action : actions) {
                    String toolName = action.path("tool").asText("");
                    JsonNode args = action.path("args");

                    String result = "";
                    try {
                        switch (toolName) {
                            case "createFile" -> {
                                String path = args.path("path").asText("");
                                String content = args.path("content").asText("");
                                if (!path.isBlank()) result = createFileTool.call(path, content);
                            }
                            case "readFile" -> {
                                String path = args.path("path").asText("");
                                if (!path.isBlank()) result = readFileTool.call(path);
                            }
                            case "editFile" -> {
                                String path = args.path("path").asText("");
                                String newContent = args.path("newContent").asText("");
                                if (!path.isBlank()) result = editFileTool.call(path, newContent);
                            }
                            case "editFileWithReplacement" -> {
                                String path = args.path("path").asText("");
                                String oldContent = args.path("oldContent").asText("");
                                String newContent = args.path("newContent").asText("");
                                if (!path.isBlank()) result = editFileWithReplacementTool.call(path, oldContent, newContent);
                            }
                            case "deleteFile" -> {
                                String path = args.path("path").asText("");
                                if (!path.isBlank()) result = deleteFileTool.call(path);
                            }
                            case "deleteFolder" -> {
                                String path = args.path("path").asText("");
                                if (!path.isBlank()) result = deleteFolderTool.call(path);
                            }
                            case "runCommand" -> {
                                String command = args.path("command").asText("");
                                String workingDir = args.path("workingDirectory").asText("");
                                if (!command.isBlank()) result = runCommandTool.call(command, workingDir);
                            }
                            default -> {
                                log.warn("⚠️ [TOOL SKIP] Herramienta desconocida: {}", toolName);
                                continue;
                            }
                        }
                        found = true;
                    } catch (Exception e) {
                        result = "Error ejecutando herramienta " + toolName + ": " + e.getMessage();
                        found = true;
                    }

                    log.info("⚙️ [TOOL EXECUTION] {}: {}", toolName, result);
                    toolResults.append("[tool:").append(toolName).append("] Result: ").append(result).append("\n");
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ [TOOL PARSE] Fallo al parsear JSON, intentando legacy: {}", e.getMessage());
            return executeLegacyNativeTools(response);
        }

        return found ? toolResults.toString() : response;
    }

    private String executeLegacyNativeTools(String response) {
        // Regex robusto para capturar llamadas: toolName("arg1", "arg2") o toolName(arg1, arg2)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?:print\\s*\\()?\\s*(\\w+)\\s*\\((.*?)\\)\\s*\\)?", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(response);

        StringBuilder toolResults = new StringBuilder();
        boolean found = false;

        while (matcher.find()) {
            String toolName = matcher.group(1);
            String argsStr = matcher.group(2);

            List<String> args = parseLegacyArgs(argsStr);

            if (args.isEmpty() && !argsStr.isBlank()) {
                for (String s : argsStr.split(",")) {
                    args.add(s.trim().replaceAll("^'|'$|^\"|\"$", ""));
                }
            }

            String result = "";
            try {
                switch (toolName) {
                    case "createFile" -> {
                        if (args.size() >= 2) result = createFileTool.call(args.get(0), args.get(1));
                    }
                    case "readFile" -> {
                        if (args.size() >= 1) result = readFileTool.call(args.get(0));
                    }
                    case "editFile" -> {
                        if (args.size() >= 2) result = editFileTool.call(args.get(0), args.get(1));
                    }
                    case "editFileWithReplacement" -> {
                        if (args.size() >= 3) result = editFileWithReplacementTool.call(args.get(0), args.get(1), args.get(2));
                    }
                    case "deleteFile" -> {
                        if (args.size() >= 1) result = deleteFileTool.call(args.get(0));
                    }
                    case "deleteFolder" -> {
                        if (args.size() >= 1) result = deleteFolderTool.call(args.get(0));
                    }
                    case "runCommand" -> {
                        if (args.size() >= 2) result = runCommandTool.call(args.get(0), args.get(1));
                    }
                    default -> {
                        continue;
                    }
                }
                found = true;
            } catch (Exception e) {
                result = "Error ejecutando herramienta " + toolName + ": " + e.getMessage();
                found = true;
            }

            log.info("⚙️ [TOOL EXECUTION] {}: {}", toolName, result);
            toolResults.append("[tool:").append(toolName).append("] Result: ").append(result).append("\n");
        }

        return found ? toolResults.toString() : response;
    }

    private List<String> parseLegacyArgs(String argsStr) {
        List<String> args = new ArrayList<>();
        ObjectMapper mapper = new ObjectMapper();
        Matcher m = Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(argsStr);
        while (m.find()) {
            try {
                // Jackson desescapa todo correctamente
                args.add(mapper.readValue("\"" + m.group(1) + "\"", String.class));
            } catch (Exception e) {
                args.add(m.group(1)); // fallback sin desescapar
            }
        }
        return args;
    }

    // ─── Config resolution ─────────────────────────────────────────────────────

    public BorisProperties.ModelConfig resolveConfig(String modelId, int indexFallback) {
        String targetId = (modelId == null || modelId.isBlank()) ? llamaServerWrapper.getActiveModelId() : modelId;

        if (targetId != null && !targetId.isBlank()) {
            BorisProperties.ModelConfig cfg = llamaServerWrapper.getModelConfig(targetId);
            if (cfg != null) return cfg;

            // Dynamic fallback for models loaded via UI that are not in application.yml
            BorisProperties.ModelConfig dynamicCfg = new BorisProperties.ModelConfig();
            dynamicCfg.setId(targetId);
            dynamicCfg.setName(targetId);
            return dynamicCfg;
        }

        return resolveConfigByIndex(indexFallback);
    }


    private BorisProperties.ModelConfig resolveConfigByIndex(int index) {
        List<BorisProperties.ModelConfig> preloadModels = borisProperties.getPreloadModels();
        if (preloadModels == null || preloadModels.size() <= index) {
            if (preloadModels != null && !preloadModels.isEmpty()) return preloadModels.get(0);
            BorisProperties.ModelConfig fallback = new BorisProperties.ModelConfig();
            fallback.setId("default-model");
            fallback.setName("default-model.gguf");
            return fallback;
        }
        return preloadModels.get(index);
    }

    // ─── Model lifecycle ───────────────────────────────────────────────────────

    public <T> T loadModelWithParams(BorisProperties.ModelConfig config, Supplier<T> action) {
        if (config == null || config.getId() == null) {
            log.warn("Executing without model config (invalid or null)");
            return action.get();
        }

        // --- OPTIMIZATION: Skip reload if already active ---
        if (config.getId().equals(activeModelId)) {
            log.info("♻️ Model {} already active - skipping native reload", activeModelId);
            return action.get();
        }

        try {
            String paramsStr = String.format(
                "%sid%s=%s%s%s, %sthreads%s=%s%s%s, %sgpu%s=%s%s%s, %sbatch%s=%s%s%s, %stemp%s=%s%s%s, %smaxtok%s=%s%s%s",
                ANSI_CYAN, ANSI_RESET, ANSI_GREEN, config.getContextSize()  != null ? config.getContextSize()  : "default", ANSI_RESET,
                ANSI_CYAN, ANSI_RESET, ANSI_GREEN, config.getThreads()      != null ? config.getThreads()      : "default", ANSI_RESET,
                ANSI_CYAN, ANSI_RESET, ANSI_GREEN, config.getGpuLayers()    != null ? config.getGpuLayers()    : "default", ANSI_RESET,
                ANSI_CYAN, ANSI_RESET, ANSI_GREEN, config.getBatchSize()    != null ? config.getBatchSize()    : "default", ANSI_RESET,
                ANSI_CYAN, ANSI_RESET, ANSI_GREEN, config.getTemperature()  != null ? config.getTemperature()  : "default", ANSI_RESET,
                ANSI_CYAN, ANSI_RESET, ANSI_GREEN, config.getMaxTokens()    != null ? config.getMaxTokens()    : "default", ANSI_RESET
            );
            System.out.println("📥 " + ANSI_YELLOW + "Cargando modelo " + config.getName() + ANSI_RESET + " con params: " + paramsStr);

            llamaServerWrapper.loadModelWithParams(
                config.getId(),
                config.getName(),
                config.getContextSize(),
                config.getThreads(),
                config.getGpuLayers(),
                config.getBatchSize(),
                config.getTemperature(),
                config.getMaxTokens(),
                config.getParallel()
            );
            activeModelId = config.getId();

            return action.get();
        } catch (Exception e) {
            log.error("Error during model load or execution for {}: {}", config.getId(), e.getMessage(), e);
            activeModelId = null;
            throw new RuntimeException("Model load failed", e);
        }
    }

    public void unloadCurrentModel() {
        if (activeModelId != null) {
            try {
                log.info("📤 Finalizando flujo: Descargando modelo {}...", activeModelId);
                llamaServerWrapper.unloadModel(activeModelId);
                activeModelId = null;
            } catch (Exception e) {
                log.error("Error unloading model during cleanup: {}", e.getMessage());
            }
        }
    }

    public void unloadAnyModel() {
        if (activeModelId != null) {
            try {
                log.info("📤 Liberando memoria: Descargando modelo {}...", activeModelId);
                llamaServerWrapper.unloadModel(activeModelId);
                activeModelId = null;
            } catch (Exception e) {
                log.error("Error unloading model: {}", e.getMessage());
            }
        }
    }

    public void logModelResponse(String role, String modelName, String color, String response) {
        String safeResponse = response == null ? "" : response.replaceAll("[\\r\\n]+", " ").trim();
        if (safeResponse.length() > 140) safeResponse = safeResponse.substring(0, 140) + "...";
        System.out.println(color + "🤖 [MODEL RESPONSE][" + role + "][" + modelName + "] " + ANSI_RESET + safeResponse);
    }

    public String getColorForRole(String role) {
        return switch (role.toLowerCase()) {
            case "planner", "designer" -> ANSI_CYAN;
            case "coder", "implementer" -> ANSI_YELLOW;
            default                         -> ANSI_RESET;
        };
    }

    public void startNewConversation() {
        if (activeModelId == null) {
            log.warn("No hay modelo activo para iniciar una nueva conversación");
            return;
        }
        log.info("🆕 Nueva conversación sobre el modelo activo: {}", activeModelId);
    }

    /**
     * Extracts JSON from model response by looking for JSON blocks.
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.isBlank()) return null;

        String cleaned = response.trim();
        if (cleaned.contains("{") && cleaned.contains("}")) {
            int start = cleaned.indexOf("{");
            int end = cleaned.lastIndexOf("}");
            if (start < end) {
                cleaned = cleaned.substring(start, end + 1);
            }
        }

        try {
            ObjectMapper mapper = new ObjectMapper()
                    .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
                    .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature());

            JsonNode node = mapper.readTree(cleaned);
            return mapper.writeValueAsString(node);
        } catch (Exception e) {
            log.debug("No valid JSON found in response: {}", e.getMessage());
            return null;
        }
    }


    public String executeNativeToolsDirectly(String json) {
        return executeNativeTools(json);
    }

}
