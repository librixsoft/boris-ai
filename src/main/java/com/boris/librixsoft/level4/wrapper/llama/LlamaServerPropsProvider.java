package com.boris.librixsoft.level4.wrapper.llama;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class LlamaServerPropsProvider {

    public String getServerProps() throws IOException {
        return "{\"engine\": \"native-jna\", \"status\": \"running\"}";
    }
}
