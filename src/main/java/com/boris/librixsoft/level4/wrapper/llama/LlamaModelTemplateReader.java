package com.boris.librixsoft.level4.wrapper.llama;

import com.boris.librixsoft.ai.Message;
import com.boris.librixsoft.ai.SystemMessage;
import com.boris.librixsoft.ai.UserMessage;
import com.boris.librixsoft.exception.LlamaModelException;
import com.boris.librixsoft.exception.MissingChatTemplateException;
import com.boris.librixsoft.level5.nativeCpp.jna.LlamaLibrary;
import com.hubspot.jinjava.Jinjava;
import com.hubspot.jinjava.interpret.RenderResult;
import com.sun.jna.Pointer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlamaModelTemplateReader {

    private final LlamaModelMetadataReader metadataReader;

    public String buildPrompt(Pointer model, List<Message> messages, boolean systemPromptPrefilled) {
        return assemblePrompt(messages, requireChatTemplate(model), systemPromptPrefilled, true,
                readTemplateTokens(model));
    }

    public String buildPrompt(List<Message> messages, String chatTemplate, boolean systemPromptPrefilled) {
        return assemblePrompt(messages, chatTemplate, systemPromptPrefilled, true, ChatTemplateTokens.empty());
    }

    public String buildPromptFromStrings(Pointer model, String systemPrompt, String userPrompt) {
        return buildPromptFromStrings(systemPrompt, userPrompt, requireChatTemplate(model),
                readTemplateTokens(model));
    }

    public String buildPromptFromStrings(String systemPrompt, String userPrompt, String chatTemplate) {
        return buildPromptFromStrings(systemPrompt, userPrompt, chatTemplate, ChatTemplateTokens.empty());
    }

    private String buildPromptFromStrings(String systemPrompt,
                                          String userPrompt,
                                          String chatTemplate,
                                          ChatTemplateTokens templateTokens) {
        List<Message> msgs = new ArrayList<>();
        if (systemPrompt != null && !systemPrompt.isBlank()) {
            msgs.add(new SystemMessage(systemPrompt));
        }
        msgs.add(new UserMessage(userPrompt));
        return assemblePrompt(msgs, chatTemplate, false, true, templateTokens);
    }

    public boolean isReasoningModel(Pointer model) {
        return isReasoningTemplate(readChatTemplate(model));
    }

    public String requireChatTemplate(Pointer model) {
        String chatTemplate = readChatTemplate(model);
        if (chatTemplate == null || chatTemplate.isEmpty()) {
            throw new MissingChatTemplateException(
                    "Chat template is missing in GGUF metadata for the model.");
        }
        return chatTemplate;
    }

    public String readChatTemplate(Pointer model) {
        return metadataReader.readMetadata(model, "tokenizer.chat_template");
    }

    public boolean isReasoningTemplate(String template) {
        return template != null
                && (template.contains("<think>")
                || template.contains("<|thinking|>")
                || template.contains("thinking"));
    }

    private String assemblePrompt(List<Message> messages,
                                  String chatTemplate,
                                  boolean systemPromptPrefilled,
                                  boolean addAssistantTurn,
                                  ChatTemplateTokens templateTokens) {
        log.debug("[PROMPT] Chat template: {}", chatTemplate);
        log.debug("[PROMPT] System prompt prefilled: {}", systemPromptPrefilled);
        log.debug("[PROMPT] Add assistant turn: {}", addAssistantTurn);
        log.debug("[PROMPT] Number of messages: {}", messages.size());
        
        List<Message> effectiveMessages = new ArrayList<>();
        for (Message message : messages) {
            if ("system".equals(message.getMessageType().getValue()) && systemPromptPrefilled) {
                log.debug("[PROMPT] System prompt skipped (in KV cache)");
                continue;
            }
            effectiveMessages.add(message);
        }

        // Use Jinjava directly for all templates to support complex Jinja2 templates
        return assemblePromptWithJinjava(effectiveMessages, chatTemplate, addAssistantTurn, templateTokens);
    }

    private String assemblePromptWithJinjava(List<Message> messages,
                                             String chatTemplate,
                                             boolean addAssistantTurn,
                                             ChatTemplateTokens templateTokens) {
        log.debug("[PROMPT] Rendering GGUF tokenizer.chat_template with Jinjava");
        
        Jinjava jinjava = new Jinjava();
        Map<String, Object> context = new HashMap<>();
        
        List<Map<String, Object>> messagesList = new ArrayList<>();
        for (Message message : messages) {
            Map<String, Object> msgMap = new HashMap<>();
            msgMap.put("role", message.getMessageType().getValue());
            msgMap.put("content", message.getText());
            messagesList.add(msgMap);
        }
        
        context.put("messages", messagesList);
        context.put("add_generation_prompt", addAssistantTurn);
        context.put("bos_token", templateTokens.bosToken());
        context.put("eos_token", templateTokens.eosToken());
        
        try {
            RenderResult result = jinjava.renderForResult(chatTemplate, context);
            String rendered = result.getOutput();
            log.info("[PROMPT] Jinjava template result length: {}, content preview: {}", rendered.length(), 
                rendered.length() > 200 ? rendered.substring(0, 200) + "..." : rendered);
            return rendered;
        } catch (Exception e) {
            log.error("[PROMPT] Jinjava template processing failed: {}", e.getMessage(), e);
            throw new LlamaModelException("Failed to render GGUF chat template", e);
        }
    }

    private ChatTemplateTokens readTemplateTokens(Pointer model) {
        if (model == null) {
            return ChatTemplateTokens.empty();
        }

        Pointer vocab = LlamaLibrary.get().llama_model_get_vocab(model);
        if (vocab == null) {
            throw new LlamaModelException("Vocab pointer is null, cannot read chat template tokens");
        }

        return new ChatTemplateTokens(
                readTokenPiece(vocab, LlamaLibrary.get().llama_vocab_bos(vocab)),
                readTokenPiece(vocab, LlamaLibrary.get().llama_vocab_eos(vocab))
        );
    }

    private String readTokenPiece(Pointer vocab, int token) {
        if (token < 0) {
            return "";
        }

        byte[] buf = new byte[256];
        int len = LlamaLibrary.get().llama_token_to_piece(vocab, token, buf, buf.length, 0, (byte) 1);
        if (len < 0) {
            buf = new byte[-len];
            len = LlamaLibrary.get().llama_token_to_piece(vocab, token, buf, buf.length, 0, (byte) 1);
        }
        if (len <= 0) {
            return "";
        }
        return new String(buf, 0, len, java.nio.charset.StandardCharsets.UTF_8);
    }

    private record ChatTemplateTokens(String bosToken, String eosToken) {
        static ChatTemplateTokens empty() {
            return new ChatTemplateTokens("", "");
        }
    }
}
