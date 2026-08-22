package com.boris.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "spring.ai.memory")
public class MemoryProperties {

    private boolean enabled = true;
    private String sessionId = "default";
    private int maxContextTokens = 6000;
    private int maxHistoryMessages = 15;
    private int recentFull = 8;
    private int summaryTrigger = 25;
    private boolean useOllamaTokenize = true;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }

    public int getMaxContextTokens() { return maxContextTokens; }
    public void setMaxContextTokens(int maxContextTokens) { this.maxContextTokens = maxContextTokens; }

    public int getMaxHistoryMessages() { return maxHistoryMessages; }
    public void setMaxHistoryMessages(int maxHistoryMessages) { this.maxHistoryMessages = maxHistoryMessages; }

    public int getRecentFull() { return recentFull; }
    public void setRecentFull(int recentFull) { this.recentFull = recentFull; }

    public int getSummaryTrigger() { return summaryTrigger; }
    public void setSummaryTrigger(int summaryTrigger) { this.summaryTrigger = summaryTrigger; }

    public boolean isUseOllamaTokenize() { return useOllamaTokenize; }
    public void setUseOllamaTokenize(boolean useOllamaTokenize) { this.useOllamaTokenize = useOllamaTokenize; }
}