package com.boris.librixsoft.server.service;

import com.boris.librixsoft.agent.tools.*;
import com.boris.librixsoft.config.BorisProperties;
import com.boris.librixsoft.server.dto.*;
import com.boris.librixsoft.server.service.prompts.EditorSystemPrompt;
import com.boris.librixsoft.server.service.prompts.EditorEditPrompt;
import com.boris.librixsoft.server.service.prompts.PromptModel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Servicio principal de Boris.
 *
 * IMPORTANTE: Este servicio asume que el modelo YA está cargado desde el dashboard
 * (models → load). No intenta cargar ni descargar el modelo en ningún momento.
 * Toda llamada a LLM usa el modelo activo tal como está.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ModelService {

    private final BorisProperties borisProperties;
    private final LlamaChatService llamaChatService;
    private final PromptModel promptModel;
    private final EditorSystemPrompt editorSystemPrompt;
    private final EditorEditPrompt editorEditPrompt;
    private final ReadFileTool readFileTool;
    private final ChatSessionService chatSessionService;


    public ApiResponse<Map<String, Object>> executeFlow(String instruction, List<Message> history,
                                           AtomicBoolean cancellationRequested, String sessionId) {
        try {
            BorisProperties.ModelConfig cfg = resolveModelConfig();

            // FIX: llamada directa al inference, sin loadModelWithParams.
            String modelResponse = llamaChatService.executePromptWithTools(
                    cfg.getId(), promptModel.build(), instruction, cfg.getTemperature(),
                    null, cancellationRequested, cfg.getMaxTokens(), history);

            llamaChatService.logModelResponse("boris", cfg.getId(),
                    llamaChatService.getColorForRole("planner"), modelResponse);

            TokenInfo tokens = llamaChatService.getTokenInfo();
            String cleanedResponse = modelResponse;

            if (sessionId != null && !sessionId.isBlank()) {
                chatSessionService.addMessage(sessionId, new UserMessage(instruction));
                chatSessionService.addMessage(sessionId, new AssistantMessage(cleanedResponse));
            }

            ChatResponseDTO responseDTO = parseChatResponse(cleanedResponse, ChatResponseDTO.class);

            if (responseDTO != null && responseDTO.getType() != null) {
                String type = responseDTO.getType().toUpperCase();
                return switch (type) {
                    case "CREATE"   -> handleCreateRequest(responseDTO, sid(sessionId));
                    case "EDIT"     -> handleEditRequest(responseDTO, sid(sessionId), cancellationRequested);
                    case "REDESIGN" -> handleRedesignRequest(responseDTO, sid(sessionId));
                    case "DELETE"   -> handleDeleteRequest(responseDTO, sid(sessionId));
                    default         -> buildConversationalResponse(cleanedResponse, tokens, sessionId);
                };
            }

            return buildConversationalResponse(cleanedResponse, tokens, sessionId);

        } catch (Exception e) {
            log.error("❌ Fallo en orquestación: {}", e.getMessage());
            return ApiResponse.error("Orchestration Failed: " + e.getMessage());
        }
    }

    public Flux<ChatMessageResponse> streamFlow(String instruction, List<Message> history,
                                               AtomicBoolean cancellationRequested, String sessionId) {
        BorisProperties.ModelConfig cfg = resolveModelConfig();
        
        StringBuilder accumulatedResponse = new StringBuilder();

        return llamaChatService.streamPrompt(
                cfg.getId(), promptModel.build(), instruction, cfg.getTemperature(),
                cancellationRequested, history, cfg.getMaxTokens()
        ).map(response -> {
            String text = response.getResult().getOutput().getText();
            accumulatedResponse.append(text);
            return new ChatMessageResponse(null, "success", null, text);
        }).doOnComplete(() -> {
            String fullText = accumulatedResponse.toString();
            if (sessionId != null && !sessionId.isBlank()) {
                chatSessionService.addMessage(sessionId, new UserMessage(instruction));
                chatSessionService.addMessage(sessionId, new AssistantMessage(fullText));
            }
        });
    }
    private String sid(String s) { return s != null ? s : ""; }

    // ── CREATE ──────────────────────────────────────────────────────────────────
    private ApiResponse<Map<String, Object>> handleCreateRequest(ChatResponseDTO responseDTO, String sid) {
        String path    = ensureValidPath(responseDTO.getPath());
        String content = responseDTO.getEffectiveContent();

        if (content == null || content.isBlank()) {
            log.warn("⚠️ CREATE sin contenido para '{}'. Revisa el prompt del modelo.", path);
            content = "";
        }

        ToolExecutionPayload payload = new ToolExecutionPayload(
            List.of(new ToolAction("createFile", Map.of("path", path, "content", content))),
            "Archivo " + path + " creado exitosamente."
        );
        return executeToolPayload(payload, path, "create", sid);
    }

    // ── REDESIGN ─────────────────────────────────────────────────────────────────
    private ApiResponse<Map<String, Object>> handleRedesignRequest(ChatResponseDTO responseDTO, String sid) {
        String path    = ensureValidPath(responseDTO.getPath());
        String content = responseDTO.getEffectiveContent();

        if (content == null || content.isBlank()) {
            log.warn("⚠️ REDESIGN sin contenido para '{}'. Revisa el prompt del modelo.", path);
            content = "";
        }

        ToolExecutionPayload payload = new ToolExecutionPayload(
            List.of(new ToolAction("editFile", Map.of("path", path, "newContent", content))),
            "Rediseño aplicado a " + path
        );
        return executeToolPayload(payload, path, "redesign", sid);
    }

    // ── EDIT ─────────────────────────────────────────────────────────────────────
    // FIX: recibe cancellationRequested como parámetro en lugar de crear uno nuevo,
    // y llama directamente al inference sin loadModelWithParams.
    private ApiResponse<Map<String, Object>> handleEditRequest(ChatResponseDTO responseDTO, String sid,
                                                   AtomicBoolean cancellationRequested) {
        BorisProperties.ModelConfig cfg = resolveModelConfig();
        String path       = ensureValidPath(responseDTO.getPath());
        String rawContent = readFileTool.call(path);
        final String content = rawContent.startsWith("error:") ? "[Contenido no disponible]" : rawContent;

        // FIX: llamada directa, sin loadModelWithParams.
        llamaChatService.executePromptWithTools(
            cfg.getId(),
            editorSystemPrompt.build(),
            editorEditPrompt.build(path, responseDTO.getEffectiveInstructions(), content),
            cfg.getTemperature(),
            this,
            cancellationRequested,
            cfg.getMaxTokens(),
            new ArrayList<>()
        );

        if (!sid.isEmpty()) chatSessionService.addMessage(sid, new AssistantMessage("[Editado: " + path + "]"));
        String msg = "Edición completada en " + path;
        return ApiResponse.ok(msg, Map.of("type", "edit", "path", path, "result", msg));
    }

    // ── DELETE ───────────────────────────────────────────────────────────────────
    private ApiResponse<Map<String, Object>> handleDeleteRequest(ChatResponseDTO responseDTO, String sid) {
        String path = ensureValidPath(responseDTO.getPath());
        ToolExecutionPayload payload = new ToolExecutionPayload(
            List.of(new ToolAction("deleteFile", Map.of("path", path))),
            "Archivo " + path + " eliminado."
        );
        return executeToolPayload(payload, path, "delete", sid);
    }

    // ── Tool execution ───────────────────────────────────────────────────────────
    // FIX: eliminado loadModelWithParams. La ejecución de tools nativa no
    // necesita recargar el modelo; usa el que ya está activo.
    private ApiResponse<Map<String, Object>> executeToolPayload(ToolExecutionPayload payload, String path, String type, String sid) {
        try {
            String json     = new ObjectMapper().writeValueAsString(payload);
            String response = llamaChatService.executeNativeToolsDirectly(json);
            if (!sid.isEmpty()) chatSessionService.addMessage(sid, new AssistantMessage("[" + type + " en " + path + "]"));
            return ApiResponse.ok(response, Map.of("type", type, "path", path, "result", response));
        } catch (Exception e) {
            log.error("❌ Tool execution failed para path='{}': {}", path, e.getMessage());
            return ApiResponse.error("Tool execution failed: " + e.getMessage());
        }
    }

    // ── JSON parser ──────────────────────────────────────────────────────────────
    private <T> T parseChatResponse(String jsonInput, Class<T> clazz) {
        if (jsonInput == null || jsonInput.isBlank()) return null;
        try {
            ObjectMapper mapper = new ObjectMapper()
                .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature())
                .enable(com.fasterxml.jackson.core.json.JsonReadFeature.ALLOW_SINGLE_QUOTES.mappedFeature());

            com.fasterxml.jackson.core.JsonParser parser = mapper.getFactory().createParser(jsonInput);
            while (parser.nextToken() != null && parser.getCurrentToken() != com.fasterxml.jackson.core.JsonToken.START_OBJECT) {}

            return parser.getCurrentToken() == com.fasterxml.jackson.core.JsonToken.START_OBJECT
                ? mapper.readValue(parser, clazz) : null;
        } catch (Exception e) {
            return null;
        }
    }

    // ── Helpers ──────────────────────────────────────────────────────────────────
    private ApiResponse<Map<String, Object>> buildConversationalResponse(String response, TokenInfo tokens, String sessionId) {
        Map<String, Object> data = Map.of(
            "type", "direct",
            "result", response,
            "tokens", tokens != null ? tokens : Map.of()
        );
        return ApiResponse.ok(response, data);
    }

    private String getWorkspacePrefix() {
        String prefix = borisProperties.getWorkspacePrefix();
        return com.boris.librixsoft.util.PathResolver.getUserHome()
               + (prefix == null || prefix.isBlank() ? "/.boris/workspace" : prefix);
    }

    private String ensureValidPath(String pathStr) {
        if (pathStr == null || pathStr.isBlank()) return getWorkspacePrefix();
        java.nio.file.Path p = java.nio.file.Paths.get(pathStr).normalize();
        return p.isAbsolute() ? p.toString()
                              : java.nio.file.Paths.get(getWorkspacePrefix(), p.toString()).toString();
    }

    public void startNewConversation() { llamaChatService.startNewConversation(); }

    public BorisProperties.ModelConfig resolveModelConfig() {
        return llamaChatService.resolveConfig(null, 0);
    }
}