package com.boris.librixsoft.level4.wrapper.llama;

import com.boris.librixsoft.level5.nativeCpp.jna.LlamaInstance;
import lombok.Getter;
import lombok.Setter;
import org.springframework.stereotype.Component;

@Component
@Getter
@Setter
public class LlamaServerState {

    /** Instancia nativa actualmente activa. */
    private LlamaInstance defaultInstance;

    /** ID del modelo actualmente cargado en {@code defaultInstance}. */
    private String activeModelId;

    private boolean ready = false;
    private boolean enableLlamaCppLogs = false;
}
