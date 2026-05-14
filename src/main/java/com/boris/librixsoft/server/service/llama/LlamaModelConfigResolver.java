package com.boris.librixsoft.server.service.llama;

import com.boris.librixsoft.config.BorisProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LlamaModelConfigResolver {

    private final BorisProperties properties;

    public BorisProperties.ModelConfig getModelConfig(String id) {
        if (properties.getPreloadModels() == null)
            return null;
        return properties.getPreloadModels().stream()
                .filter(m -> id.equals(m.getId()))
                .findFirst()
                .orElse(null);
    }
}
