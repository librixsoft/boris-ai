package com.boris.librixsoft.level4.wrapper.llama;

import com.boris.librixsoft.level5.nativeCpp.jna.LlamaInstance;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlamaModelClearer {

    private final LlamaServerState serverState;
    private final LlamaChatService llamaChatService;

    public void clearModel(String modelId) throws IOException {
        log.info("[*] Native model resources clear requested for: {}.", (modelId != null ? modelId : "default"));
        
        llamaChatService.resetKvCache();
        LlamaInstance defaultInstance = serverState.getDefaultInstance();
        if (defaultInstance != null) {
            defaultInstance.clearKvCache();
        }
    }

    public void clearModel() throws IOException {
        clearModel(null);
    }
}
