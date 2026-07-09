package com.boris.librixsoft.ai;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.imageio.ImageWriter;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatResponse {
    @Getter
    private List<Generation> results;

    public ChatResponse(Generation generation) {
        this.results = List.of(generation);
    }

}
