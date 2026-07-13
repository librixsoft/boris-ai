package com.boris.librixsoft.level4.wrapper.llama;

import com.boris.librixsoft.ai.*;
import com.boris.librixsoft.exception.LlamaModelException;
import com.boris.librixsoft.level3.domain.service.SkillExecutor;
import com.boris.librixsoft.level3.domain.service.SkillService;
import com.boris.librixsoft.dto.TokenInfo;
import com.boris.librixsoft.level5.nativeCpp.jna.LlamaInstance;
import com.boris.librixsoft.level5.nativeCpp.jna.LlamaLibrary;
import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.Memory;
import com.sun.jna.Pointer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlamaChatService implements ChatModel, StreamingChatModel {

    private static final ObjectMapper TOOL_JSON_MAPPER = new ObjectMapper()
            .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
            .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature());

    private final SkillService skillService;
    private final SkillExecutor skillExecutor;

    private final AtomicReference<LlamaInstance> activeInstance = new AtomicReference<>();
    private final ReentrantLock generateLock = new ReentrantLock();
    private final AtomicReference<TokenInfo> lastTokenInfo = new AtomicReference<>();
    private final AtomicBoolean ready = new AtomicBoolean(false);

    private volatile int kvCachePosition = 0;
    private int[] sessionTokens = new int[32768];
    private volatile boolean systemPromptPrefilled = false;
    private int systemPromptTokenCount = 0;
    private volatile boolean reasoningModel = false;

    private final LlamaModelTemplateReader templateReader;
    private final LlamaModelStopTokens stopTokens;

    public static final String SYSTEM_PROMPT = loadSystemPrompt();

    private static String loadSystemPrompt() {
        try (java.io.InputStream is = LlamaChatService.class.getClassLoader().getResourceAsStream("prompts/AGENT.md")) {
            if (is == null) {
                throw new com.boris.librixsoft.exception.PromptLoadException(
                        "Required system prompt resource 'prompts/AGENT.md' not found in classpath.");
            }
            String prompt = new String(is.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8).trim();
            if (prompt.isEmpty()) {
                throw new com.boris.librixsoft.exception.PromptLoadException(
                        "Required system prompt resource 'prompts/AGENT.md' is empty.");
            }
            return prompt;
        } catch (com.boris.librixsoft.exception.PromptLoadException e) {
            throw e;
        } catch (Exception e) {
            throw new com.boris.librixsoft.exception.PromptLoadException(
                    "Error loading system prompt resource 'prompts/AGENT.md'.", e);
        }
    }

    LlamaLibrary.llama_batch.ByValue cachedBatch = null;
    int cachedBatchCapacity = 0;
    Pointer cachedSampler = null;
    float cachedSamplerTemp = Float.NaN;
    private final byte[] tokenBuf = new byte[256];

    private static final String ANSI_RESET  = "\u001B[0m";
    private static final String ANSI_CYAN   = "\u001B[96m";
    private static final String ANSI_YELLOW = "\u001B[93m";
    private static final String ANSI_GREEN  = "\u001B[92m";

