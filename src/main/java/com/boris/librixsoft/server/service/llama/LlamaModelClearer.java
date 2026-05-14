package com.boris.librixsoft.server.service.llama;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlamaModelClearer {

    public void clearModel(String modelId) throws IOException {
        log.info("[*] Native model resources clear requested for: {}. Managed natively.", (modelId != null ? modelId : "default"));
    }

    public void clearModel() throws IOException {
        clearModel(null);
    }
}
