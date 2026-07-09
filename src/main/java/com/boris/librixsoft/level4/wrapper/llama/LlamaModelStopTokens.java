package com.boris.librixsoft.level4.wrapper.llama;

import com.boris.librixsoft.exception.LlamaModelException;
import com.boris.librixsoft.level5.nativeCpp.jna.LlamaLibrary;
import com.sun.jna.Pointer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
@RequiredArgsConstructor
public class LlamaModelStopTokens {

    private static final List<String> STOP_TOKEN_METADATA_KEYS = List.of(
            "tokenizer.ggml.stop_token_ids",
            "tokenizer.ggml.eos_token_id",
            "tokenizer.ggml.eot_token_id"
    );

    private final LlamaModelMetadataReader metadataReader;
    private volatile Set<Integer> activeStopTokens = new HashSet<>();

    public Set<Integer> build(Pointer model, Pointer vocab) {
        Set<Integer> set = new HashSet<>();
        if (vocab == null) {
            throw new LlamaModelException("Vocab pointer is null, cannot build stop tokens");
        }

        try {
            int eos = LlamaLibrary.get().llama_vocab_eos(vocab);
            set.add(eos);
            log.info("🛑 [StopTokens] EOS token ID: {}", eos);
        } catch (Exception e) {
            throw new LlamaModelException("Failed to get EOS token from vocab", e);
        }

        for (String key : STOP_TOKEN_METADATA_KEYS) {
            String metadataStopTokens = metadataReader.readMetadata(model, key);
            if (metadataStopTokens != null && !metadataStopTokens.isBlank()) {
                for (Integer tokenId : parseTokenIds(key, metadataStopTokens)) {
                    set.add(tokenId);
                    log.debug("🛑 [StopTokens] metadata {} token ID {}", key, tokenId);
                }
            }
        }
        log.info("🛑 [StopTokens] Final stop token IDs: {}", set);
        return set;
    }

    public void initialize(Pointer model, Pointer vocab) {
        activeStopTokens = Set.copyOf(build(model, vocab));
    }

    public boolean isStopToken(int token, Pointer vocab) {
        if (!activeStopTokens.isEmpty() && activeStopTokens.contains(token)) {
            return true;
        }
        if (vocab == null) {
            log.warn("🛑 [StopTokens] Vocab is null, cannot check stop token, returning false");
            return false;
        }
        try {
            return LlamaLibrary.get().llama_vocab_is_eog(vocab, token);
        } catch (Exception e) {
            log.warn("🛑 [StopTokens] Failed to check EOG token, returning false: {}", e.getMessage());
            return false;
        }
    }

    private static Set<Integer> parseTokenIds(String metadataKey, String metadataStopTokens) {
        Set<Integer> tokenIds = new HashSet<>();
        String normalized = metadataStopTokens.trim();
        if (normalized.startsWith("[") && normalized.endsWith("]")) {
            normalized = normalized.substring(1, normalized.length() - 1);
        }

        for (String part : normalized.split(",")) {
            String candidate = part.trim();
            if (candidate.isEmpty()) {
                continue;
            }
            try {
                tokenIds.add(Integer.parseInt(candidate));
            } catch (NumberFormatException e) {
                throw new LlamaModelException("Invalid stop token id '" + candidate
                        + "' in GGUF metadata key '" + metadataKey + "'", e);
            }
        }
        return tokenIds;
    }
}
