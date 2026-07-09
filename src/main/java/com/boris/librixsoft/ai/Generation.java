package com.boris.librixsoft.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Generation {
    private Message output;

    public Generation(String text) {
        this.output = new AssistantMessage(text);
    }
}
