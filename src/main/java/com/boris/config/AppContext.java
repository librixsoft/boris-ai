package com.boris.config;

import com.boris.memory.ConversationRepository;
import com.boris.memory.MemoryService;
import com.boris.settings.Settings;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.ConfigurableApplicationContext;

public final class AppContext {

    private static ConfigurableApplicationContext context;

    private AppContext() {}

    public static ConfigurableApplicationContext getInstance() {
        if (context == null) {
            synchronized (AppContext.class) {
                if (context == null) {
                    context = new AnnotationConfigApplicationContext(JpaConfig.class);
                }
            }
        }
        return context;
    }

    public static MemoryService getMemoryService() {
        return getInstance().getBean(MemoryService.class);
    }

    public static ConversationRepository getConversationRepository() {
        return getInstance().getBean(ConversationRepository.class);
    }

    public static Settings getSettings() {
        return getInstance().getBean(Settings.class);
    }

    public static void close() {
        if (context != null) {
            context.close();
            context = null;
        }
    }
}