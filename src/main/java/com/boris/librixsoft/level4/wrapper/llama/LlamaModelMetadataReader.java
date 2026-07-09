package com.boris.librixsoft.level4.wrapper.llama;

import com.boris.librixsoft.level5.nativeCpp.jna.LlamaLibrary;
import com.sun.jna.Pointer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
public class LlamaModelMetadataReader {

    public String readMetadata(Pointer model, String key) {
        if (model == null) {
            return null;
        }
        try {
            int maxLen = 4096;
            byte[] buf = new byte[maxLen];
            int len = LlamaLibrary.get().llama_model_meta_val_str(model, key, buf, (long) maxLen);

            if (len > maxLen) {
                buf = new byte[len];
                len = LlamaLibrary.get().llama_model_meta_val_str(model, key, buf, (long) len);
            }

            if (len > 0) {
                int actualLen = 0;
                while (actualLen < len && buf[actualLen] != 0) {
                    actualLen++;
                }
                return new String(buf, 0, actualLen, StandardCharsets.UTF_8).trim();
            }
        } catch (Exception e) {
            log.debug("GGUF metadata key '{}' error: {}", key, e.getMessage());
        }
        return null;
    }

    public String readArchitecture(Pointer model) {
        return readMetadata(model, "general.architecture");
    }
}
