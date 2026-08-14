package com.boris.chat;

@FunctionalInterface
public interface LlmProvider {
    String send(String message);
}
