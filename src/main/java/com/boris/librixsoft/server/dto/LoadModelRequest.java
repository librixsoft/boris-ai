package com.boris.librixsoft.server.dto;

import lombok.Data;

@Data
public class LoadModelRequest {
    private String id;
    private String model;
    private Integer contextSize;
    private Integer threads;
    private Integer gpuLayers;
    private Integer batchSize;
    private Double temperature;
    private Integer maxTokens;
    private Integer parallel;
    private Boolean loadModel = true;  // Default true, set false for candidate-only registration
}
