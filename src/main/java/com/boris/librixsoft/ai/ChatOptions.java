package com.boris.librixsoft.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatOptions {
    private Double temperature;
    private Integer maxTokens;

    public static ChatOptionsBuilder builder() {
        return new ChatOptionsBuilder();
    }
}
