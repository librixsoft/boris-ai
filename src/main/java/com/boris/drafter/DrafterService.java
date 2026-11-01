package com.boris.drafter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import com.knuddels.jtokkit.Encodings;
import com.knuddels.jtokkit.api.Encoding;
import com.knuddels.jtokkit.api.EncodingRegistry;
import com.knuddels.jtokkit.api.EncodingType;

import com.boris.exceptions.BorisException;
import com.boris.llm.client.BorisLlmClient;
import com.boris.llm.client.ChatMessage;
import com.boris.llm.client.ChatRequest;
import com.boris.llm.client.ChatResponse;
import com.boris.settings.DrafterConfig;
import com.boris.settings.ModelConfig;
import com.boris.settings.Settings;
import com.boris.tooling.ToolProgressNotifier;
import com.boris.tooling.ToolResultNotifier;
import com.boris.tooling.annotation.Tool;
import com.boris.tooling.annotation.ToolParam;
import com.boris.tooling.tool.WriteTool;
import com.boris.context.ProjectContext;

public class DrafterService {

    private static final String DRAFTER_PROMPT_RESOURCE = "/prompts/drafter/drafter-system-prompt.md";
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    private static final Encoding TOKEN_ENCODING;
    static {
        EncodingRegistry registry = Encodings.newDefaultEncodingRegistry();
        TOKEN_ENCODING = registry.getEncoding(EncodingType.CL100K_BASE);
    }

    private final String modelName;
    private final Double temperature;
    private final Integer maxTokens;
    private final BorisLlmClient client;

    public DrafterService(Settings settings) {
        DrafterConfig drafter = settings != null ? settings.getDrafter() : null;
        if (drafter == null || !Boolean.TRUE.equals(drafter.getEnabled())) {
            throw new BorisException("DrafterService requiere el bloque 'drafter' habilitado en settings.json");
        }
        if (drafter.getName() == null || drafter.getName().isBlank()) {
            throw new BorisException("El bloque 'drafter' de settings.json requiere name");
        }
        if (drafter.getTemperature() == null || drafter.getMaxTokens() == null) {
            throw new BorisException("El bloque 'drafter' de settings.json requiere temperature y maxTokens");
        }
        ModelConfig mainModel = settings.getModel();
        if (mainModel == null || mainModel.getBaseUrl() == null || mainModel.getBaseUrl().isBlank()) {
            throw new BorisException("El bloque 'model' de settings.json requiere baseUrl: el drafter comparte el mismo servidor que el modelo principal");
        }
        this.modelName = drafter.getName();
        this.temperature = drafter.getTemperature();
        this.maxTokens = drafter.getMaxTokens();
        this.client = new BorisLlmClient(mainModel.getBaseUrl());
    }

    public static boolean isDrafterActive(Settings settings) {
        return settings != null
                && settings.getDrafter() != null
                && Boolean.TRUE.equals(settings.getDrafter().getEnabled());
    }

    public static boolean isModelAvailable(String baseUrl, String modelName) {
        if (baseUrl == null || baseUrl.isBlank() || modelName == null || modelName.isBlank()) {
            return false;
        }
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            String tagsUrl = baseUrl.strip();
            if (tagsUrl.endsWith("/")) {
                tagsUrl = tagsUrl.substring(0, tagsUrl.length() - 1);
            }
            HttpRequest request = HttpRequest.newBuilder(URI.create(tagsUrl + "/api/tags"))
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return false;
            }
            JsonNode root = MAPPER.readTree(response.body());
            JsonNode models = root.get("models");
            if (models == null || !models.isArray() || models.isEmpty()) {
                return false;
            }
            for (JsonNode model : models) {
                JsonNode name = model.get("name");
                if (name != null && (name.asText().equals(modelName) || name.asText().contains(modelName))) {
                    return true;
                }
            }
            return false;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    public static boolean isDrafterAvailable(Settings settings) {
        if (!isDrafterActive(settings)) {
            return false;
        }
        ModelConfig mainModel = settings.getModel();
        return isModelAvailable(
                mainModel != null ? mainModel.getBaseUrl() : null,
                settings.getDrafter().getName());
    }

    @Tool(
            name = "write_file",
            description = "Create a NEW file or fully overwrite an existing one. You provide ONLY a short specification (spec); the full file content is written by the drafter model. Afterwards review the written file and refine it with apply_edit when needed. Do NOT use it for partial changes to an existing file; use apply_edit for those.")
    public String write_file(
            @ToolParam(description = "Absolute or relative file path") String path,
            @ToolParam(description = "Short specification of the file: purpose, structure, classes and methods, required imports and conventions. The drafter model writes the complete content from it") String spec) {
        ToolProgressNotifier.notify("write_file", "Drafter (" + modelName + ") escribiendo: " + path);
        return writeFile(path, spec);
    }

