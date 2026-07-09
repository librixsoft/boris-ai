package com.boris.librixsoft.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SystemMessage implements Message {
    private String text;

    @Override
    public MessageType getMessageType() {
        return MessageType.SYSTEM;
    }
}
