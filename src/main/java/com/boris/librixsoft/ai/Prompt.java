package com.boris.librixsoft.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Prompt {
    private List<Message> instructions;
    private ChatOptions options;

    public Prompt(List<Message> instructions) {
        this.instructions = instructions;
        this.options = null;
    }
}
