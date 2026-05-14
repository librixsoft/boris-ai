package com.boris.librixsoft.server.service.llama;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Slf4j
@Service
@RequiredArgsConstructor
public class LlamaModelUnloader {

    public void unloadModel(String modelName) throws IOException {
        log.info("[*] Native model unload requested for: {}. Managed by Wrapper.", modelName);
        // Note: Actual unloading happens in BorisLLamaServerWrapper by closing the instance
    }
}
