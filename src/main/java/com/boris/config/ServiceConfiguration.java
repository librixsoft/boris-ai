package com.boris.config;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Supplier;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;

import com.boris.chat.ChatService;
import com.boris.chat.DecomposableChatService;
import com.boris.llm.think.OllamaThinkSpringAiFactory;
import com.boris.settings.Settings;
import com.boris.skill.SkillExecutor;
import com.boris.skill.SkillLoader;
import com.boris.skill.SkillManager;
import com.boris.task.TaskAborter;
import com.boris.task.decomposition.TaskDecompositionService;
import com.boris.task.decomposition.TaskDecompositionService.DecompositionConfig;

@Configuration
public class ServiceConfiguration {

    @Bean
    public SkillLoader skillLoader(Path skillsDirectory) {
        return new SkillLoader(skillsDirectory);
    }

    @Bean
    public SkillExecutor skillExecutor(Path workspaceDirectory) {
        return new SkillExecutor(workspaceDirectory);
    }

    @Bean
    public SkillManager skillManager(SkillLoader skillLoader, SkillExecutor skillExecutor) {
        return new SkillManager(skillLoader, skillExecutor);
    }

    @Bean
    public TaskAborter taskAborter() {
        return new TaskAborter();
    }

    @Bean
    @Lazy
    public ChatClient chatClient(Settings settings) {
        try {
            String thinkMode = OllamaThinkSpringAiFactory.resolveThinkMode(settings);
            boolean thinkingEnabled = thinkMode != null;
            return OllamaThinkSpringAiFactory.createChatClient(settings, thinkingEnabled ? thinkMode : null);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to create ChatClient: " + e.getMessage(), e);
        }
    }

    @Bean
    @Lazy
    public Supplier<ChatClient> chatClientSupplier(ChatClient chatClient) {
        return () -> chatClient;
    }

    @Bean
    @Lazy
    public ChatService chatService(Supplier<ChatClient> chatClientSupplier,
                                   Settings settings,
                                   TaskAborter taskAborter) {
        String modelName = settings.getModel() != null ? settings.getModel().getName() : null;
        String thinkMode = OllamaThinkSpringAiFactory.resolveThinkMode(settings);
        boolean thinkingEnabled = thinkMode != null;
        String thinkingMode = thinkMode != null ? thinkMode : "low";
        int historySize = settings.getMaxHistorySize() != null ? settings.getMaxHistorySize() : 10;
        boolean enableHistory = settings.getEnableHistory() != null ? settings.getEnableHistory() : true;

        return new ChatService(
                chatClientSupplier,
                "boris",
                taskAborter,
                historySize,
                enableHistory,
                thinkingEnabled,
                thinkingMode,
                modelName
        );
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
}
