package com.boris.librixsoft.ai;

public interface ChatModel {
    ChatResponse call(Prompt prompt);
}
