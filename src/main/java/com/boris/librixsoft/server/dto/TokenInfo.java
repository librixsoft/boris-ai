package com.boris.librixsoft.server.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TokenInfo {
    private int inputTokens;
    private int outputTokens;
    private int contextSize;

    public int getTotalTokens() {
        return inputTokens + outputTokens;
    }

    public int getRemainingTokens() {
        return contextSize - getTotalTokens();
    }
}
