package com.boris.config;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.boris.chat.ChatService;
import com.boris.chat.DecomposableChatService;
import com.boris.llm.client.BorisLlmClient;
import com.boris.llm.ollama.OllamaModelService;
import com.boris.llm.think.OllamaThinkSpringAiFactory;
import com.boris.settings.Settings;
import com.boris.skill.SkillExecutor;
import com.boris.skill.SkillLoader;
import com.boris.skill.SkillManager;
import com.boris.task.TaskAborter;
import com.boris.task.decomposition.TaskDecompositionService;
import com.boris.task.decomposition.TaskDecompositionService.DecompositionConfig;
import com.boris.tooling.engine.ToolRegistry;
import com.boris.tooling.integration.ToolCallingConfig;

@Configuration
public class ServiceConfiguration {

    @Bean
    public SkillLoader skillLoader(Path skillsDirectory) {
        return new SkillLoader(skillsDirectory);
    }

    @Bean
    public SkillExecutor skillExecutor(Path workspaceDirectory, TaskAborter taskAborter) {
        return new SkillExecutor(workspaceDirectory, taskAborter);
    }

    @Bean
    public SkillManager skillManager(SkillLoader skillLoader, SkillExecutor skillExecutor, TaskAborter taskAborter) {
        return new SkillManager(skillLoader, skillExecutor, taskAborter);
    }

    @Bean
    public TaskAborter taskAborter() {
        return new TaskAborter();
    }

    @Bean
    @Lazy
    public BorisLlmClient borisLlmClient(Settings settings) {
        try {
            return OllamaThinkSpringAiFactory.createClient(settings);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create BorisLlmClient: " + e.getMessage(), e);
        }
    }

    @Bean
    @Lazy
    public Supplier<BorisLlmClient> llmClientSupplier(BorisLlmClient borisLlmClient) {
        return () -> borisLlmClient;
    }

    @Bean
    @Lazy
    public ChatService chatService(Supplier<BorisLlmClient> llmClientSupplier,
                                   Settings settings,
                                   TaskAborter taskAborter) {
        String modelName = settings.getModel() != null ? settings.getModel().getName() : null;
        String thinkMode = OllamaThinkSpringAiFactory.resolveThinkMode(settings);
        boolean thinkingEnabled = thinkMode != null;
        String thinkingMode = thinkMode != null ? thinkMode : "low";
        int historySize = settings.getMaxHistorySize() != null ? settings.getMaxHistorySize() : 10;
        boolean enableHistory = settings.getEnableHistory() != null ? settings.getEnableHistory() : true;

        if (settings.getModel() != null && settings.getModel().getBaseUrl() != null) {
            taskAborter.setOllamaBaseUrl(settings.getModel().getBaseUrl());
        }

        ChatService service = new ChatService(
                llmClientSupplier,
                "boris",
                taskAborter,
                historySize,
                enableHistory,
                thinkingEnabled,
                thinkingMode,
                modelName
        );
        service.setSettings(settings);
        try {
            ToolRegistry registry = ToolCallingConfig.buildToolRegistry(settings);
            String prompt = ToolCallingConfig.loadSystemPrompt(settings);
            service.setToolRegistry(registry);
            service.setSystemPrompt(prompt);
        } catch (Exception ignored) {}

        return service;
    }

    @Bean
    @Lazy
    public DecomposableChatService decomposableChatService(ChatService chatService) {
        Consumer<String> defaultCallback = System.out::println;
        return new DecomposableChatService(chatService, defaultCallback, DecompositionConfig.defaults());
    }

    @Bean
    @Lazy
    public TaskDecompositionService taskDecompositionService(ChatService chatService) {
        return new TaskDecompositionService(
                chatService::sendMessage,
                System.out::println,
                DecompositionConfig.defaults()
        );
    }

    @Bean
    public OllamaModelService ollamaModelService() {
        return new OllamaModelService();
    }
}
