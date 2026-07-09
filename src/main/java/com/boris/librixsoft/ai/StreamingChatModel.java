package com.boris.librixsoft.ai;

import reactor.core.publisher.Flux;

public interface StreamingChatModel {
    Flux<ChatResponse> stream(Prompt prompt);
}