    public String writeFile(String path, String spec) {
        if (path == null || path.isBlank()) {
            throw new BorisException("write_file requiere 'path'");
        }
        if (spec == null || spec.isBlank()) {
            throw new BorisException("write_file requiere 'spec': el modelo principal debe describir el archivo y el contenido lo escribe el drafter");
        }

        String resolvedPath = ProjectContext.resolveRelativePath(path.trim());
        String existingSection = readExistingSection(Paths.get(resolvedPath));
        String userMessage = "PATH: " + path.trim() + "\n\nSPEC:\n" + spec.trim() + existingSection;

        String content = collectDraft(userMessage);
        notifyTokenUsage(userMessage, content);

        String result = WriteTool.execute(Map.of("path", path, "content", content));
        return augmentResult(result, modelName, resolvedPath, content);
    }

    private String collectDraft(String userMessage) {
        ChatResponse response;
        try {
            response = client.chat(ChatRequest.builder()
                    .model(modelName)
                    .messages(java.util.List.of(
                            ChatMessage.system(loadDrafterPrompt()),
                            ChatMessage.user(userMessage)))
                    .temperature(temperature)
                    .maxTokens(maxTokens)
                    .stream(false)
                    .build());
        } catch (IOException e) {
            throw new BorisException("Error llamando al drafter " + modelName + ": " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BorisException("Llamada al drafter " + modelName + " interrumpida", e);
        }

        ChatMessage message = response != null ? response.getFirstMessage() : null;
        String raw = message != null && message.content() != null ? message.content() : "";
        String content = stripCodeFences(raw);
        if (content.isBlank()) {
            throw new BorisException("El drafter " + modelName + " devolvió contenido vacío");
        }
        return content;
    }

    private static String loadDrafterPrompt() {
        try (var in = DrafterService.class.getResourceAsStream(DRAFTER_PROMPT_RESOURCE)) {
            if (in == null) {
                throw new BorisException("Prompt del drafter no encontrado en classpath: " + DRAFTER_PROMPT_RESOURCE);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
        } catch (IOException e) {
            throw new BorisException("No se pudo leer el prompt del drafter: " + DRAFTER_PROMPT_RESOURCE, e);
        }
    }

    private static String readExistingSection(Path resolvedPath) {
        if (!Files.exists(resolvedPath) || !Files.isRegularFile(resolvedPath)) {
            return "";
        }
        try {
            String existing = Files.readString(resolvedPath, StandardCharsets.UTF_8);
            if (existing.isBlank()) {
                return "";
            }
            return "\n\nEXISTING (reescribir completo incorporando lo pedido, conservar lo que no cambia):\n" + existing;
        } catch (IOException e) {
            throw new BorisException("No se pudo leer el archivo existente para el drafter: " + resolvedPath, e);
        }
    }

    private static String stripCodeFences(String raw) {
        String trimmed = raw.strip();
        if (!trimmed.startsWith("```")) {
            return trimmed;
        }
        int firstNewline = trimmed.indexOf('\n');
        if (firstNewline < 0) {
            return trimmed;
        }
        String body = trimmed.substring(firstNewline + 1);
        body = body.stripTrailing();
        if (body.endsWith("```")) {
            body = body.substring(0, body.length() - 3);
        }
        return body.strip();
    }

    private void notifyTokenUsage(String input, String output) {
        int inputTokens = TOKEN_ENCODING.countTokens(input);
        int outputTokens = TOKEN_ENCODING.countTokens(output);
        if (inputTokens > 0 || outputTokens > 0) {
            ToolResultNotifier.notifyTokenUsage("write_file:drafter", inputTokens, outputTokens);
        }
    }

    private static String augmentResult(String result, String drafterName, String resolvedPath, String content) {
        try {
            var node = MAPPER.readTree(result);
            if (node instanceof ObjectNode objectNode
                    && objectNode.has("success")
                    && objectNode.get("success").asBoolean()) {
                int lines = content.isBlank() ? 0 : content.split("\\r?\\n").length;
                objectNode.put("message", "Draft written by drafter " + drafterName + ": " + resolvedPath
                        + " (" + lines + " lines). Review the file and refine it with apply_edit if needed.");
                return MAPPER.writeValueAsString(objectNode);
            }
            return result;
        } catch (IOException e) {
            throw new BorisException("No se pudo interpretar el resultado de write_file: " + result, e);
        }
    }
}