// ─── Inferencia central usando JNA ─────────────────────────────────────────

    public void setActiveModel(LlamaInstance instance) {
        System.out.println("🔧 [JnaChatModel] setActiveModel START. instance alive? " + (instance != null && instance.isAlive()));
        generateLock.lock();
        try {
            ready.set(false);
            kvCachePosition = 0;
            if (sessionTokens != null) Arrays.fill(sessionTokens, 0);
            freeCachedResources();
            activeInstance.set(instance);
            boolean isAlive = instance != null && instance.isAlive();
            ready.set(isAlive);
            if (isAlive) {
                Pointer model = instance.getModel();
                Pointer vocab = LlamaLibrary.get().llama_model_get_vocab(model);
                templateReader.requireChatTemplate(model);
                reasoningModel = templateReader.isReasoningModel(model);
                stopTokens.initialize(model, vocab);
                log.info("🤖 [LlamaChatService] reasoning={}", reasoningModel);
            }
            System.out.println("🔧 [JnaChatModel] setActiveModel END. ready set to: " + ready.get());
        } finally {
            generateLock.unlock();
        }
    }

    public boolean isReady() {
        return ready.get();
    }

    public void prefillSystemPrompt(String systemPrompt) {
        LlamaInstance instance = activeInstance.get();
        if (instance == null || !instance.isAlive()) {
            log.warn("[SYSTEM-PREFILL] No active model instance, skipping prefill");
            return;
        }

        Pointer vocab = LlamaLibrary.get().llama_model_get_vocab(instance.getModel());
        Pointer ctx = instance.getContext();
        int nBatch = LlamaLibrary.get().llama_n_batch(ctx);

        instance.clearKvCache();
        kvCachePosition = 0;
        if (sessionTokens != null) Arrays.fill(sessionTokens, 0);

        int[] tokens = tokenize(vocab, systemPrompt, true);
        ensureBatch(nBatch);

        int remaining = tokens.length;
        int i = 0;
        while (remaining > 0) {
            int chunkSize = 1;
            while (chunkSize * 2 <= remaining && chunkSize * 2 <= nBatch) chunkSize *= 2;
            cachedBatch.n_tokens = 0;
            for (int j = 0; j < chunkSize; j++) {
                int idx = i + j;
                batchAdd(cachedBatch, tokens[idx], idx, List.of(0), idx == tokens.length - 1);
                sessionTokens[idx] = tokens[idx];
            }
            if (LlamaLibrary.get().llama_decode(ctx, cachedBatch) != 0) {
                throw new LlamaModelException("llama_decode failed during system prompt prefill at chunk " + i);
            }
            i += chunkSize;
            remaining -= chunkSize;
        }

        kvCachePosition = tokens.length;
        systemPromptTokenCount = tokens.length;
        systemPromptPrefilled = true;
        log.info("[SYSTEM-PREFILL] ✅ {} tokens, kvPos={}", tokens.length, kvCachePosition);
    }

    public void resetKvCache() {
        generateLock.lock();
        try {
            LlamaInstance inst = activeInstance.get();
            if (inst != null && inst.isAlive()) {
                inst.clearKvCache();
                kvCachePosition = 0;
                if (sessionTokens != null) Arrays.fill(sessionTokens, 0);
                systemPromptPrefilled = false;
                systemPromptTokenCount = 0;
                log.debug("[KV] Cache cleared");
            }
        } finally {
            generateLock.unlock();
        }
    }

    /**
     * Envía un mensaje directo al modelo sin usar List<Message>.
     */
    public String executePrompt(String modelName, String systemPrompt, String userPrompt,
                                Double temperature, AtomicBoolean cancellationRequested) {
        return generate(systemPrompt, userPrompt, temperature, null);
    }

    public String executePrompt(String modelName, String systemPrompt, String userPrompt,
                               Double temperature, AtomicBoolean cancellationRequested,
                               List<Message> history) {
        return executePrompt(modelName, systemPrompt, userPrompt, temperature, cancellationRequested, history, null);
    }

    public Flux<ChatResponse> streamPrompt(String modelName, String systemPrompt, String userPrompt,
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

         var optionsBuilder = ChatOptions.builder();
         if (maxTokens != null) optionsBuilder.maxTokens(maxTokens);
         if (temperature != null) optionsBuilder.temperature(temperature);

         ChatOptions options = optionsBuilder.build();
         Prompt prompt = options != null ? new Prompt(messages, options) : new Prompt(messages);

         return stream(prompt, cancellationRequested);
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
        
        var optionsBuilder = ChatOptions.builder();
        if (maxTokens != null) optionsBuilder.maxTokens(maxTokens);
        if (temperature != null) optionsBuilder.temperature(temperature);
        
        ChatOptions options = optionsBuilder.build();

        Prompt prompt = options != null ? new Prompt(messages, options) : new Prompt(messages);

        try {
            StringBuilder fullResponse = new StringBuilder();
            stream(prompt, cancellationRequested).toIterable().forEach(r ->
                    fullResponse.append(r.getResults().get(0).getOutput().getText()));
            TokenInfo tokenInfo = getLastTokenInfo();
            log.info("📊 [TOKENS] Input: {}, Output: {}, Context: {}, Remaining: {}",
                    tokenInfo.getInputTokens(), tokenInfo.getOutputTokens(),
                    tokenInfo.getContextSize(), tokenInfo.getRemainingTokens());
            return fullResponse.toString();
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
            if (cancellationRequested != null && cancellationRequested.get()) {
                break;
            }
            turn++;
            String response = executePrompt(modelName, systemPrompt, currentInput, temperature, cancellationRequested, localHistory, maxTokens);
            
            if (response == null || response.isBlank()) break;
            lastResponse = response;

            if (cancellationRequested != null && cancellationRequested.get()) {
                break;
            }

            String toolResults = executeNativeTools(response, cancellationRequested);
            if (!toolResults.equals(response)) {
                log.info("🛠️ [JNA TOOL CALL] Turno {}: Herramientas ejecutadas en {}", turn, modelName);
                
                // Guardamos el intercambio en la historia local para el siguiente turno
                localHistory.add(new UserMessage(currentInput));
                localHistory.add(new AssistantMessage(response));

                // El resultado de la herramienta se agrega al historial, no como input crudo
                String toolSummary = toolResults.length() > 500 ? toolResults.substring(0, 500) + "..." : toolResults;
                localHistory.add(new UserMessage("[Resultado de herramienta]:\n" + toolSummary));

                // Detectar si necesitamos más turnos (ej. si leyó un archivo y necesita procesarlo)
                String upperResponse = response.toUpperCase();
                boolean needsMoreTurns = upperResponse.contains("\"READFILE\"") || upperResponse.contains("\"EDIT\"");
                if (!needsMoreTurns) {
                    break;
                }

                // El resultado limpio para la próxima instrucción si continúa
                currentInput = "[TOOL_RESULT]\n" + toolResults.trim() + "\n[/TOOL_RESULT]\n" +
                               "Tarea completada. ¿Necesitas realizar alguna otra acción en los archivos leídos?";
                continue;
            }
            
            // Si no hay más llamadas a herramientas o no se ejecutaron, salimos
            break;
        }
        
        return lastResponse;
    }

    public TokenInfo getTokenInfo() {
        return getLastTokenInfo();
    }

    public String executeNativeToolsDirectly(String json) {
        return executeNativeTools(json, null);
    }

    public String executeNativeToolsDirectly(String json, AtomicBoolean cancellationRequested) {
        return executeNativeTools(json, cancellationRequested);
    }

    private String executeNativeTools(String response, AtomicBoolean cancellationRequested) {
        if (cancellationRequested != null && cancellationRequested.get()) {
            return response;
        }

        String json = extractJsonFromResponse(response);
        if (json == null || json.isBlank()) {
            // Fallback: intentar parseo legacy de texto plano
            return executeLegacyNativeTools(response, cancellationRequested);
        }

        StringBuilder toolResults = new StringBuilder();
        boolean found = false;

        try {
            JsonNode root = TOOL_JSON_MAPPER.readTree(json);
            JsonNode actions = root.path("actions");
            if (actions.isArray()) {
                for (JsonNode action : actions) {
                    if (cancellationRequested != null && cancellationRequested.get()) {
                        log.info("🛑 Tool execution cancelled before running next tool.");
                        break;
                    }
                    String skillOrToolName = action.path("tool").asText("");
                    Map<String, String> args = jsonArgsToMap(action.path("args"));

                    if (!skillExecutor.canExecute(skillOrToolName)) {
                        log.warn("⚠️ [TOOL SKIP] Skill desconocida o sin command: '{}' (skills: {})",
                                skillOrToolName, skillService.getSkills().keySet());
                        continue;
                    }

                    String result;
                    try {
                        result = skillExecutor.execute(skillOrToolName, args);
                        found = true;
                    } catch (Exception e) {
                        result = "Error ejecutando herramienta " + skillOrToolName + ": " + e.getMessage();
                        found = true;
                    }

                    log.info("⚙️ [TOOL EXECUTION] {}: {}", skillOrToolName, result);
                    toolResults.append("[tool:").append(skillOrToolName).append("] Result: ").append(result).append("\n");
                }
            }
        } catch (Exception e) {
            log.warn("⚠️ [TOOL PARSE] Fallo al parsear JSON, intentando legacy: {}", e.getMessage());
            return executeLegacyNativeTools(response, cancellationRequested);
        }

        return found ? toolResults.toString() : response;
    }

    private String executeLegacyNativeTools(String response, AtomicBoolean cancellationRequested) {
        if (cancellationRequested != null && cancellationRequested.get()) {
            return response;
        }

        // Regex robusto para capturar llamadas: toolName("arg1", "arg2") o toolName(arg1, arg2)
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?:print\\s*\\()?\\s*(\\w+)\\s*\\((.*?)\\)\\s*\\)?", java.util.regex.Pattern.DOTALL);
        java.util.regex.Matcher matcher = pattern.matcher(response);

        StringBuilder toolResults = new StringBuilder();
        boolean found = false;

        while (matcher.find()) {
            if (cancellationRequested != null && cancellationRequested.get()) {
                log.info("🛑 Legacy tool execution cancelled before running next tool.");
                break;
            }
            String skillOrToolName = matcher.group(1);
            String argsStr = matcher.group(2);

            List<String> args = parseLegacyArgs(argsStr);

            if (args.isEmpty() && !argsStr.isBlank()) {
                for (String s : argsStr.split(",")) {
                    args.add(s.trim().replaceAll("^'|'$|^\"|\"$", ""));
                }
            }

            if (!skillExecutor.canExecute(skillOrToolName)) {
                continue;
            }

            String result;
            try {
                result = skillExecutor.executeLegacy(skillOrToolName, args);
                found = true;
            } catch (Exception e) {
                result = "Error ejecutando herramienta " + skillOrToolName + ": " + e.getMessage();
                found = true;
            }

            log.info("⚙️ [TOOL EXECUTION] {}: {}", skillOrToolName, result);
            toolResults.append("[tool:").append(skillOrToolName).append("] Result: ").append(result).append("\n");
        }

        return found ? toolResults.toString() : response;
    }

    private static Map<String, String> jsonArgsToMap(JsonNode args) {
        Map<String, String> map = new LinkedHashMap<>();
        if (args == null || args.isNull() || !args.isObject()) {
            return map;
        }
        Iterator<Map.Entry<String, JsonNode>> fields = args.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> entry = fields.next();
            JsonNode value = entry.getValue();
            if (value == null || value.isNull()) {
                map.put(entry.getKey(), null);
            } else if (value.isTextual()) {
                map.put(entry.getKey(), value.asText());
            } else {
                map.put(entry.getKey(), value.toString());
            }
        }
        return map;
    }

    private List<String> parseLegacyArgs(String argsStr) {
        List<String> args = new ArrayList<>();
        Matcher m = Pattern.compile("\"((?:\\\\.|[^\"])*)\"").matcher(argsStr);
        while (m.find()) {
            try {
                // Jackson desescapa todo correctamente
                args.add(TOOL_JSON_MAPPER.readValue("\"" + m.group(1) + "\"", String.class));
            } catch (Exception e) {
                args.add(m.group(1)); // fallback sin desescapar
            }
        }
        return args;
    }

    // ─── LlamaChatService: solo chat/inferencia ───────────────────────────────

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
        resetKvCache();
        log.info("🆕 Nueva conversación sobre el modelo activo");
    }

    /**
     * Extracts the first object or array that Jackson can parse from a model response.
     */
    private String extractJsonFromResponse(String response) {
        if (response == null || response.isBlank()) return null;

        String source = response.trim();
        int offset = 0;

        while (offset < source.length()) {
            try (JsonParser parser = TOOL_JSON_MAPPER.getFactory().createParser(source.substring(offset))) {
                JsonToken firstToken = parser.nextToken();
                if (firstToken == null) {
                    return null;
                }
                if (!firstToken.isStructStart()) {
                    offset++;
                    continue;
                }

                JsonNode node = TOOL_JSON_MAPPER.readTree(parser);
                return TOOL_JSON_MAPPER.writeValueAsString(node);
            } catch (JsonParseException e) {
                offset = nextJacksonRetryOffset(offset, e);
            } catch (IOException e) {
                log.debug("No valid JSON found in response: {}", e.getMessage());
                return null;
            }
        }

        return null;
    }

    private int nextJacksonRetryOffset(int currentOffset, JsonParseException e) {
        long charOffset = e.getLocation() != null ? e.getLocation().getCharOffset() : -1;
        if (charOffset < 0 || charOffset > Integer.MAX_VALUE - currentOffset - 1L) {
            return currentOffset + 1;
        }
        return Math.max(currentOffset + 1, currentOffset + (int) charOffset + 1);
    }


    @Override
    public ChatResponse call(Prompt prompt) {
        StringBuilder fullResponse = new StringBuilder();
        stream(prompt).toIterable().forEach(r ->
                fullResponse.append(r.getResults().get(0).getOutput().getText()));
        return new ChatResponse(List.of(new Generation(new AssistantMessage(fullResponse.toString()))));
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return stream(prompt, null);
    }

    public Flux<ChatResponse> stream(Prompt prompt, AtomicBoolean cancellationRequested) {
        System.out.println("🔧 [JnaChatModel] stream() called. ready=" + ready.get() + ", activeInstance alive? " + (activeInstance.get() != null && activeInstance.get().isAlive()));

        Sinks.Many<ChatResponse> sink = Sinks.many().unicast().onBackpressureBuffer();

        reactor.core.scheduler.Schedulers.boundedElastic().schedule(() -> {
            if (!ready.get()) {
                System.out.println("❌ [JnaChatModel] Model NOT ready! Throwing exception.");
                sink.tryEmitError(new IllegalStateException("Model not ready"));
                return;
            }

            String systemPromptFallback = null;
            for (Message m : prompt.getInstructions()) {
                if ("system".equals(m.getMessageType().getValue())) {
                    systemPromptFallback = m.getText();
                    break;
                }
            }
            final String effectiveSystemPrompt = systemPromptFallback != null ? systemPromptFallback : SYSTEM_PROMPT;

            generateLock.lock();
            int inputTokens = 0, outputTokens = 0, contextSize = 0;
            try {
                LlamaInstance instance = activeInstance.get();
                if (instance == null || !instance.isAlive()) {
                    sink.tryEmitError(new IllegalStateException("No active native model loaded"));
                    return;
                }

                Pointer model = instance.getModel();
                Pointer ctx = instance.getContext();
                Pointer vocab = LlamaLibrary.get().llama_model_get_vocab(model);
                contextSize = LlamaLibrary.get().llama_n_ctx(ctx);
                int nBatchMax = LlamaLibrary.get().llama_n_batch(ctx);

                String modelPath = instance.getModelPath();

                String input = templateReader.buildPrompt(instance.getModel(), prompt.getInstructions(), systemPromptPrefilled);
                log.info("📝 [PROMPT] Full prompt being sent to model: {}", input);
                int[] tokens = tokenize(vocab, input, !systemPromptPrefilled);
                int nTokens = tokens.length;
                inputTokens = nTokens;

                if (nTokens > contextSize - 4) {
                    sink.tryEmitError(new IllegalArgumentException(
                            "Prompt too long (" + nTokens + " > " + contextSize + ")"));
                    return;
                }

                ensureBatch(nBatchMax);

                int kvOffset = systemPromptPrefilled ? systemPromptTokenCount : 0;
                int prefixLen = 0;
                while (prefixLen < (kvCachePosition - kvOffset) && prefixLen < nTokens
                        && sessionTokens[prefixLen] == tokens[prefixLen]) prefixLen++;

                if (prefixLen < (kvCachePosition - kvOffset)) {
                    try {
                        Pointer mem = LlamaLibrary.get().llama_get_memory(ctx);
                        LlamaLibrary.get().llama_memory_seq_rm(mem, 0, kvOffset + prefixLen, -1);
                        kvCachePosition = kvOffset + prefixLen;
                    } catch (Exception e) {
                        log.debug("[KV] seq_rm falló, reset completo: {}", e.getMessage());
                        instance.clearKvCache();
                        prefillSystemPrompt(effectiveSystemPrompt);
                        kvOffset = systemPromptTokenCount;
                        prefixLen = 0;
                    }
                }

                if (sessionTokens.length < contextSize) {
                    int[] newTokens = new int[contextSize];
                    System.arraycopy(sessionTokens, 0, newTokens, 0, Math.max(0, kvCachePosition - kvOffset));
                    sessionTokens = newTokens;
                }

                if (prefixLen < nTokens) {
                    int remaining = nTokens - prefixLen;
                    int i = prefixLen;
                    while (remaining > 0) {
                        if (cancellationRequested != null && cancellationRequested.get()) {
                            log.info("🛑 [JnaChatModel] Prefill cancelled by user.");
                            sink.tryEmitComplete();
                            return;
                        }
                        int chunkSize = 1;
                        while (chunkSize * 2 <= remaining && chunkSize * 2 <= nBatchMax) chunkSize *= 2;
                        cachedBatch.n_tokens = 0;
                        for (int j = 0; j < chunkSize; j++) {
                            int tokIdx = i + j;
                            int absolutePos = kvOffset + tokIdx;
                            batchAdd(cachedBatch, tokens[tokIdx], absolutePos, List.of(0), tokIdx == nTokens - 1);
                            sessionTokens[tokIdx] = tokens[tokIdx];
                        }
                        if (LlamaLibrary.get().llama_decode(ctx, cachedBatch) != 0) {
                            sink.tryEmitError(new RuntimeException("llama_decode failed on prefill"));
                            return;
                        }
                        i += chunkSize;
                        remaining -= chunkSize;
                    }
                    kvCachePosition = kvOffset + nTokens;
                }

                float temperature = 0.1f;
                if (prompt.getOptions() != null && prompt.getOptions().getTemperature() != null)
                    temperature = prompt.getOptions().getTemperature().floatValue();
                ensureSampler(temperature);

                int maxTokens = 4096;
                if (prompt.getOptions() != null && prompt.getOptions().getMaxTokens() != null)
                    maxTokens = prompt.getOptions().getMaxTokens();
                maxTokens = resolveMaxTokens(modelPath, maxTokens);

                int thinkingTokens = 0;
                int maxThinkTokens = getMaxThinkTokens(modelPath);
                boolean inThinkBlock = false;
                StringBuilder textAcc = new StringBuilder();
                Utf8Decoder utf8Decoder = new Utf8Decoder();

                int nCur = kvCachePosition;
                while (nCur < contextSize && outputTokens < maxTokens) {
                    if (cancellationRequested != null && cancellationRequested.get()) {
                        log.info("🛑 [JnaChatModel] Generation cancelled by user inside stream loop.");
                        break;
                    }

                    int token = LlamaLibrary.get().llama_sampler_sample(
                            cachedSampler, ctx, cachedBatch.n_tokens - 1);
                    LlamaLibrary.get().llama_sampler_accept(cachedSampler, token);

                    if (stopTokens.isStopToken(token, vocab)) break;

                    byte[] pieceBytes = detokenizeToBytes(vocab, token);
                    if (maxThinkTokens > 0) {
                        String piece = utf8Decoder.decode(pieceBytes);
                        textAcc.append(piece);
                        String acc = textAcc.toString();

                        if (!inThinkBlock && acc.contains("<|thinking|>")) {
                            inThinkBlock = true;
                            textAcc = new StringBuilder(acc.substring(acc.indexOf("<|thinking|>")));
                        }
                        if (inThinkBlock && acc.contains("</thinking>")) {
                            inThinkBlock = false;
                            thinkingTokens = 0;
                            textAcc = new StringBuilder();
                            cachedBatch.n_tokens = 0;
                            batchAdd(cachedBatch, token, nCur, List.of(0), true);
                            if (LlamaLibrary.get().llama_decode(ctx, cachedBatch) != 0) {
                                sink.tryEmitError(new RuntimeException("llama_decode failed"));
                                return;
                            }
                            sessionTokens[nCur] = token;
                            nCur++;
                            kvCachePosition++;
                            outputTokens++;
                            continue;
                        }
                        if (inThinkBlock) {
                            thinkingTokens++;
                            if (thinkingTokens >= maxThinkTokens) {
                                inThinkBlock = false;
                                thinkingTokens = 0;
                                textAcc = new StringBuilder();
                            }
                            cachedBatch.n_tokens = 0;
                            batchAdd(cachedBatch, token, nCur, List.of(0), true);
                            if (LlamaLibrary.get().llama_decode(ctx, cachedBatch) != 0) {
                                sink.tryEmitError(new RuntimeException("llama_decode failed"));
                                return;
                            }
                            sessionTokens[nCur] = token;
                            nCur++;
                            kvCachePosition++;
                            continue;
                        }
                        if (!piece.isEmpty()) {
                            var emitResult = sink.tryEmitNext(new ChatResponse(List.of(new Generation(new AssistantMessage(piece)))));
                            if (emitResult.isFailure()) {
                                log.info("🛑 [JnaChatModel] Sink emit failed, stopping generation: {}", emitResult);
                                break;
                            }
                        }
                    } else {
                        String piece = utf8Decoder.decode(pieceBytes);
                        if (!piece.isEmpty()) {
                            var emitResult = sink.tryEmitNext(new ChatResponse(List.of(new Generation(new AssistantMessage(piece)))));
                            if (emitResult.isFailure()) {
                                log.info("🛑 [JnaChatModel] Sink emit failed, stopping generation: {}", emitResult);
                                break;
                            }
                        }
                    }

                    outputTokens++;
                    cachedBatch.n_tokens = 0;
                    batchAdd(cachedBatch, token, nCur, List.of(0), true);
                    if (LlamaLibrary.get().llama_decode(ctx, cachedBatch) != 0) {
                        sink.tryEmitError(new RuntimeException("llama_decode failed"));
                        return;
                    }
                    sessionTokens[nCur] = token;
                    nCur++;
                    kvCachePosition++;
                }

                String remainingPiece = utf8Decoder.flush();
                if (!remainingPiece.isEmpty()) {
                    sink.tryEmitNext(new ChatResponse(List.of(new Generation(new AssistantMessage(remainingPiece)))));
                }

                lastTokenInfo.set(new TokenInfo(inputTokens, outputTokens, contextSize));
                sink.tryEmitComplete();

            } catch (Exception e) {
                sink.tryEmitError(e);
            } finally {
                generateLock.unlock();
            }
        });

        return sink.asFlux();
    }

    public TokenInfo getLastTokenInfo() {
        return lastTokenInfo.get();
    }

    public void ensureBatch(int requiredCapacity) {
        if (cachedBatch == null || cachedBatchCapacity < requiredCapacity) {
            if (cachedBatch != null) {
                try {
                    LlamaLibrary.get().llama_batch_free(cachedBatch);
                } catch (Exception e) {
                    throw new LlamaModelException("Failed to free cached batch", e);
                }
            }
            cachedBatch = LlamaLibrary.get().llama_batch_init(requiredCapacity, 0, 1);
            cachedBatchCapacity = requiredCapacity;
        }
    }

    public void ensureSampler(float temperature) {
        if (cachedSampler != null && cachedSamplerTemp == temperature) return;
        if (cachedSampler != null) {
            try {
                LlamaLibrary.get().llama_sampler_free(cachedSampler);
            } catch (Exception e) {
                throw new LlamaModelException("Failed to free cached sampler", e);
            }
        }
        LlamaLibrary.llama_sampler_chain_params.ByValue sparams =
                LlamaLibrary.get().llama_sampler_chain_default_params();
        cachedSampler = LlamaLibrary.get().llama_sampler_chain_init(sparams);
        LlamaLibrary.get().llama_sampler_chain_add(cachedSampler, LlamaLibrary.get().llama_sampler_init_top_k(40));
        LlamaLibrary.get().llama_sampler_chain_add(cachedSampler, LlamaLibrary.get().llama_sampler_init_top_p(0.95f, new com.sun.jna.NativeLong(1)));
        LlamaLibrary.get().llama_sampler_chain_add(cachedSampler, LlamaLibrary.get().llama_sampler_init_temp(temperature));
        LlamaLibrary.get().llama_sampler_chain_add(cachedSampler, LlamaLibrary.get().llama_sampler_init_dist((int) (Math.random() * Integer.MAX_VALUE)));
        LlamaLibrary.get().llama_sampler_chain_add(cachedSampler,
                LlamaLibrary.get().llama_sampler_init_penalties(64, 1.1f, 0.0f, 0.0f));
        cachedSamplerTemp = temperature;
    }

    public void freeCachedResources() {
        if (cachedBatch != null) {
            try {
                LlamaLibrary.get().llama_batch_free(cachedBatch);
            } catch (Exception e) {
                throw new LlamaModelException("Failed to free cached batch during cleanup", e);
            }
            cachedBatch = null;
            cachedBatchCapacity = 0;
        }
        if (cachedSampler != null) {
            try {
                LlamaLibrary.get().llama_sampler_free(cachedSampler);
            } catch (Exception e) {
                throw new LlamaModelException("Failed to free cached sampler during cleanup", e);
            }
            cachedSampler = null;
            cachedSamplerTemp = Float.NaN;
        }
    }

    public int[] tokenize(Pointer vocab, String text, boolean addBos) {
        byte[] utf8 = text.getBytes(StandardCharsets.UTF_8);
        Memory textMem = new Memory(utf8.length + 1);
        textMem.write(0, utf8, 0, utf8.length);
        textMem.setByte(utf8.length, (byte) 0);
        byte addBosByte = addBos ? (byte) 1 : (byte) 0;
        int n = LlamaLibrary.get().llama_tokenize(vocab, textMem, utf8.length, null, 0, addBosByte, (byte) 1);
        if (n == 0) return new int[0];
        if (n < 0) n = -n;
        Memory mem = new Memory(n * 4L);
        int actual = LlamaLibrary.get().llama_tokenize(vocab, textMem, utf8.length, mem, n, addBosByte, (byte) 1);
        int count = actual < 0 ? n : actual;
        int[] result = new int[count];
        mem.read(0, result, 0, count);
        return result;
    }

    private byte[] detokenizeToBytes(Pointer vocab, int token) {
        int n = LlamaLibrary.get().llama_token_to_piece(vocab, token, tokenBuf, tokenBuf.length, 0, (byte) 0);
        byte[] buf = tokenBuf;
        if (n < 0) {
            buf = new byte[-n];
            n = LlamaLibrary.get().llama_token_to_piece(vocab, token, buf, buf.length, 0, (byte) 1);
        }
        if (n <= 0) return new byte[0];
        byte[] result = new byte[n];
        System.arraycopy(buf, 0, result, 0, n);
        return result;
    }

    private String detokenize(Pointer vocab, int token) {
        byte[] bytes = detokenizeToBytes(vocab, token);
        if (bytes.length == 0) return "";
        return new String(bytes, 0, bytes.length, StandardCharsets.UTF_8);
    }



    public String generate(String systemPrompt, String userPrompt,
                           Double temperature, Integer maxTokens) {
        generateLock.lock();
        java.io.ByteArrayOutputStream responseBytes = new java.io.ByteArrayOutputStream();
        try {
            LlamaInstance instance = activeInstance.get();
            if (instance == null || !instance.isAlive()) {
                throw new IllegalStateException("No active native model loaded");
            }

            Pointer model = instance.getModel();
            Pointer ctx = instance.getContext();
            Pointer vocab = LlamaLibrary.get().llama_model_get_vocab(model);
            int contextSize = LlamaLibrary.get().llama_n_ctx(ctx);
            int nBatchMax = LlamaLibrary.get().llama_n_batch(ctx);

            String input = templateReader.buildPromptFromStrings(instance.getModel(), systemPrompt, userPrompt);
            int[] tokens = tokenize(vocab, input, !systemPromptPrefilled);
            int nTokens = tokens.length;

            if (nTokens > contextSize - 4) {
                throw new IllegalArgumentException(
                        "Prompt too long (" + nTokens + " > " + contextSize + ")");
            }

            ensureBatch(nBatchMax);

            int kvOffset = systemPromptPrefilled ? systemPromptTokenCount : 0;
            int prefixLen = 0;
            while (prefixLen < (kvCachePosition - kvOffset) && prefixLen < nTokens
                    && sessionTokens[prefixLen] == tokens[prefixLen]) prefixLen++;

            if (prefixLen < (kvCachePosition - kvOffset)) {
                try {
                    Pointer mem = LlamaLibrary.get().llama_get_memory(ctx);
                    LlamaLibrary.get().llama_memory_seq_rm(mem, 0, kvOffset + prefixLen, -1);
                    kvCachePosition = kvOffset + prefixLen;
                } catch (Exception e) {
                    log.debug("[KV] seq_rm falló, reset completo: {}", e.getMessage());
                    instance.clearKvCache();
                    prefillSystemPrompt(systemPrompt != null && !systemPrompt.isBlank()
                            ? systemPrompt : SYSTEM_PROMPT);
                    kvOffset = systemPromptTokenCount;
                    prefixLen = 0;
                }
            }

            if (sessionTokens.length < contextSize) {
                int[] newTokens = new int[contextSize];
                System.arraycopy(sessionTokens, 0, newTokens, 0,
                        Math.max(0, kvCachePosition - kvOffset));
                sessionTokens = newTokens;
            }

            if (prefixLen < nTokens) {
                int remaining = nTokens - prefixLen;
                int i = prefixLen;
                while (remaining > 0) {
                    int chunkSize = 1;
                    while (chunkSize * 2 <= remaining && chunkSize * 2 <= nBatchMax) chunkSize *= 2;
                    cachedBatch.n_tokens = 0;
                    for (int j = 0; j < chunkSize; j++) {
                        int tokIdx = i + j;
                        int absolutePos = kvOffset + tokIdx;
                        batchAdd(cachedBatch, tokens[tokIdx], absolutePos, List.of(0),
                                tokIdx == nTokens - 1);
                        sessionTokens[tokIdx] = tokens[tokIdx];
                    }
                    if (LlamaLibrary.get().llama_decode(ctx, cachedBatch) != 0) {
                        throw new RuntimeException("llama_decode failed on prefill");
                    }
                    i += chunkSize;
                    remaining -= chunkSize;
                }
                kvCachePosition = kvOffset + nTokens;
            }

            float temp = 0.1f;
            if (temperature != null) temp = temperature.floatValue();
            ensureSampler(temp);

            int maxToks = 4096;
            if (maxTokens != null) maxToks = maxTokens;

            int outputTokens = 0;
            while (outputTokens < maxToks) {
                int token = LlamaLibrary.get().llama_sampler_sample(
                        cachedSampler, ctx, cachedBatch.n_tokens - 1);
                LlamaLibrary.get().llama_sampler_accept(cachedSampler, token);

                if (stopTokens.isStopToken(token, vocab)) break;

                byte[] pieceBytes = detokenizeToBytes(vocab, token);
                if (pieceBytes.length > 0) {
                    responseBytes.write(pieceBytes, 0, pieceBytes.length);
                }

                outputTokens++;
                cachedBatch.n_tokens = 0;
                batchAdd(cachedBatch, token, kvCachePosition, List.of(0), true);
                if (LlamaLibrary.get().llama_decode(ctx, cachedBatch) != 0) {
                    throw new LlamaModelException("llama_decode failed during generation");
                }
                sessionTokens[kvCachePosition] = token;
                kvCachePosition++;
            }

            lastTokenInfo.set(new TokenInfo(nTokens, outputTokens, contextSize));
            return responseBytes.toString(StandardCharsets.UTF_8);

        } finally {
            generateLock.unlock();
        }
    }

    public void batchAdd(LlamaLibrary.llama_batch batch, int token, int pos,
                         List<Integer> seqIds, boolean logits) {
        batch.token.setInt(batch.n_tokens * 4L, token);
        batch.pos.setInt(batch.n_tokens * 4L, pos);
        batch.n_seq_id.setInt(batch.n_tokens * 4L, seqIds.size());
        for (int i = 0; i < seqIds.size(); i++)
            batch.seq_id.getPointer(batch.n_tokens * 8L).setInt(i * 4L, seqIds.get(i));
        batch.logits.setByte(batch.n_tokens, (byte) (logits ? 1 : 0));
        batch.n_tokens++;
    }

    private int getMaxThinkTokens(String ignoredModelPath) {
        return reasoningModel ? 256 : 0;
    }

    private int resolveMaxTokens(String ignoredModelPath, int defaultMax) {
        return defaultMax;
    }

    private static class Utf8Decoder {
        private final java.io.ByteArrayOutputStream pendingBytes = new java.io.ByteArrayOutputStream();

        public String decode(byte[] pieceBytes) {
            if (pieceBytes.length == 0) return "";
            pendingBytes.write(pieceBytes, 0, pieceBytes.length);
            byte[] buffered = pendingBytes.toByteArray();
            int incompleteCount = getIncompleteBytesCount(buffered, buffered.length);
            int completeLength = buffered.length - incompleteCount;
            if (completeLength > 0) {
                String result = new String(buffered, 0, completeLength, java.nio.charset.StandardCharsets.UTF_8);
                pendingBytes.reset();
                if (incompleteCount > 0) {
                    pendingBytes.write(buffered, completeLength, incompleteCount);
                }
                return result;
            } else {
                return "";
            }
        }

        public String flush() {
            if (pendingBytes.size() > 0) {
                String result = new String(pendingBytes.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                pendingBytes.reset();
                return result;
            }
            return "";
        }

        private int getIncompleteBytesCount(byte[] data, int len) {
            if (len == 0) return 0;
            for (int i = 1; i <= Math.min(4, len); i++) {
                byte b = data[len - i];
                if ((b & 0x80) == 0) {
                    return 0;
                }
                if ((b & 0xC0) == 0xC0) {
                    int expectedLen;
                    if ((b & 0xE0) == 0xC0) expectedLen = 2;
                    else if ((b & 0xF0) == 0xE0) expectedLen = 3;
                    else if ((b & 0xF8) == 0xF0) expectedLen = 4;
                    else expectedLen = 1;

                    int actualLen = i;
                    if (actualLen < expectedLen) {
                        return actualLen;
                    } else {
                        return 0;
                    }
                }
            }
            return 0;
        }
    }

}
